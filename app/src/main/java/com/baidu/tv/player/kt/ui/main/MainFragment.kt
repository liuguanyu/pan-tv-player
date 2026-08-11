package com.baidu.tv.player.kt.ui.main

import android.app.Activity
import android.app.AlertDialog
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
import androidx.recyclerview.widget.LinearLayoutManager
import com.baidu.tv.player.kt.R
import com.baidu.tv.player.kt.auth.BaiduAuthService
import com.baidu.tv.player.kt.databinding.FragmentMainBinding
import com.baidu.tv.player.kt.model.MediaType
import com.baidu.tv.player.kt.model.Playlist
import com.baidu.tv.player.kt.ui.filebrowser.FileBrowserActivity
import com.baidu.tv.player.kt.ui.playback.PlaybackActivity
import com.baidu.tv.player.kt.ui.settings.SettingsActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainFragment : Fragment() {

    private var _binding: FragmentMainBinding? = null
    private val binding get() = requireNotNull(_binding)

    @Inject lateinit var authService: BaiduAuthService

    private val viewModel: MainViewModel by viewModels()
    private val playlistAdapter = PlaylistAdapter { authService.getAccessToken() }
    private val recentTaskAdapter = RecentTaskAdapter { authService.getAccessToken() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerViews()
        setupActions()
        collectViewModel()
    }

    override fun onResume() {
        super.onResume()
        view?.isFocusableInTouchMode = true
        view?.requestFocus()
        view?.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (keyCode) {
                KeyEvent.KEYCODE_MENU -> {
                    // 焦点在播放列表卡片上时，菜单键呼出该卡片的操作菜单；其余场景打开设置。
                    val focused = playlistAdapter.focusedPlaylist
                    if (focused != null) {
                        showPlaylistActions(focused)
                    } else {
                        openSettings()
                    }
                    true
                }
                KeyEvent.KEYCODE_BACK -> {
                    if (playlistAdapter.isEditMode) {
                        playlistAdapter.setEditMode(false)
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }
        requestFocusOnVisibleElement()
    }

    override fun onDestroyView() {
        binding.rvPlaylists.adapter = null
        binding.rvRecentTasks.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private fun setupRecyclerViews() {
        binding.rvPlaylists.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvPlaylists.adapter = playlistAdapter
        binding.rvPlaylists.setHasFixedSize(true)
        binding.rvPlaylists.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS

        binding.rvRecentTasks.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvRecentTasks.adapter = recentTaskAdapter
        binding.rvRecentTasks.setHasFixedSize(true)
        binding.rvRecentTasks.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
    }

    private fun setupActions() {
        playlistAdapter.onItemClick = { playlist -> openPlaylist(playlist) }
        // TV 遥控器快捷操作：卡片短按播放，长按直接同步网盘目录，无需进入卡片内部移动焦点。
        playlistAdapter.onItemLongClick = { playlist -> viewModel.refreshPlaylist(playlist) }
        playlistAdapter.onDeleteClick = { playlist -> confirmDeletePlaylist(playlist) }

        recentTaskAdapter.onItemClick = { history ->
            // TODO Phase 5: PlaybackActivity 迁移后补齐播放历史恢复逻辑。
            startActivity(Intent(requireContext(), PlaybackActivity::class.java).putExtra("historyId", history.id))
        }

        // 底部提示条随焦点位置切换。
        playlistAdapter.onItemFocusChange = { focused ->
            playlistCardFocused = focused != null
            updateHint()
        }
        recentTaskAdapter.onItemFocusChange = { hasFocus ->
            recentCardFocused = hasFocus
            updateHint()
        }

        binding.btnBrowseFiles.setOnClickListener {
            // TODO Phase 4: FileBrowserActivity 迁移后补齐浏览业务。
            startActivity(
                Intent(requireContext(), FileBrowserActivity::class.java)
                    .putExtra("mediaType", MediaType.ALL.value)
                    .putExtra("initialPath", "/"),
            )
        }
        binding.btnCreatePlaylist.setOnClickListener { openCreatePlaylist() }
        binding.btnEmptyCreate.setOnClickListener { openCreatePlaylist() }
        binding.btnSettings.setOnClickListener { openSettings() }
    }

    private fun collectViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.playlists.collect { playlists ->
                        playlistAdapter.setPlaylists(playlists)
                        renderPlaylistEmptyState(playlists.isEmpty())
                    }
                }
                launch {
                    viewModel.recentTasks.collect { history ->
                        recentTaskAdapter.setHistoryList(history)
                        renderRecentEmptyState(history.isEmpty())
                    }
                }
                launch { viewModel.events.collect(::handleEvent) }
            }
        }
    }

    private fun renderPlaylistEmptyState(isEmpty: Boolean) {
        binding.emptyPlaylist.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.rvPlaylists.visibility = if (isEmpty) View.GONE else View.VISIBLE
        requestFocusOnVisibleElement()
    }

    private fun renderRecentEmptyState(isEmpty: Boolean) {
        binding.emptyRecent.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.rvRecentTasks.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun handleEvent(event: MainUiEvent) {
        when (event) {
            is MainUiEvent.RefreshStarted -> {
                playlistAdapter.setRefreshing(event.playlist.id, true)
                Toast.makeText(requireContext(), "正在同步“${event.playlist.name}”...", Toast.LENGTH_SHORT).show()
            }
            is MainUiEvent.RefreshSucceeded -> {
                playlistAdapter.setRefreshing(event.playlist.id, false)
                // event.playlist 是刷新发起时的快照，totalItems 为同步前的文件数，可算出增量。
                val delta = event.itemCount - event.playlist.totalItems
                val message = when {
                    delta > 0 -> "同步完成，新增${delta}个文件"
                    delta < 0 -> "同步完成，减少${-delta}个文件"
                    else -> "同步完成，共${event.itemCount}个文件"
                }
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
            is MainUiEvent.RefreshFailed -> {
                playlistAdapter.setRefreshing(event.playlist.id, false)
                Toast.makeText(requireContext(), event.message, Toast.LENGTH_LONG).show()
            }
            is MainUiEvent.PlaylistDeleted -> playlistAdapter.setEditMode(false)
            is MainUiEvent.Error -> Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openCreatePlaylist() {
        // TODO Phase 4: FileBrowserActivity 迁移后补齐播放列表创建结果返回。
        @Suppress("DEPRECATION")
        startActivityForResult(
            Intent(requireContext(), FileBrowserActivity::class.java)
                .putExtra("multiSelectMode", true),
            REQUEST_CREATE_PLAYLIST,
        )
    }

    private fun openPlaylist(playlist: Playlist) {
        // TODO Phase 5: PlaybackActivity 迁移后补齐播放列表播放逻辑。
        startActivity(Intent(requireContext(), PlaybackActivity::class.java).putExtra("playlistDatabaseId", playlist.id))
    }

    private fun showPlaylistActions(playlist: Playlist) {
        val actions = arrayOf(
            getString(R.string.playlist_action_refresh),
            getString(R.string.playlist_action_delete),
        )
        AlertDialog.Builder(requireContext())
            .setTitle(playlist.name)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> viewModel.refreshPlaylist(playlist)
                    1 -> confirmDeletePlaylist(playlist)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun updateHint() {
        val hintRes = when {
            playlistCardFocused -> R.string.hint_playlist_card
            recentCardFocused -> R.string.hint_recent_card
            else -> R.string.hint_default
        }
        binding.tvHint.setText(hintRes)
    }

    private fun confirmDeletePlaylist(playlist: Playlist) {
        AlertDialog.Builder(requireContext())
            .setTitle("删除播放列表")
            .setMessage("确定要删除播放列表\"${playlist.name}\"吗？此操作不会删除网盘中的文件。")
            .setPositiveButton("删除") { _, _ -> viewModel.deletePlaylist(playlist) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun openSettings() {
        // TODO Phase 6: SettingsActivity 迁移实际设置业务。
        startActivity(Intent(requireContext(), SettingsActivity::class.java))
    }

    private fun requestFocusOnVisibleElement() {
        binding.root.post {
            when {
                binding.rvPlaylists.visibility == View.VISIBLE && playlistAdapter.itemCount > 0 -> binding.rvPlaylists.requestFocus()
                // 列表为空时把焦点落在空态的主行动按钮上，引导用户直接创建。
                binding.emptyPlaylist.visibility == View.VISIBLE -> binding.btnEmptyCreate.requestFocus()
                else -> binding.btnBrowseFiles.requestFocus()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CREATE_PLAYLIST && resultCode == Activity.RESULT_OK) {
            Toast.makeText(requireContext(), "播放列表已创建", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val REQUEST_CREATE_PLAYLIST = 1001
    }

    private var playlistCardFocused = false
    private var recentCardFocused = false
}
