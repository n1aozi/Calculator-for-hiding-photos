package com.android.calculator2.vault

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.android.calculator2.R

class MediaAdapter(
    var items: List<MediaItem>,
    private val onItemClickListener: OnItemClickListener
) : RecyclerView.Adapter<MediaAdapter.ViewHolder>() {

    interface OnItemClickListener {
        fun onItemClick(position: Int, item: MediaItem)
        fun onItemLongClick(position: Int, item: MediaItem): Boolean
    }

    var isSelectMode = false

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val thumbnail: ImageView = view.findViewById(R.id.image_thumbnail)
        val videoIcon: ImageView = view.findViewById(R.id.icon_video)
        val checkbox: CheckBox = view.findViewById(R.id.checkbox)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_media_grid, parent, false)
        // 设置正方形比例
        view.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        // 加载缩略图
        loadThumbnail(item, holder.thumbnail)

        // 视频图标
        holder.videoIcon.visibility = if (item.type == MediaType.VIDEO) View.VISIBLE else View.GONE

        // 多选模式
        holder.checkbox.visibility = if (isSelectMode) View.VISIBLE else View.GONE
        holder.checkbox.setOnCheckedChangeListener(null)
        holder.checkbox.isChecked = item.selected
        holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
            item.selected = isChecked
        }

        // 点击事件
        holder.itemView.setOnClickListener {
            onItemClickListener.onItemClick(position, item)
        }
        holder.itemView.setOnLongClickListener {
            onItemClickListener.onItemLongClick(position, item)
        }
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<MediaItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun getSelectedItems(): List<MediaItem> {
        return items.filter { it.selected }
    }

    fun selectAll() {
        items.forEach { it.selected = true }
        notifyDataSetChanged()
    }

    fun deselectAll() {
        items.forEach { it.selected = false }
        notifyDataSetChanged()
    }

    private fun loadThumbnail(item: MediaItem, imageView: ImageView) {
        // 在后台线程生成缩略图
        Thread {
            val repo = VaultRepository.getInstance(imageView.context)
            val bitmap = repo.getThumbnail(item.path, item.type)
            imageView.post {
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap)
                } else {
                    imageView.setImageResource(android.R.drawable.ic_menu_gallery)
                }
            }
        }.start()
    }
}