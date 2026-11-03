package com.postpacker.app.model

enum class MediaType {
    IMAGE,
    VIDEO
}

data class PostMedia(
    val url: String,
    val type: MediaType = MediaType.IMAGE,
    val thumbnailUrl: String? = null,
    val suggestedFileName: String = if (type == MediaType.VIDEO) "media.mp4" else "media.jpg"
)
