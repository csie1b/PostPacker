package com.postpacker.app.packager

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.postpacker.app.model.MediaType
import com.postpacker.app.model.PostData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale

class MediaGallerySaver(private val context: Context) {

    private val client = OkHttpClient()

    suspend fun savePostMediaToGallery(
        post: PostData,
        onProgress: (current: Int, total: Int, name: String) -> Unit
    ): Result<Int> = withContext(Dispatchers.IO) {
        var savedCount = 0
        try {
            val total = post.mediaList.size
            post.mediaList.forEachIndexed { index, media ->
                val isVideo = media.type == MediaType.VIDEO
                val ext = if (isVideo) "mp4" else "jpg"
                val mimeType = if (isVideo) "video/mp4" else "image/jpeg"
                val safeAuthor = post.author.replace(Regex("[^a-zA-Z0-9_]"), "_").take(15)
                val fileName = "PostPacker_${safeAuthor}_${System.currentTimeMillis()}_${index + 1}.$ext"

                onProgress(index + 1, total, "正在儲存至相簿 (${index + 1}/$total)")

                val uri = insertMediaEntry(fileName, mimeType, isVideo)
                if (uri != null) {
                    val downloaded = downloadToUri(media.url, uri)
                    if (downloaded) {
                        savedCount++
                    }
                }
            }
            Result.success(savedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun insertMediaEntry(fileName: String, mimeType: String, isVideo: Boolean): Uri? {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val subDir = if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$subDir/PostPacker")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val collectionUri = if (isVideo) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
        }

        val uri = resolver.insert(collectionUri, contentValues)

        if (uri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }
        return uri
    }

    private fun downloadToUri(url: String, uri: Uri): Boolean {
        val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false
                val inStream: InputStream? = response.body?.byteStream()
                val outStream: OutputStream? = context.contentResolver.openOutputStream(uri)
                if (inStream != null && outStream != null) {
                    inStream.use { input ->
                        outStream.use { output ->
                            input.copyTo(output)
                        }
                    }
                    true
                } else false
            }
        } catch (e: Exception) {
            false
        }
    }
}
