package com.baidu.tv.player.kt.ui.filebrowser

import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.baidu.tv.player.kt.R
import com.baidu.tv.player.kt.databinding.ItemFileBinding
import com.baidu.tv.player.kt.databinding.ItemFileGridBinding
import com.baidu.tv.player.kt.model.FileInfo
import com.bumptech.glide.Glide
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileAdapter : RecyclerView.Adapter<FileViewHolder>() {

    private val files = mutableListOf<FileInfo>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    var onItemClick: ((FileInfo, Int) -> Unit)? = null
    var onItemLongClick: ((FileInfo, Int) -> Boolean)? = null
    var onItemNavigateLeft: (() -> Boolean)? = null
    var isGridMode: Boolean = false
        private set

    var multiSelectMode: Boolean = false
        private set
    private var selectedPaths: Set<String> = emptySet()

    fun submitFiles(newFiles: List<FileInfo>) {
        files.clear()
        files.addAll(newFiles)
        notifyDataSetChanged()
    }

    fun setGridMode(enabled: Boolean) {
        if (isGridMode == enabled) return
        isGridMode = enabled
        notifyDataSetChanged()
    }

    fun setMultiSelectMode(enabled: Boolean) {
        if (multiSelectMode == enabled) return
        multiSelectMode = enabled
        notifyDataSetChanged()
    }

    fun setSelectedPaths(paths: Set<String>) {
        selectedPaths = paths
        notifyDataSetChanged()
    }

    fun getSelectedPaths(): Set<String> = selectedPaths

    fun getItem(position: Int): FileInfo = files[position]

    override fun getItemViewType(position: Int): Int = if (isGridMode) VIEW_TYPE_GRID else VIEW_TYPE_LIST

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_GRID) {
            FileViewHolder(ItemFileGridBinding.inflate(inflater, parent, false))
        } else {
            FileViewHolder(ItemFileBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val file = files[position]
        holder.bind(file, multiSelectMode, selectedPaths.contains(file.path), dateFormat)
        holder.itemView.setOnClickListener { onItemClick?.invoke(file, holder.bindingAdapterPosition) }
        holder.itemView.setOnLongClickListener { onItemLongClick?.invoke(file, holder.bindingAdapterPosition) ?: false }
        holder.itemView.setOnKeyListener { _, keyCode, event ->
            if (event.action == android.view.KeyEvent.ACTION_DOWN && keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
                onItemNavigateLeft?.invoke() ?: false
            } else {
                false
            }
        }
    }

    override fun getItemCount(): Int = files.size

    companion object {
        private const val VIEW_TYPE_LIST = 0
        private const val VIEW_TYPE_GRID = 1
    }
}

class FileViewHolder private constructor(
    private val rootView: View,
    private val iconView: android.widget.ImageView,
    private val nameView: android.widget.TextView,
    private val infoView: android.widget.TextView,
    private val selectedView: android.widget.ImageView,
) : RecyclerView.ViewHolder(rootView) {
    val binding: ItemFileBinding
        get() = ItemFileBinding.bind(rootView)

    constructor(binding: ItemFileBinding) : this(
        binding.root,
        binding.ivFileIcon,
        binding.tvFileName,
        binding.tvFileInfo,
        binding.ivSelected,
    )

    constructor(binding: ItemFileGridBinding) : this(
        binding.root,
        binding.ivFileIcon,
        binding.tvFileName,
        binding.tvFileInfo,
        binding.ivSelected,
    )

    fun bind(file: FileInfo, multiSelectMode: Boolean, selected: Boolean, dateFormat: SimpleDateFormat) {
        nameView.text = file.serverFilename ?: file.path.orEmpty()
        bindIcon(file)
        infoView.text = buildFileInfo(file, dateFormat)
        selectedView.visibility = if (multiSelectMode) View.VISIBLE else View.GONE
        selectedView.setImageResource(
            if (selected) android.R.drawable.checkbox_on_background else android.R.drawable.checkbox_off_background,
        )
        rootView.isSelected = selected
    }

    private fun bindIcon(file: FileInfo) {
        val thumbUrl = file.thumbs?.url1
        when {
            file.isDirectory() -> iconView.setImageResource(R.drawable.ic_browse_folder)
            file.isImage() -> loadThumb(thumbUrl, android.R.drawable.ic_menu_gallery)
            file.isVideo() -> loadThumb(thumbUrl, android.R.drawable.ic_media_play)
            else -> iconView.setImageResource(android.R.drawable.ic_menu_info_details)
        }
    }

    private fun loadThumb(url: String?, placeholder: Int) {
        if (url.isNullOrEmpty()) {
            iconView.setImageResource(placeholder)
        } else {
            Glide.with(iconView)
                .load(url)
                .placeholder(placeholder)
                .error(placeholder)
                .timeout(30_000)
                .into(iconView)
        }
    }

    private fun buildFileInfo(file: FileInfo, dateFormat: SimpleDateFormat): String {
        val base = if (file.isDirectory()) "文件夹" else Formatter.formatFileSize(rootView.context, file.size)
        return if (file.serverMtime > 0) {
            "$base · ${dateFormat.format(Date(file.serverMtime * 1000))}"
        } else {
            base
        }
    }
}
