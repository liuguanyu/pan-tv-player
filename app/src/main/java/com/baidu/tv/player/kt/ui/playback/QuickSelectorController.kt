package com.baidu.tv.player.kt.ui.playback

import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.baidu.tv.player.kt.model.FileInfo
import com.baidu.tv.player.kt.repository.ThumbnailProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 快速选播控制器（Phase 10.4）。
 *
 * 从 [PlaybackActivity] 抽出快速选播列表的全部状态与交互：
 * - adapter 配置 / 数据变化重绑
 * - 显示 / 隐藏 / 同步当前项
 * - 焦点恢复（含有限重试）
 * - 确认选播
 * - 异步缩略图刷新
 *
 * 自动切换策略（Task 10.3）：
 * - 用户正在浏览时，播放项变化只调用 [PlaylistQuickSelectorAdapter.setHighlightedFile]
 *   更新底部蓝条，不抢焦点。
 * - 重新打开（show）时，adapter 重新配置并聚焦当前播放项。
 *
 * @param adapter 已创建的适配器（Activity 负责生命周期，Controller 负责状态）。
 * @param recyclerView 已布局的 RecyclerView（Activity 负责 findViewById）。
 * @param thumbnailProvider 缩略图数据边界。
 * @param scope 生命周期作用域，用于异步获取缩略图。
 * @param focusHost 隐藏时回收焦点的宿主 View（通常为播放页根布局）。
 */
class QuickSelectorController(
    private val adapter: PlaylistQuickSelectorAdapter,
    private val recyclerView: RecyclerView,
    private val thumbnailProvider: ThumbnailProvider,
    private val scope: CoroutineScope,
    private val focusHost: View,
) {

    /** 列表是否当前可见。 */
    var visible: Boolean = false
        private set

    private var dataKey: String? = null
    private var currentKey: String? = null
    private var onItemSelected: ((FileInfo) -> Unit)? = null

    /** 焦点恢复最大重试次数（ViewHolder 可能尚未布局，需要 post 延迟重试）。 */
    private val focusRetryLimit = 2

    /**
     * 显示快速选播列表。
     *
     * 倒序展示 [files]，高亮 [currentFile]，聚焦当前项，并异步拉取缩略图。
     * 选中某项时回调 [onItemSelected]（Controller 会先隐藏再回调）。
     */
    fun show(files: List<FileInfo>, currentFile: FileInfo?, onItemSelected: (FileInfo) -> Unit) {
        if (files.isEmpty()) return
        visible = true
        this.onItemSelected = onItemSelected
        reconfigure(files, currentFile)
        recyclerView.visibility = View.VISIBLE
        focusCurrentItem(currentFile)
        fetchThumbnails(files)
    }

    /** 隐藏快速选播列表，回收焦点到 [focusHost]。 */
    fun hide() {
        visible = false
        currentKey = null
        dataKey = null
        recyclerView.visibility = View.GONE
        recyclerView.clearFocus()
        focusHost.requestFocus()
    }

    /**
     * 同步当前播放项。
     *
     * 数据变化（播放列表改变）时重新配置 adapter 并刷新缩略图；
     * 否则只局部更新高亮（不抢焦点），满足"自动切换只更新蓝条"策略。
     *
     * 若当前项 key 变化，尝试聚焦新当前项（用户未主动浏览时恢复焦点）。
     */
    fun syncCurrent(files: List<FileInfo>, currentFile: FileInfo?) {
        if (!visible) return
        val key = files.dataKey()
        if (key != dataKey) {
            reconfigure(files, currentFile)
            fetchThumbnails(files)
        } else {
            adapter.setHighlightedFile(currentFile)
        }
        val newCurrentKey = currentFile?.fileKey()
        if (newCurrentKey != currentKey) {
            focusCurrentItem(currentFile)
        }
    }

    /** 异步更新缩略图（缩略图晚到时调用，不抢焦点、不崩溃）。 */
    fun updateThumbnails(thumbnails: Map<Long, String>) {
        if (!visible || thumbnails.isEmpty()) return
        adapter.updateThumbnails(thumbnails)
    }

    /**
     * 确认当前焦点项选播。
     *
     * @return true 表示已确认（已隐藏并回调）；false 表示当前无有效焦点项，什么都不做。
     */
    fun confirmSelection(): Boolean {
        val focusedChild = recyclerView.focusedChild
        val position = focusedChild?.let(recyclerView::getChildAdapterPosition)
            ?: RecyclerView.NO_POSITION
        return adapter.selectAt(position)
    }

    /**
     * 聚焦当前播放项。
     *
     * 查找 [currentFile] 在 adapter 中的位置，滚动到屏幕 1/3 处并请求焦点。
     * ViewHolder 可能尚未布局，通过 post 延迟并在失败时有限重试。
     */
    fun focusCurrentItem(currentFile: FileInfo?) {
        val position = adapter.positionOf(currentFile)
        if (position == RecyclerView.NO_POSITION) return
        currentKey = currentFile?.fileKey()
        requestFocusWithRetry(position, 0)
    }

    private fun requestFocusWithRetry(position: Int, attempt: Int) {
        recyclerView.post {
            val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return@post
            lm.scrollToPositionWithOffset(position, recyclerView.width / 3)
            recyclerView.post {
                val holder = recyclerView.findViewHolderForAdapterPosition(position)
                if (holder != null) {
                    holder.itemView.requestFocus()
                } else if (attempt < focusRetryLimit) {
                    requestFocusWithRetry(position, attempt + 1)
                }
            }
        }
    }

    private fun reconfigure(
        files: List<FileInfo>,
        currentFile: FileInfo?,
    ) {
        dataKey = files.dataKey()
        currentKey = null
        adapter.configure(files.reversed(), currentFile) { selectedFile ->
            hide()
            onItemSelected?.invoke(selectedFile)
        }
    }

    private fun fetchThumbnails(files: List<FileInfo>) {
        scope.launch {
            try {
                val thumbnails = thumbnailProvider.fetchThumbnails(files)
                updateThumbnails(thumbnails)
            } catch (e: Exception) {
                Log.w(TAG, "获取选播列表缩略图失败", e)
            }
        }
    }

    companion object {
        private const val TAG = "QuickSelectorController"

        /** 配置 RecyclerView 布局管理器与焦点策略（在 Activity 初始化时调用一次）。 */
        fun setupRecyclerView(recyclerView: RecyclerView) {
            recyclerView.apply {
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                isFocusable = false
                descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            }
        }

        internal fun FileInfo.fileKey(): String =
            if (fsId != 0L) "fs:$fsId" else "path:${path.orEmpty()}"

        internal fun List<FileInfo>.dataKey(): String =
            joinToString("|") { it.fileKey() }
    }
}
