package com.baidu.tv.player.kt.ui.filebrowser

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.baidu.tv.player.kt.R
import com.baidu.tv.player.kt.databinding.FragmentFileBrowserBinding
import com.baidu.tv.player.kt.model.MediaType
import com.baidu.tv.player.kt.ui.playback.PlaybackActivity
import com.baidu.tv.player.kt.util.PlaylistCache
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class FileBrowserFragment : Fragment() {

    @Inject lateinit var playlistCache: PlaylistCache

    private var _binding: FragmentFileBrowserBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val viewModel: FileBrowserViewModel by viewModels()
    private val adapter = FileAdapter()

    /** 上次已请求过列表焦点的路径+文件数指纹，避免每次状态更新都抢走按钮焦点。 */
    private var lastFocusedListKey: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFileBrowserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupActions()
        collectViewModel()
        viewModel.loadInitialIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        binding.root.isFocusableInTouchMode = true
        binding.root.requestFocus()
        binding.root.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK) onBackPressed() else false
        }
        requestFocusOnList()
    }

    override fun onDestroyView() {
        binding.gridFiles.adapter = null
        binding.gridFiles.layoutManager = null
        _binding = null
        super.onDestroyView()
    }

    fun onBackPressed(): Boolean {
        val state = viewModel.uiState.value
        if (state.multiSelectMode && state.selectedPaths.isNotEmpty()) {
            viewModel.clearSelection()
            return true
        }
        return viewModel.goBack()
    }

    private fun setupRecyclerView() {
        binding.gridFiles.layoutManager = LinearLayoutManager(requireContext())
        binding.gridFiles.adapter = adapter
        binding.gridFiles.setHasFixedSize(true)
        binding.gridFiles.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        binding.gridFiles.isFocusable = true

        adapter.onItemClick = { file, _ -> viewModel.onFileClicked(file) }
        adapter.onItemLongClick = { file, _ -> viewModel.onFileLongClicked(file) }
        // 长列表操作流优化：在任意列表项按「左键」直接跳回顶部操作按钮
        adapter.onItemNavigateLeft = {
            binding.btnPlaySelected.requestFocus()
        }
    }

    private fun setupActions() {
        binding.btnRecursive.setOnClickListener { viewModel.toggleRecursive() }
        binding.btnSort.setOnClickListener { viewModel.toggleSortMode() }
        binding.btnViewMode.setOnClickListener { viewModel.toggleViewMode() }
        binding.btnPlaySelected.setOnClickListener {
            if (viewModel.uiState.value.multiSelectMode) viewModel.confirmSelection() else viewModel.playCurrentList()
        }
    }

    private fun collectViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::renderState) }
                launch { viewModel.events.collect(::handleEvent) }
            }
        }
    }

    private fun renderState(state: FileBrowserUiState) {
        binding.tvCurrentPath.text = state.currentPath
        binding.progressLoading.visibility = if (state.isLoading || state.isCreatingPlaylist) View.VISIBLE else View.GONE
        binding.tvEmptyMessage.visibility = if (state.errorMessage != null || (!state.isLoading && state.files.isEmpty())) View.VISIBLE else View.GONE
        binding.tvEmptyMessage.text = state.errorMessage ?: "暂无文件"
        binding.btnRecursive.text = if (state.isRecursive) "递归加载: 开" else "递归加载: 关"
        binding.btnSort.text = state.sortMode.label
        binding.btnViewMode.text = getString(
            if (state.isGridMode) R.string.file_browser_list_view else R.string.file_browser_grid_view,
        )
        binding.btnViewMode.setCompoundDrawablesWithIntrinsicBounds(
            if (state.isGridMode) R.drawable.ic_view_list else R.drawable.ic_view_grid,
            0,
            0,
            0,
        )
        if (state.isGridMode && binding.gridFiles.layoutManager !is GridLayoutManager) {
            binding.gridFiles.layoutManager = GridLayoutManager(requireContext(), GRID_COLUMNS)
        } else if (!state.isGridMode && binding.gridFiles.layoutManager is GridLayoutManager) {
            binding.gridFiles.layoutManager = LinearLayoutManager(requireContext())
        }
        adapter.setGridMode(state.isGridMode)
        binding.btnPlaySelected.text = if (state.multiSelectMode) "确认选择" else "播放当前列表"
        binding.btnPlaySelected.isEnabled = !state.isLoading && !state.isCreatingPlaylist
        adapter.setMultiSelectMode(state.multiSelectMode)
        adapter.setSelectedPaths(state.selectedPaths)
        adapter.submitFiles(state.files)
        // 仅在「目录/文件列表真正变化」时把焦点移入列表，避免点按顶部按钮后焦点被抢回
        val listKey = "${state.currentPath}#${state.files.size}#${state.isRecursive}#${state.sortMode}"
        if (state.files.isNotEmpty() && listKey != lastFocusedListKey) {
            lastFocusedListKey = listKey
            requestFocusOnList()
        }
    }

    private fun handleEvent(event: FileBrowserUiEvent) {
        when (event) {
            is FileBrowserUiEvent.OpenPlayback -> startPlayback(event)
            FileBrowserUiEvent.PlaylistCreationStarted -> Toast.makeText(requireContext(), "正在扫描文件，请稍候...", Toast.LENGTH_SHORT).show()
            is FileBrowserUiEvent.PlaylistCreationSucceeded -> {
                Toast.makeText(requireContext(), "播放列表创建成功！\n共添加 ${event.itemCount} 个文件", Toast.LENGTH_SHORT).show()
                requireActivity().setResult(Activity.RESULT_OK, Intent().putExtra("playlistId", event.playlistId))
                requireActivity().finish()
            }
            is FileBrowserUiEvent.PlaylistCreationFailed -> Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
            is FileBrowserUiEvent.SelectionChanged -> {
                Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
                // 长列表中完成一次选择后，直接把焦点交给「确认选择」。
                // 用户可立即确认；如需继续选择，按下键即可回到当前可见列表，避免一路按上返回顶部。
                if (viewModel.uiState.value.multiSelectMode && viewModel.uiState.value.selectedPaths.isNotEmpty()) {
                    binding.btnPlaySelected.post { binding.btnPlaySelected.requestFocus() }
                }
            }
            is FileBrowserUiEvent.Error -> Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun startPlayback(event: FileBrowserUiEvent.OpenPlayback) {
        val playlistId = UUID.randomUUID().toString()
        playlistCache.put(playlistId, event.files)
        startActivity(
            Intent(requireContext(), PlaybackActivity::class.java)
                .putExtra("playlistId", playlistId)
                .putExtra("mediaType", event.mediaType)
                .putExtra("folderPath", event.folderPath)
                .putExtra("startIndex", event.startIndex),
        )
    }

    private fun requestFocusOnList() {
        binding.gridFiles.post {
            val firstChild = binding.gridFiles.getChildAt(0)
            if (firstChild != null) firstChild.requestFocus() else binding.gridFiles.requestFocus()
        }
    }

    companion object {
        private const val GRID_COLUMNS = 4

        fun newInstance(
            mediaType: Int = MediaType.ALL.value,
            initialPath: String = "/",
            multiSelectMode: Boolean = false,
        ): FileBrowserFragment = FileBrowserFragment().apply {
            arguments = Bundle().apply {
                putInt(FileBrowserActivity.EXTRA_MEDIA_TYPE, mediaType)
                putString(FileBrowserActivity.EXTRA_INITIAL_PATH, initialPath)
                putBoolean(FileBrowserActivity.EXTRA_MULTI_SELECT_MODE, multiSelectMode)
            }
        }
    }
}
