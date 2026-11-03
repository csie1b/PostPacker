package com.postpacker.app.model

import java.io.File

data class PackagedArchive(
    val id: String,
    val platform: Platform,
    val author: String,
    val captionPreview: String,
    val zipFile: File,
    val mediaCount: Int,
    val packagedTimestamp: Long = System.currentTimeMillis()
)
