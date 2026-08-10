package com.baidu.tv.player.kt.ui.filebrowser

import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.baidu.tv.player.kt.R
import com.baidu.tv.player.kt.databinding.ItemFileBinding
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

    /** 列表项上按「左键」时回调（用于快速跳回顶部操作按钮），返回 true 表示已消费。 */
    var onItemNavigateLeft: (() -> Boolean)? = null

    var multiSelectMode: Boolean = false
        private set
    private var selectedPaths: Set<String> = emptySet()

    fun submitFiles(newFiles: List<FileInfo>) {
        files.clear()
        files.addAll(newFiles)
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val binding = ItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FileViewHolder(binding)
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
}

class FileViewHolder(val binding: ItemFileBinding) : RecyclerView.ViewHolder(binding.root) {
    fun bind(file: FileInfo, multiSelectMode: Boolean, selected: Boolean, dateFormat: SimpleDateFormat) {
        binding.tvFileName.text = file.serverFilename ?: file.path.orEmpty()
        bindIcon(file)
        binding.tvFileInfo.text = buildFileInfo(file, dateFormat)
        binding.ivSelected.visibility = if (multiSelectMode) View.VISIBLE else View.GONE
        binding.ivSelected.setImageResource(
            if (selected) android.R.drawable.checkbox_on_background else android.R.drawable.checkbox_off_background,
        )
        binding.root.isSelected = selected
    }

    private fun bindIcon(file: FileInfo) {
        val thumbUrl = file.thumbs?.url1
        when {
            file.isDirectory() -> binding.ivFileIcon.setImageResource(android.R.drawable.ic_menu_view)
            file.isImage() -> loadThumb(thumbUrl, android.R.drawable.ic_menu_gallery)
            file.isVideo() -> loadThumb(thumbUrl, android.R.drawable.ic_media_play)
            else -> binding.ivFileIcon.setImageResource(android.R.drawable.ic_menu_info_details)
        }
    }

    private fun loadThumb(url: String?, placeholder: Int) {
        if (url.isNullOrEmpty()) {
            binding.ivFileIcon.setImageResource(placeholder)
        } else {
            Glide.with(binding.ivFileIcon)
                .load(url)
                .placeholder(placeholder)
                .error(placeholder)
                .timeout(30_000)
                .into(binding.ivFileIcon)
        }
    }

    private fun buildFileInfo(file: FileInfo, dateFormat: SimpleDateFormat): String {
        val base = if (file.isDirectory()) "文件夹" else Formatter.formatFileSize(binding.root.context, file.size)
        return if (file.serverMtime > 0) {
            "$base · ${dateFormat.format(Date(file.serverMtime * 1000))}"
        } else {
            base
        }
    }
}
