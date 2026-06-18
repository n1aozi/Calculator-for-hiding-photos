package com.android.calculator2.vault

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

class VaultRepository private constructor(private val context: Context) {

    companion object {
        private const val VAULT_DIR_NAME = ".vault"
        private const val IMAGES_DIR_NAME = "images"
        private const val VIDEOS_DIR_NAME = "videos"
        private const val NOMEDIA_FILE_NAME = ".nomedia"

        // 支持的图片扩展名
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "heic", "gif")
        // 支持的视频扩展名
        private val VIDEO_EXTENSIONS = setOf("mp4", "avi")

        // 缩略图尺寸
        private const val THUMB_SIZE = 256

        @Volatile
        private var instance: VaultRepository? = null

        fun getInstance(context: Context): VaultRepository {
            return instance ?: synchronized(this) {
                instance ?: VaultRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    private val vaultDir = File(context.filesDir, VAULT_DIR_NAME)
    private val imagesDir = File(vaultDir, IMAGES_DIR_NAME)
    private val videosDir = File(vaultDir, VIDEOS_DIR_NAME)

    fun initVault() {
        migrateFromOldDir()
        imagesDir.mkdirs()
        videosDir.mkdirs()
        File(vaultDir, NOMEDIA_FILE_NAME).createNewFile()
    }

    private fun migrateFromOldDir() {
        val oldDir = File(context.filesDir, "vault")
        if (oldDir.exists() && oldDir.isDirectory && !vaultDir.exists()) {
            oldDir.renameTo(vaultDir)
        } else if (oldDir.exists() && vaultDir.exists()) {
            // 新旧目录都存在，合并内容
            val oldImages = File(oldDir, IMAGES_DIR_NAME)
            val oldVideos = File(oldDir, VIDEOS_DIR_NAME)
            if (oldImages.exists()) {
                oldImages.listFiles()?.forEach { it.renameTo(File(imagesDir, it.name)) }
                oldImages.delete()
            }
            if (oldVideos.exists()) {
                oldVideos.listFiles()?.forEach { it.renameTo(File(videosDir, it.name)) }
                oldVideos.delete()
            }
            val oldNomedia = File(oldDir, NOMEDIA_FILE_NAME)
            if (oldNomedia.exists()) oldNomedia.delete()
            oldDir.delete()
        }
    }

    data class ImportResult(
        val items: List<MediaItem>,
        val mediaStoreUris: List<Uri>
    )

    fun importFromGallery(uris: List<android.net.Uri>): ImportResult {
        initVault()
        val imported = mutableListOf<MediaItem>()
        val mediaStoreUris = mutableListOf<Uri>()
        var itemId = System.currentTimeMillis()

        for (uri in uris) {
            try {
                val mimeType = context.contentResolver.getType(uri) ?: continue
                val isVideo = mimeType.startsWith("video/")
                val targetDir = if (isVideo) videosDir else imagesDir

                val displayName = getDisplayName(uri) ?: "file_${itemId}"
                val extension = getExtensionFromMime(mimeType) ?: getExtensionFromName(displayName) ?: continue

                if (!isVideo && extension.lowercase() !in IMAGE_EXTENSIONS) continue
                if (isVideo && extension.lowercase() !in VIDEO_EXTENSIONS) continue

                val destFile = File(targetDir, "${displayName}")
                var actualDest = destFile
                var counter = 1
                while (actualDest.exists()) {
                    val nameWithoutExt = displayName.substringBeforeLast(".", displayName)
                    actualDest = File(targetDir, "${nameWithoutExt}_${counter}.$extension")
                    counter++
                }

                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(actualDest).use { output ->
                        input.copyTo(output)
                    }
                } ?: continue

                val mediaType = if (isVideo) MediaType.VIDEO else MediaType.IMAGE
                imported.add(
                    MediaItem(
                        id = itemId,
                        name = actualDest.name,
                        path = actualDest.absolutePath,
                        type = mediaType,
                        size = actualDest.length(),
                        dateAdded = System.currentTimeMillis()
                    )
                )

                resolveMediaStoreUri(uri, isVideo, displayName, actualDest.length())?.let { mediaStoreUris.add(it) }

                itemId++
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return ImportResult(imported, mediaStoreUris)
    }

    private fun resolveMediaStoreUri(safUri: Uri, isVideo: Boolean, displayName: String? = null, fileSize: Long = -1): Uri? {
        // 如果 URI 已经是 MediaStore URI，直接返回
        if (safUri.authority == "media") {
            return safUri
        }

        // SAF 媒体 URI 格式: content://com.android.providers.media.documents/document/image:123
        if (safUri.authority == "com.android.providers.media.documents") {
            val segments = safUri.lastPathSegment?.split(":")
            if (segments?.size == 2) {
                val id = segments[1].toLongOrNull()
                if (id != null) {
                    val collection = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    return ContentUris.withAppendedId(collection, id)
                }
            }
        }

        // 回退1：通过 DATA 列匹配文件路径
        findMediaStoreUriByData(safUri, isVideo)?.let { return it }

        // 回退2：通过显示名称和文件大小匹配
        if (displayName != null) {
            findMediaStoreUriByDisplayName(displayName, isVideo, fileSize)?.let { return it }
        }

        return null
    }

    private fun findMediaStoreUriByDisplayName(displayName: String, isVideo: Boolean, fileSize: Long = -1): Uri? {
        val collection = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection: String
        val selectionArgs: Array<String>

        if (fileSize > 0) {
            selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.SIZE} = ?"
            selectionArgs = arrayOf(displayName, fileSize.toString())
        } else {
            selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
            selectionArgs = arrayOf(displayName)
        }

        context.contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                return ContentUris.withAppendedId(collection, id)
            }
        }
        return null
    }

