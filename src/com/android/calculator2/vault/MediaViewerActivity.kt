package com.android.calculator2.vault

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.android.calculator2.R
import java.io.File

class MediaViewerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MEDIA_LIST = "media_list"
        const val EXTRA_MEDIA_TYPES = "media_types"
        const val EXTRA_POSITION = "position"
    }

    private lateinit var viewPager: ViewPager2
    private lateinit var textIndicator: android.widget.TextView
    private var paths: List<String> = emptyList()
    private var types: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media_viewer)

        viewPager = findViewById(R.id.view_pager)
        textIndicator = findViewById(R.id.text_indicator)

        paths = intent.getStringArrayListExtra(EXTRA_MEDIA_LIST) ?: emptyList()
        types = intent.getStringArrayListExtra(EXTRA_MEDIA_TYPES) ?: emptyList()
        val position = intent.getIntExtra(EXTRA_POSITION, 0)

        if (paths.isEmpty()) {
            finish()
            return
        }

        val adapter = ViewerAdapter(paths, types)
        viewPager.adapter = adapter
        viewPager.setCurrentItem(position, false)
        updateIndicator(position)

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(pos: Int) {
                updateIndicator(pos)
                // 停止之前的视频播放
                adapter.stopVideoAt(pos)
            }
        })
    }

    private fun updateIndicator(position: Int) {
        textIndicator.text = "${position + 1}/${paths.size}"
    }

    override fun onDestroy() {
        super.onDestroy()
        // 释放资源
    }

    private class ViewerAdapter(
        private val paths: List<String>,
        private val types: List<String>
    ) : RecyclerView.Adapter<ViewerAdapter.ViewHolder>() {

        private var currentVideoView: VideoView? = null

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val imageView: ImageView = view.findViewById(R.id.image_view)
            val videoView: VideoView = view.findViewById(R.id.video_view)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_media_viewer, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val path = paths[position]
            val type = types[position]
            val file = File(path)

            if (type == MediaType.VIDEO.name) {
                holder.imageView.visibility = View.GONE
                holder.videoView.visibility = View.VISIBLE

                // 生成视频第一帧作为预览
                val thumb = VaultRepository.getInstance(holder.itemView.context)
                    .getThumbnail(path, MediaType.VIDEO)
                if (thumb != null) {
                    holder.imageView.setImageBitmap(thumb)
                    holder.imageView.visibility = View.VISIBLE
                }

                holder.videoView.setVideoPath(path)
                holder.videoView.setOnPreparedListener { mp ->
                    mp.isLooping = true
                    mp.start()
                    holder.imageView.visibility = View.GONE
                }
                holder.videoView.setOnErrorListener { _, _, _ ->
                    true
                }

                currentVideoView = holder.videoView
            } else {
                holder.videoView.visibility = View.GONE
                holder.imageView.visibility = View.VISIBLE

                // 加载图片，支持大图
                Thread {
                    try {
                        val options = BitmapFactory.Options().apply {
                            inJustDecodeBounds = true
                        }
                        BitmapFactory.decodeFile(path, options)

                        // 适配屏幕大小
                        val maxSize = 2048
                        options.inSampleSize = calculateInSampleSize(options, maxSize, maxSize)
                        options.inJustDecodeBounds = false

                        val bitmap = BitmapFactory.decodeFile(path, options)
                        holder.imageView.post {
                            if (bitmap != null) {
                                holder.imageView.setImageBitmap(bitmap)
                            }
                        }
                    } catch (e: OutOfMemoryError) {
                        // 超大图片进一步压缩
                        val options = BitmapFactory.Options().apply {
                            inSampleSize = 4
                        }
                        val bitmap = BitmapFactory.decodeFile(path, options)
                        holder.imageView.post {
                            if (bitmap != null) {
                                holder.imageView.setImageBitmap(bitmap)
                            }
                        }
                    }
                }.start()
            }

            // 点击关闭
            holder.itemView.setOnClickListener {
                (holder.itemView.context as? AppCompatActivity)?.onBackPressedDispatcher?.onBackPressed()
            }
        }

        override fun getItemCount() = paths.size

        fun stopVideoAt(currentPos: Int) {
            currentVideoView?.let {
                if (it.isPlaying) {
                    it.pause()
                }
            }
        }

        private fun calculateInSampleSize(
            options: BitmapFactory.Options,
            reqWidth: Int,
            reqHeight: Int
        ): Int {
            val height = options.outHeight
            val width = options.outWidth
            var inSampleSize = 1

            if (height > reqHeight || width > reqWidth) {
                val halfHeight = height / 2
                val halfWidth = width / 2

                while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                    inSampleSize *= 2
                }
            }
            return inSampleSize
        }
    }
}