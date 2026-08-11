package com.baidu.tv.player.kt.ui.main

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnLayout
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
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

    private var selectedSection = HomeSection.PLAYLISTS
    private var playlistsEmpty = true
    private var recentTasksEmpty = true
    private var playlistCardFocused = false
    private var recentCardFocused = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        selectedSection = savedInstanceState?.getString(STATE_SECTION)
            ?.let { runCatching { HomeSection.valueOf(it) }.getOrNull() }
            ?: HomeSection.PLAYLISTS
        setupRecyclerViews()
        setupActions()
        renderSelectedSection(requestFocus = false)
        collectViewModel()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_SECTION, selectedSection.name)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        view?.isFocusableInTouchMode = true
        view?.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (keyCode) {
                KeyEvent.KEYCODE_MENU -> {
                    val focused = playlistAdapter.focusedPlaylist
                    if (selectedSection == HomeSection.PLAYLISTS && focused != null) {
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
        // 从文件浏览页返回时，RecyclerView 可能还在恢复布局；布局完成后再补一次子项焦点。
        view?.postDelayed({ requestFocusOnVisibleElement() }, FOCUS_RESTORE_DELAY_MS)
    }

    fun onMenuKeyPressed(): Boolean {
        if (!isAdded || _binding == null || selectedSection != HomeSection.PLAYLISTS) return false
        val focused = playlistAdapter.focusedPlaylist ?: run {
            val focusedView = requireActivity().currentFocus ?: return false
            val holder = binding.rvPlaylists.findContainingViewHolder(focusedView)
            holder?.bindingAdapterPosition
                ?.takeIf { it != RecyclerView.NO_POSITION }
                ?.let { playlistAdapter.getItem(it) }
        } ?: return false
        showPlaylistActions(focused)
        return true
    }

    override fun onDestroyView() {
        binding.rvPlaylists.adapter = null
        binding.rvRecentTasks.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private fun setupRecyclerViews() {
        binding.rvPlaylists.layoutManager = GridLayoutManager(requireContext(), GRID_COLUMNS)
        binding.rvPlaylists.adapter = playlistAdapter
        binding.rvPlaylists.setHasFixedSize(true)
        binding.rvPlaylists.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS

        binding.rvRecentTasks.layoutManager = GridLayoutManager(requireContext(), GRID_COLUMNS)
        binding.rvRecentTasks.adapter = recentTaskAdapter
        binding.rvRecentTasks.setHasFixedSize(true)
        binding.rvRecentTasks.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
    }

    private fun setupActions() {
        binding.tabPlaylists.setOnClickListener { selectSection(HomeSection.PLAYLISTS) }
        binding.tabRecent.setOnClickListener { selectSection(HomeSection.RECENT) }

        playlistAdapter.onItemClick = { playlist -> openPlaylist(playlist) }
        playlistAdapter.onItemLongClick = { playlist -> viewModel.refreshPlaylist(playlist) }
        playlistAdapter.onDeleteClick = { playlist -> confirmDeletePlaylist(playlist) }
        playlistAdapter.onItemFocusChange = { focused ->
            playlistCardFocused = focused != null
            updateHint()
        }

        recentTaskAdapter.onItemClick = { history ->
            startActivity(Intent(requireContext(), PlaybackActivity::class.java).putExtra("historyId", history.id))
        }
        recentTaskAdapter.onItemFocusChange = { hasFocus ->
            recentCardFocused = hasFocus
            updateHint()
        }

        binding.btnBrowseFiles.setOnClickListener {
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
                        playlistsEmpty = playlists.isEmpty()
                        binding.tabPlaylists.text = getString(R.string.home_tab_count, getString(R.string.my_playlists), playlists.size)
                        renderSelectedSection(requestFocus = false)
                    }
                }
                launch {
                    viewModel.recentTasks.collect { history ->
                        recentTaskAdapter.setHistoryList(history)
                        recentTasksEmpty = history.isEmpty()
                        binding.tabRecent.text = getString(R.string.home_tab_count, getString(R.string.recent_tasks), history.size)
                        renderSelectedSection(requestFocus = false)
                    }
                }
                launch { viewModel.events.collect(::handleEvent) }
            }
        }
    }

    private fun selectSection(section: HomeSection) {
        if (selectedSection == section) {
            requestFocusOnVisibleElement()
            return
        }
        selectedSection = section
        playlistAdapter.setEditMode(false)
        playlistCardFocused = false
        recentCardFocused = false
        renderSelectedSection(requestFocus = true)
        updateHint()
    }

    private fun renderSelectedSection(requestFocus: Boolean) {
        val showPlaylists = selectedSection == HomeSection.PLAYLISTS
        binding.tabPlaylists.isActivated = showPlaylists
        binding.tabRecent.isActivated = !showPlaylists
        binding.rvPlaylists.visibility = if (showPlaylists && !playlistsEmpty) View.VISIBLE else View.GONE
        binding.emptyPlaylist.visibility = if (showPlaylists && playlistsEmpty) View.VISIBLE else View.GONE
        binding.rvRecentTasks.visibility = if (!showPlaylists && !recentTasksEmpty) View.VISIBLE else View.GONE
        binding.emptyRecent.visibility = if (!showPlaylists && recentTasksEmpty) View.VISIBLE else View.GONE
        if (requestFocus) requestFocusOnVisibleElement()
    }

    private fun handleEvent(event: MainUiEvent) {
        when (event) {
            is MainUiEvent.RefreshStarted -> {
                playlistAdapter.setRefreshing(event.playlist.id, true)
                Toast.makeText(requireContext(), "正在同步“${event.playlist.name}”...", Toast.LENGTH_SHORT).show()
            }
            is MainUiEvent.RefreshSucceeded -> {
                playlistAdapter.setRefreshing(event.playlist.id, false)
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
            is MainUiEvent.PlaylistRenamed -> Toast.makeText(
                requireContext(),
                R.string.playlist_rename_success,
                Toast.LENGTH_SHORT,
            ).show()
            is MainUiEvent.PlaylistDeleted -> playlistAdapter.setEditMode(false)
            is MainUiEvent.Error -> Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openCreatePlaylist() {
        @Suppress("DEPRECATION")
        startActivityForResult(
            Intent(requireContext(), FileBrowserActivity::class.java)
                .putExtra("multiSelectMode", true),
            REQUEST_CREATE_PLAYLIST,
        )
    }

    private fun openPlaylist(playlist: Playlist) {
        startActivity(Intent(requireContext(), PlaybackActivity::class.java).putExtra("playlistDatabaseId", playlist.id))
    }

    private fun showPlaylistActions(playlist: Playlist) {
        val actions = arrayOf(
            getString(R.string.playlist_action_rename),
            getString(R.string.playlist_action_refresh),
            getString(R.string.playlist_action_delete),
        )
        AlertDialog.Builder(requireContext())
            .setTitle(playlist.name)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> showRenamePlaylistDialog(playlist)
                    1 -> viewModel.refreshPlaylist(playlist)
                    2 -> confirmDeletePlaylist(playlist)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showRenamePlaylistDialog(playlist: Playlist) {
        val input = EditText(requireContext()).apply {
            setText(playlist.name)
            setSelection(text.length)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            isSingleLine = true
            setSelectAllOnFocus(false)
        }
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.playlist_rename_title)
            .setView(input)
            .setPositiveButton(R.string.confirm, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val newName = input.text.toString().trim()
                if (newName.isEmpty()) {
                    input.error = getString(R.string.playlist_rename_empty)
                } else {
                    viewModel.renamePlaylist(playlist, newName)
                    dialog.dismiss()
                }
            }
            input.requestFocus()
        }
        dialog.show()
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
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun openSettings() {
        startActivity(Intent(requireContext(), SettingsActivity::class.java))
    }

    private fun requestFocusOnVisibleElement() {
        binding.root.post {
            val target = when (selectedSection) {
                HomeSection.PLAYLISTS -> when {
                    !playlistsEmpty && playlistAdapter.itemCount > 0 -> binding.rvPlaylists
                    binding.emptyPlaylist.visibility == View.VISIBLE -> binding.btnEmptyCreate
                    else -> binding.tabPlaylists
                }
                HomeSection.RECENT -> when {
                    !recentTasksEmpty && recentTaskAdapter.itemCount > 0 -> binding.rvRecentTasks
                    else -> binding.tabRecent
                }
            }
            target.requestFocus()
            if (target is androidx.recyclerview.widget.RecyclerView) {
                target.doOnLayout {
                    target.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CREATE_PLAYLIST && resultCode == Activity.RESULT_OK) {
            selectedSection = HomeSection.PLAYLISTS
            renderSelectedSection(requestFocus = true)
            // 文件浏览 Activity 返回后先刷新数据，再在 RecyclerView 完成布局后恢复到首张卡片。
            binding.root.postDelayed({ requestFocusOnVisibleElement() }, FOCUS_RESTORE_DELAY_MS)
            Toast.makeText(requireContext(), "播放列表已创建", Toast.LENGTH_SHORT).show()
        }
    }

    private enum class HomeSection { PLAYLISTS, RECENT }

    companion object {
        private const val REQUEST_CREATE_PLAYLIST = 1001
        private const val GRID_COLUMNS = 4
        private const val STATE_SECTION = "home_section"
        private const val FOCUS_RESTORE_DELAY_MS = 180L
    }
}