    private fun findMediaStoreUriByData(safUri: Uri, isVideo: Boolean): Uri? {
        // 尝试通过 SAF 获取文件路径，再在 MediaStore 中查找
        var filePath: String? = null
        context.contentResolver.query(safUri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val dataIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                if (dataIndex >= 0) {
                    filePath = cursor.getString(dataIndex)
                }
            }
        }

        if (filePath != null) {
            val collection = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DATA} = ?",
                arrayOf(filePath),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    return ContentUris.withAppendedId(collection, id)
                }
            }
        }
        return null
    }

    fun exportToGallery(items: List<MediaItem>) {
        for (item in items) {
            try {
                val sourceFile = File(item.path)
                if (!sourceFile.exists()) continue

                val success = when (item.type) {
                    MediaType.IMAGE -> exportImageToGallery(sourceFile, item.name)
                    MediaType.VIDEO -> exportVideoToGallery(sourceFile, item.name)
                }

                if (success) {
                    sourceFile.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun exportImageToGallery(sourceFile: File, displayName: String): Boolean {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, getMimeTypeFromName(displayName))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues) ?: return false

        context.contentResolver.openOutputStream(uri)?.use { output ->
            FileInputStream(sourceFile).use { input ->
                input.copyTo(output)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, contentValues, null, null)
        }

        return true
    }

    private fun exportVideoToGallery(sourceFile: File, displayName: String): Boolean {
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, getMimeTypeFromName(displayName))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES)
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues) ?: return false

        context.contentResolver.openOutputStream(uri)?.use { output ->
            FileInputStream(sourceFile).use { input ->
                input.copyTo(output)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, contentValues, null, null)
        }

        return true
    }

    fun deleteItems(items: List<MediaItem>) {
        for (item in items) {
            try {
                val file = File(item.path)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getAllMedia(): List<MediaItem> {
        initVault()
        val items = mutableListOf<MediaItem>()
        var itemId = 1L

        scanDirectory(imagesDir, MediaType.IMAGE, items, { itemId++ })
        scanDirectory(videosDir, MediaType.VIDEO, items, { itemId++ })

        // 按添加时间倒序
        items.sortByDescending { it.dateAdded }
        return items
    }

    private fun scanDirectory(
        dir: File,
        type: MediaType,
        items: MutableList<MediaItem>,
        idGenerator: () -> Long
    ) {
        if (!dir.exists() || !dir.isDirectory) return

        val extensions = if (type == MediaType.IMAGE) IMAGE_EXTENSIONS else VIDEO_EXTENSIONS

        dir.listFiles()?.forEach { file ->
            if (file.isFile) {
                val ext = file.extension.lowercase()
                if (ext in extensions) {
                    items.add(
                        MediaItem(
                            id = idGenerator(),
                            name = file.name,
                            path = file.absolutePath,
                            type = type,
                            size = file.length(),
                            dateAdded = file.lastModified()
                        )
                    )
                }
            }
        }
    }

    fun getThumbnail(path: String, type: MediaType): Bitmap? {
        return try {
            val file = File(path)
            if (!file.exists()) return null

            when (type) {
                MediaType.IMAGE -> {
                    // 图片缩略图：先压缩采样加载
                    val options = android.graphics.BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    android.graphics.BitmapFactory.decodeFile(path, options)

                    options.inSampleSize = calculateInSampleSize(options, THUMB_SIZE, THUMB_SIZE)
                    options.inJustDecodeBounds = false

                    android.graphics.BitmapFactory.decodeFile(path, options)
                }
                MediaType.VIDEO -> {
                    // 视频缩略图：使用 ThumbnailUtils
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ThumbnailUtils.createVideoThumbnail(file, android.util.Size(THUMB_SIZE, THUMB_SIZE), null)
                    } else {
                        @Suppress("DEPRECATION")
                        ThumbnailUtils.createVideoThumbnail(path, MediaStore.Video.Thumbnails.MINI_KIND)
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(
        options: android.graphics.BitmapFactory.Options,
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

    private fun getDisplayName(uri: android.net.Uri): String? {
        var name: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    name = cursor.getString(nameIndex)
                }
            }
        }
        return name
    }

    private fun getExtensionFromMime(mimeType: String): String? {
        return MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
    }

    private fun getExtensionFromName(name: String): String? {
        val dotIndex = name.lastIndexOf('.')
        return if (dotIndex >= 0 && dotIndex < name.length - 1) {
            name.substring(dotIndex + 1).lowercase()
        } else {
            null
        }
    }

    private fun getMimeTypeFromName(name: String): String {
        val extension = getExtensionFromName(name) ?: return "application/octet-stream"
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
    }
}