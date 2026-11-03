package com.postpacker.app.packager

import android.content.Context
import com.google.gson.GsonBuilder
import com.postpacker.app.model.MediaType
import com.postpacker.app.model.PostData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipPackager(private val context: Context) {

    private val client = OkHttpClient()
    private val gson = GsonBuilder().setPrettyPrinting().create()

    suspend fun packagePost(
        post: PostData,
        onProgress: (current: Int, total: Int, stepName: String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val totalSteps = post.mediaList.size + 3 // media downloads + txt + md + json + zip
            var currentStep = 0

            // 1. Setup temporary directory for this archive
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val safeAuthor = post.author.replace(Regex("[^a-zA-Z0-9_\\-\\u4e00-\\u9fa5]"), "_").take(20)
            val baseName = "${post.platform.name.lowercase()}_${safeAuthor}_$timestamp"
            val tempDir = File(context.cacheDir, "temp_pack_$baseName")
            if (tempDir.exists()) tempDir.deleteRecursively()
            tempDir.mkdirs()

            val mediaDir = File(tempDir, "media")
            mediaDir.mkdirs()

            // 2. Download media files
            val downloadedFiles = mutableListOf<File>()
            post.mediaList.forEachIndexed { index, media ->
                currentStep++
                val ext = if (media.type == MediaType.VIDEO) "mp4" else "jpg"
                val filename = String.format(Locale.US, "%02d_%s.%s", index + 1, media.type.name.lowercase(), ext)
                val targetFile = File(mediaDir, filename)

                onProgress(currentStep, totalSteps, "正在下載媒體 (${index + 1}/${post.mediaList.size}): $filename")
                downloadFile(media.url, targetFile)
                downloadedFiles.add(targetFile)
            }

            // 3. Generate post_content.txt
            currentStep++
            onProgress(currentStep, totalSteps, "正在生成純文字文案...")
            val textFile = File(tempDir, "post_content.txt")
            val textContent = buildString {
                appendLine("==================================================")
                appendLine("社群貼文打包檔案 - PostPacker")
                appendLine("==================================================")
                appendLine("平台: ${post.platform.displayName}")
                appendLine("作者: ${post.author}")
                appendLine("原始網址: ${post.originalUrl}")
                appendLine("發布時間: ${post.publishedAt}")
                appendLine("打包時間: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
                appendLine("附件數量: ${post.mediaList.size} 個媒體檔案")
                if (post.hashtags.isNotEmpty()) {
                    appendLine("標籤: ${post.hashtags.joinToString(" ") { "#$it" }}")
                }
                appendLine("--------------------------------------------------")
                appendLine("貼文內文:")
                appendLine("--------------------------------------------------")
                appendLine(post.caption)
                appendLine("==================================================")
            }
            textFile.writeText(textContent)

            // 4. Generate post_content.md (Markdown)
            currentStep++
            onProgress(currentStep, totalSteps, "正在生成 Markdown 文檔...")
            val mdFile = File(tempDir, "README.md")
            val mdContent = buildString {
                appendLine("# 貼文備份 - ${post.author} (${post.platform.displayName})")
                appendLine()
                appendLine("- **原始網址**: [點此查看原貼文](${post.originalUrl})")
                appendLine("- **發布/備份日期**: ${post.publishedAt.ifBlank { timestamp }}")
                appendLine("- **媒體檔案數量**: ${downloadedFiles.size}")
                appendLine()
                appendLine("## 📝 貼文內文")
                appendLine()
                appendLine("> ${post.caption.replace("\n", "\n> ")}")
                appendLine()
                if (post.hashtags.isNotEmpty()) {
                    appendLine("### 🏷️ 標籤")
                    appendLine(post.hashtags.joinToString(" ") { "`#$it`" })
                    appendLine()
                }
                appendLine("## 🖼️ 媒體附件預覽")
                appendLine()
                downloadedFiles.forEachIndexed { i, file ->
                    val isVideo = file.name.endsWith(".mp4")
                    if (isVideo) {
                        appendLine("- 🎥 **影片**: `media/${file.name}`")
                    } else {
                        appendLine("![附件 ${i + 1}](media/${file.name})")
                        appendLine()
                    }
                }
            }
            mdFile.writeText(mdContent)

            // 5. Generate metadata.json
            currentStep++
            onProgress(currentStep, totalSteps, "正在生成結構化數據 (JSON)...")
            val jsonFile = File(tempDir, "metadata.json")
            jsonFile.writeText(gson.toJson(post))

            // 6. Compress into ZIP
            onProgress(totalSteps, totalSteps, "正在壓縮打包為 ZIP 檔案...")
            val exportDir = File(context.getExternalFilesDir(null), "packaged_posts")
            exportDir.mkdirs()
            val zipFile = File(exportDir, "PostPacker_${baseName}.zip")

            zipDirectory(tempDir, zipFile)

            // Cleanup temp dir
            tempDir.deleteRecursively()

            Result.success(zipFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun downloadFile(url: String, destination: File) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to download media: HTTP ${response.code}")
            }
            response.body?.byteStream()?.use { input ->
                FileOutputStream(destination).use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    private fun zipDirectory(sourceDir: File, zipFile: File) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
            sourceDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val relativePath = file.relativeTo(sourceDir).path.replace("\\", "/")
                    val zipEntry = ZipEntry(relativePath)
                    zos.putNextEntry(zipEntry)
                    BufferedInputStream(FileInputStream(file)).use { bis ->
                        bis.copyTo(zos)
                    }
                    zos.closeEntry()
                }
            }
        }
    }
}
