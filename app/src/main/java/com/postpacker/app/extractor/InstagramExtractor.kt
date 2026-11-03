package com.postpacker.app.extractor

import com.google.gson.JsonParser
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

class InstagramExtractor(private val client: OkHttpClient) {

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    suspend fun extract(rawUrl: String, cookieHeader: String? = null): Result<PostData> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = normalizeInstagramUrl(rawUrl)
            val postId = extractPostId(cleanUrl) ?: "ig_${System.currentTimeMillis()}"

            val requestBuilder = Request.Builder()
                .url(cleanUrl)
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", "zh-TW,zh;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "none")

            if (!cookieHeader.isNullOrBlank()) {
                requestBuilder.header("Cookie", cookieHeader)
            }

            val response = client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP Error: ${response.code}"))
            }

            val html = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response body"))
            val doc = Jsoup.parse(html)

            // 1. Try extracting structured ld+json
            val ldJsonData = extractLdJson(html, cleanUrl, postId)
            if (ldJsonData != null && ldJsonData.mediaList.isNotEmpty()) {
                return@withContext Result.success(ldJsonData)
            }

            // 2. Try extracting OpenGraph metadata
            val ogTitle = doc.select("meta[property=og:title]").attr("content")
            val ogDesc = doc.select("meta[property=og:description]").attr("content")
            val ogImage = doc.select("meta[property=og:image]").attr("content")
            val ogVideo = doc.select("meta[property=og:video]").attr("content")

            // Parse Author and Caption from og:title or og:description
            // Formats: "Author (@username) on Instagram: 'Caption text...'" or "Author on Instagram: ..."
            var author = "Instagram User"
            var caption = ogDesc

            if (ogTitle.isNotBlank()) {
                val authorMatch = Pattern.compile("^(.*?)(?:\\s*\\(@[\\w.]+\\))?\\s*on Instagram").matcher(ogTitle)
                if (authorMatch.find()) {
                    author = authorMatch.group(1)?.trim() ?: author
                }
            }

            if (caption.contains("on Instagram:")) {
                caption = caption.substringAfter("on Instagram:").trim()
                if (caption.startsWith("\"") && caption.endsWith("\"") && caption.length > 2) {
                    caption = caption.substring(1, caption.length - 1)
                }
            }

            val mediaList = mutableListOf<PostMedia>()

            if (ogVideo.isNotBlank()) {
                mediaList.add(
                    PostMedia(
                        url = ogVideo,
                        type = MediaType.VIDEO,
                        thumbnailUrl = ogImage.ifBlank { null },
                        suggestedFileName = "ig_video_01.mp4"
                    )
                )
            } else if (ogImage.isNotBlank()) {
                mediaList.add(
                    PostMedia(
                        url = ogImage,
                        type = MediaType.IMAGE,
                        thumbnailUrl = ogImage,
                        suggestedFileName = "ig_image_01.jpg"
                    )
                )
            }

            // 3. Scan script tags for carousel items
            val scriptMedia = extractMediaFromEmbeddedScripts(html)
            if (scriptMedia.isNotEmpty()) {
                mediaList.clear()
                mediaList.addAll(scriptMedia)
            }

            if (mediaList.isEmpty()) {
                return@withContext Result.failure(
                    Exception("Instagram 貼文受隱私權限保護或需要登入驗證。請使用「內嵌瀏覽器」功能登入後擷取。")
                )
            }

            // Extract hashtags from caption
            val hashtags = extractHashtags(caption)
            val currentDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

            val post = PostData(
                id = postId,
                platform = Platform.INSTAGRAM,
                originalUrl = cleanUrl,
                author = author,
                authorAvatarUrl = null,
                caption = caption,
                publishedAt = currentDate,
                mediaList = mediaList,
                hashtags = hashtags
            )

            Result.success(post)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun normalizeInstagramUrl(url: String): String {
        // Strip query params like ?utm_source=...
        val trimmed = url.trim()
        val matcher = Pattern.compile("https?://(?:www\\.)?instagram\\.com/(?:p|reel|reels|tv)/([a-zA-Z0-9_-]+)").matcher(trimmed)
        return if (matcher.find()) {
            val code = matcher.group(1)
            val type = if (trimmed.contains("/reel/")) "reel" else "p"
            "https://www.instagram.com/$type/$code/"
        } else {
            trimmed.substringBefore("?")
        }
    }

    private fun extractPostId(url: String): String? {
        val matcher = Pattern.compile("/(?:p|reel|reels|tv)/([a-zA-Z0-9_-]+)").matcher(url)
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun extractLdJson(html: String, postUrl: String, postId: String): PostData? {
        try {
            val doc = Jsoup.parse(html)
            val scripts = doc.select("script[type=application/ld+json]")
            for (script in scripts) {
                val json = script.data()
                if (json.contains("\"headline\"") || json.contains("\"articleBody\"") || json.contains("\"image\"") || json.contains("\"video\"")) {
                    val root = JsonParser.parseString(json).asJsonObject
                    val authorObj = root.getAsJsonObject("author")
                    val authorName = authorObj?.get("name")?.asString ?: "Instagram User"
                    val caption = when {
                        root.has("articleBody") -> root.get("articleBody").asString
                        root.has("headline") -> root.get("headline").asString
                        root.has("description") -> root.get("description").asString
                        else -> ""
                    }
                    val datePublished = root.get("datePublished")?.asString ?: ""

                    val mediaList = mutableListOf<PostMedia>()
                    if (root.has("video")) {
                        val videoObj = root.getAsJsonObject("video")
                        val videoUrl = videoObj.get("contentUrl")?.asString
                        val thumbUrl = videoObj.get("thumbnailUrl")?.asString
                        if (!videoUrl.isNullOrBlank()) {
                            mediaList.add(
                                PostMedia(
                                    url = videoUrl,
                                    type = MediaType.VIDEO,
                                    thumbnailUrl = thumbUrl,
                                    suggestedFileName = "ig_video_01.mp4"
                                )
                            )
                        }
                    }

                    if (root.has("image")) {
                        val imgElement = root.get("image")
                        if (imgElement.isJsonArray) {
                            imgElement.asJsonArray.forEachIndexed { idx, item ->
                                val imgUrl = if (item.isJsonObject) item.asJsonObject.get("url")?.asString else item.asString
                                if (!imgUrl.isNullOrBlank()) {
                                    mediaList.add(
                                        PostMedia(
                                            url = imgUrl,
                                            type = MediaType.IMAGE,
                                            thumbnailUrl = imgUrl,
                                            suggestedFileName = "ig_image_${String.format(Locale.US, "%02d", idx + 1)}.jpg"
                                        )
                                    )
                                }
                            }
                        } else if (imgElement.isJsonObject) {
                            val imgUrl = imgElement.asJsonObject.get("url")?.asString
                            if (!imgUrl.isNullOrBlank()) {
                                mediaList.add(PostMedia(imgUrl, MediaType.IMAGE, imgUrl, "ig_image_01.jpg"))
                            }
                        } else if (imgElement.isJsonPrimitive) {
                            val imgUrl = imgElement.asString
                            if (imgUrl.isNotBlank()) {
                                mediaList.add(PostMedia(imgUrl, MediaType.IMAGE, imgUrl, "ig_image_01.jpg"))
                            }
                        }
                    }

                    if (mediaList.isNotEmpty()) {
                        return PostData(
                            id = postId,
                            platform = Platform.INSTAGRAM,
                            originalUrl = postUrl,
                            author = authorName,
                            caption = caption,
                            publishedAt = datePublished,
                            mediaList = mediaList,
                            hashtags = extractHashtags(caption)
                        )
                    }
                }
            }
        } catch (_: Exception) {
        }
        return null
    }

    private fun extractMediaFromEmbeddedScripts(html: String): List<PostMedia> {
        val result = mutableListOf<PostMedia>()
        try {
            // Regex match for display_url in edge_sidecar_to_children or similar structures
            val displayUrlPattern = Pattern.compile("\"display_url\"\\s*:\\s*\"([^\"]+)\"")
            val matcher = displayUrlPattern.matcher(html)
            val seenUrls = mutableSetOf<String>()
            var idx = 1
            while (matcher.find()) {
                val rawUrl = matcher.group(1)?.replace("\\u0026", "&") ?: continue
                if (!seenUrls.contains(rawUrl)) {
                    seenUrls.add(rawUrl)
                    result.add(
                        PostMedia(
                            url = rawUrl,
                            type = MediaType.IMAGE,
                            thumbnailUrl = rawUrl,
                            suggestedFileName = "ig_image_${String.format(Locale.US, "%02d", idx++)}.jpg"
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        return result
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
