package com.postpacker.app.extractor

import com.postpacker.app.model.MediaType
import com.postpacker.app.model.Platform
import com.postpacker.app.model.PostData
import com.postpacker.app.model.PostMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

class FacebookExtractor(private val client: OkHttpClient) {

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    suspend fun extract(rawUrl: String, cookieHeader: String? = null): Result<PostData> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = rawUrl.trim()
            val requestBuilder = Request.Builder()
                .url(cleanUrl)
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", "zh-TW,zh;q=0.9,en-US;q=0.8,en;q=0.7")

            if (!cookieHeader.isNullOrBlank()) {
                requestBuilder.header("Cookie", cookieHeader)
            }

            val response = client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP Error: ${response.code}"))
            }

            val html = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response body"))
            val finalUrl = response.request.url.toString()
            val doc = Jsoup.parse(html)

            val ogTitle = doc.select("meta[property=og:title]").attr("content")
            val ogDesc = doc.select("meta[property=og:description]").attr("content")
            val ogImage = doc.select("meta[property=og:image]").attr("content")
            val ogVideo = doc.select("meta[property=og:video]").attr("content").ifBlank {
                doc.select("meta[property=og:video:url]").attr("content")
            }

            // Extract HD/SD video if present in script
            var directVideoUrl = ogVideo
            if (directVideoUrl.isBlank()) {
                val hdMatch = Pattern.compile("\"browser_native_hd_url\"\\s*:\\s*\"([^\"]+)\"").matcher(html)
                if (hdMatch.find()) {
                    directVideoUrl = hdMatch.group(1)?.replace("\\/", "/")?.replace("\\u0026", "&") ?: ""
                } else {
                    val sdMatch = Pattern.compile("\"browser_native_sd_url\"\\s*:\\s*\"([^\"]+)\"").matcher(html)
                    if (sdMatch.find()) {
                        directVideoUrl = sdMatch.group(1)?.replace("\\/", "/")?.replace("\\u0026", "&") ?: ""
                    }
                }
            }

            val mediaList = mutableListOf<PostMedia>()
            if (directVideoUrl.isNotBlank()) {
                mediaList.add(
                    PostMedia(
                        url = directVideoUrl,
                        type = MediaType.VIDEO,
                        thumbnailUrl = ogImage.ifBlank { null },
                        suggestedFileName = "fb_video_01.mp4"
                    )
                )
            } else if (ogImage.isNotBlank() && !ogImage.contains("static.xx.fbcdn.net/rsrc.php")) {
                mediaList.add(
                    PostMedia(
                        url = ogImage,
                        type = MediaType.IMAGE,
                        thumbnailUrl = ogImage,
                        suggestedFileName = "fb_image_01.jpg"
                    )
                )
            }

            // Also check for multiple image representations if available
            val photoMatches = extractPhotosFromHtml(html)
            if (photoMatches.isNotEmpty()) {
                mediaList.clear()
                mediaList.addAll(photoMatches)
            }

            if (mediaList.isEmpty() && ogDesc.isBlank() && ogTitle.isBlank()) {
                return@withContext Result.failure(
                    Exception("Facebook 貼文可能需要登入或為私人貼文。請使用「內嵌瀏覽器」功能輔助擷取。")
                )
            }

            // Determine author & caption
            val author = if (ogTitle.isNotBlank() && !ogTitle.contains("Facebook")) {
                ogTitle.substringBefore(" - ").substringBefore(" | ")
            } else {
                "Facebook User"
            }

            val caption = ogDesc.ifBlank { ogTitle }
            val postId = "fb_" + (System.currentTimeMillis() % 10000000)
            val currentDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

            val post = PostData(
                id = postId,
                platform = Platform.FACEBOOK,
                originalUrl = finalUrl,
                author = author,
                authorAvatarUrl = null,
                caption = caption,
                publishedAt = currentDate,
                mediaList = mediaList,
                hashtags = extractHashtags(caption)
            )

            Result.success(post)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractPhotosFromHtml(html: String): List<PostMedia> {
        val list = mutableListOf<PostMedia>()
        val seen = mutableSetOf<String>()
        val pattern = Pattern.compile("https://scontent[^\"]+\\.(?:jpg|jpeg|png)(?:[^\"]+)?")
        val matcher = pattern.matcher(html)
        var count = 1
        while (matcher.find()) {
            val url = matcher.group(0)?.replace("&amp;", "&") ?: continue
            // Exclude icons, avatars, small thumbnails
            if (!url.contains("p50x50") && !url.contains("s100x100") && !url.contains("cp0") && !seen.contains(url)) {
                seen.add(url)
                list.add(
                    PostMedia(
                        url = url,
                        type = MediaType.IMAGE,
                        thumbnailUrl = url,
                        suggestedFileName = "fb_photo_${String.format(Locale.US, "%02d", count++)}.jpg"
                    )
                )
                if (list.size >= 10) break
            }
        }
        return list
    }

    private fun extractHashtags(text: String): List<String> {
        val hashtags = mutableListOf<String>()
        val matcher = Pattern.compile("#(\\w+)").matcher(text)
        while (matcher.find()) {
            matcher.group(1)?.let { hashtags.add(it) }
        }
        return hashtags
    }
}
