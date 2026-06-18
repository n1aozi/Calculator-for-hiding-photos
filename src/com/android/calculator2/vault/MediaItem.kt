package com.android.calculator2.vault

data class MediaItem(
    val id: Long,
    val name: String,
    val path: String,
    val type: MediaType,
    val size: Long,
    val dateAdded: Long,
    var selected: Boolean = false
)

enum class MediaType { IMAGE, VIDEO }