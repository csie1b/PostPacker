package com.postpacker.app.model

data class PostData(
    val id: String,
    val platform: Platform,
    val originalUrl: String,
    val author: String = "Unknown",
    val authorAvatarUrl: String? = null,
    val caption: String = "",
    val publishedAt: String = "",
    val mediaList: List<PostMedia> = emptyList(),
    val hashtags: List<String> = emptyList()
) {
    val totalMediaCount: Int get() = mediaList.size
    val hasVideo: Boolean get() = mediaList.any { it.type == MediaType.VIDEO }
}
