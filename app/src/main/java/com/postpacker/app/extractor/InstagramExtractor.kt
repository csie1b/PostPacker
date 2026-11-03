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
import java.util.regex.Matcher
import java.util.regex.Pattern

class InstagramExtractor(private val client: OkHttpClient) {

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    suspend fun extract(rawUrl: String, cookieHeader: String? = null): Result<PostData> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = normalizeInstagramUrl(rawUrl)
            val postId = extractPostId(cleanUrl) ?: extractPostId(rawUrl) ?: "ig_${System.currentTimeMillis()}"

            var finalAuthor = "Instagram User"
            var finalCaption = ""
            var finalAvatar: String? = null
            var finalDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
            val finalMediaList = mutableListOf<PostMedia>()

            // 1. Fetch Instagram embed endpoint
            // Instagram embed pages render server-side JSON with complete edge_sidecar_to_children (all carousel photos & videos) without requiring cookies/login!
            val embedUrl = "https://www.instagram.com/p/$postId/embed/captioned/"
            val embedHtml = fetchHtml(embedUrl, null)

            if (!embedHtml.isNullOrBlank()) {
                val embedMedia = extractAllMediaFromHtml(embedHtml)
                if (embedMedia.isNotEmpty()) {
                    finalMediaList.addAll(embedMedia)
                }

                val (embedAuthor, embedCaption, embedAvatar) = extractMetadataFromEmbed(embedHtml)
                if (!embedAuthor.isNullOrBlank()) finalAuthor = embedAuthor
                if (!embedCaption.isNullOrBlank()) finalCaption = embedCaption
                if (!embedAvatar.isNullOrBlank()) finalAvatar = embedAvatar
            }

            // 2. Fetch direct post page to enrich metadata (e.g. published timestamp) or handle private posts
            val directHtml = fetchHtml(cleanUrl, cookieHeader)
            if (!directHtml.isNullOrBlank()) {
                val doc = Jsoup.parse(directHtml)

                // Try structured ld+json for publication date and fallback metadata
                val ldJsonData = extractLdJson(directHtml, cleanUrl, postId)
                if (ldJsonData != null) {
                    if (finalAuthor == "Instagram User" && ldJsonData.author.isNotBlank()) {
                        finalAuthor = ldJsonData.author
                    }
                    if (finalCaption.isBlank() && ldJsonData.caption.isNotBlank()) {
                        finalCaption = ldJsonData.caption
                    }
                    if (ldJsonData.publishedAt.isNotBlank()) {
                        finalDate = ldJsonData.publishedAt
                    }
                    if (finalMediaList.isEmpty() && ldJsonData.mediaList.isNotEmpty()) {
                        finalMediaList.addAll(ldJsonData.mediaList)
                    }
                }

                // Check OpenGraph metadata
                val ogTitle = doc.select("meta[property=og:title]").attr("content")
                val ogDesc = doc.select("meta[property=og:description]").attr("content")
                val ogImage = doc.select("meta[property=og:image]").attr("content")
                val ogVideo = doc.select("meta[property=og:video]").attr("content")

                if (finalAuthor == "Instagram User" && ogTitle.isNotBlank()) {
                    val authorMatch = Pattern.compile("^(.*?)(?:\\s*\\(@[\\w.]+\\))?\\s*on Instagram").matcher(ogTitle)
                    if (authorMatch.find()) {
                        finalAuthor = authorMatch.group(1)?.trim() ?: finalAuthor
                    }
                }

                if (finalCaption.isBlank() && ogDesc.isNotBlank()) {
                    var cap = ogDesc
                    if (cap.contains("on Instagram:")) {
                        cap = cap.substringAfter("on Instagram:").trim()
                        if (cap.startsWith("\"") && cap.endsWith("\"") && cap.length > 2) {
                            cap = cap.substring(1, cap.length - 1)
                        }
                    }
                    finalCaption = cap
                }

                // If direct HTML contains sidecar (e.g. logged-in user viewing private carousel)
                val directMedia = extractAllMediaFromHtml(directHtml)
                if (directMedia.size > finalMediaList.size) {
                    finalMediaList.clear()
                    finalMediaList.addAll(directMedia)
                } else if (finalMediaList.isEmpty()) {
                    if (ogVideo.isNotBlank()) {
                        finalMediaList.add(
                            PostMedia(
                                url = ogVideo,
                                type = MediaType.VIDEO,
                                thumbnailUrl = ogImage.ifBlank { null },
                                suggestedFileName = "ig_video_01.mp4"
                            )
                        )
                    } else if (ogImage.isNotBlank()) {
                        finalMediaList.add(
                            PostMedia(
                                url = ogImage,
                                type = MediaType.IMAGE,
                                thumbnailUrl = ogImage,
                                suggestedFileName = "ig_image_01.jpg"
                            )
                        )
                    }
                }
            }

            if (finalMediaList.isEmpty()) {
                return@withContext Result.failure(
                    Exception("Instagram 貼文受隱私權限保護或需要登入驗證。請點擊「網頁瀏覽器輔助擷取」登入後重試。")
                )
            }

            val hashtags = extractHashtags(finalCaption)

            val post = PostData(
                id = postId,
                platform = Platform.INSTAGRAM,
                originalUrl = cleanUrl,
                author = finalAuthor,
                authorAvatarUrl = finalAvatar,
                caption = finalCaption,
                publishedAt = finalDate,
                mediaList = finalMediaList,
                hashtags = hashtags
            )

            Result.success(post)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchHtml(url: String, cookieHeader: String?): String? {
        return try {
            val requestBuilder = Request.Builder()
                .url(url)
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
            if (response.isSuccessful) {
                response.body?.string()
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractAllMediaFromHtml(html: String): List<PostMedia> {
        val mediaList = mutableListOf<PostMedia>()
        val seenUrls = mutableSetOf<String>()

        // 1. Try finding edge_sidecar_to_children (Multi-image Carousel)
        val sidecarIdx = html.indexOf("edge_sidecar_to_children")
        if (sidecarIdx != -1) {
            val chunk = html.substring(sidecarIdx, minOf(html.length, sidecarIdx + 300000))
            val nodePattern = Pattern.compile("\\\\?[\"']node\\\\?[\"']\\s*:\\s*\\{")
            val nodeChunks = nodePattern.split(chunk)
            var itemIdx = 1

            for (i in 1 until nodeChunks.size) {
                val part = nodeChunks[i]
                val isVideo = Pattern.compile("\\\\?[\"']is_video\\\\?[\"']\\s*:\\s*true").matcher(part).find()

                val displayMatcher = Pattern.compile("\\\\?[\"']display_url\\\\?[\"']\\s*:\\s*\\\\?[\"']((?:\\\\\\\\.|[^\"'\\\\])+)\\\\?[\"']").matcher(part)
                val displayUrl = if (displayMatcher.find()) {
                    cleanJsonUrl(displayMatcher.group(1) ?: "")
                } else null

                val videoMatcher = Pattern.compile("\\\\?[\"']video_url\\\\?[\"']\\s*:\\s*\\\\?[\"']((?:\\\\\\\\.|[^\"'\\\\])+)\\\\?[\"']").matcher(part)
                val videoUrl = if (isVideo && videoMatcher.find()) {
                    cleanJsonUrl(videoMatcher.group(1) ?: "")
                } else null

                val finalUrl = if (isVideo && !videoUrl.isNullOrBlank()) videoUrl else displayUrl
                if (!finalUrl.isNullOrBlank() && !seenUrls.contains(finalUrl)) {
                    seenUrls.add(finalUrl)
                    val type = if (isVideo && !videoUrl.isNullOrBlank()) MediaType.VIDEO else MediaType.IMAGE
                    val ext = if (type == MediaType.VIDEO) "mp4" else "jpg"
                    mediaList.add(
                        PostMedia(
                            url = finalUrl,
                            type = type,
                            thumbnailUrl = displayUrl,
                            suggestedFileName = "ig_${if (type == MediaType.VIDEO) "video" else "image"}_${String.format(Locale.US, "%02d", itemIdx++)}.$ext"
                        )
                    )
                }
            }

            if (mediaList.isNotEmpty()) {
                return mediaList
            }
        }

        // 2. Single post extraction (Reel or single image)
        val isVideo = Pattern.compile("\\\\?[\"']is_video\\\\?[\"']\\s*:\\s*true").matcher(html).find()
        val videoMatcher = Pattern.compile("\\\\?[\"']video_url\\\\?[\"']\\s*:\\s*\\\\?[\"']((?:\\\\\\\\.|[^\"'\\\\])+)\\\\?[\"']").matcher(html)
        val displayMatcher = Pattern.compile("\\\\?[\"']display_url\\\\?[\"']\\s*:\\s*\\\\?[\"']((?:\\\\\\\\.|[^\"'\\\\])+)\\\\?[\"']").matcher(html)

        if (isVideo && videoMatcher.find()) {
            val videoUrl = cleanJsonUrl(videoMatcher.group(1) ?: "")
            val displayUrl = if (displayMatcher.find()) cleanJsonUrl(displayMatcher.group(1) ?: "") else null
            if (videoUrl.isNotBlank()) {
                mediaList.add(
                    PostMedia(
                        url = videoUrl,
                        type = MediaType.VIDEO,
                        thumbnailUrl = displayUrl,
                        suggestedFileName = "ig_video_01.mp4"
                    )
                )
                return mediaList
            }
        } else if (displayMatcher.find()) {
            val displayUrl = cleanJsonUrl(displayMatcher.group(1) ?: "")
            if (displayUrl.isNotBlank()) {
                mediaList.add(
                    PostMedia(
                        url = displayUrl,
                        type = MediaType.IMAGE,
                        thumbnailUrl = displayUrl,
                        suggestedFileName = "ig_image_01.jpg"
                    )
                )
                return mediaList
            }
        }

        return mediaList
    }

    private fun extractMetadataFromEmbed(html: String): Triple<String?, String?, String?> {
        var author: String? = null
        var caption: String? = null
        var avatarUrl: String? = null

        try {
            val doc = Jsoup.parse(html)
            val usernameEl = doc.selectFirst(".UsernameText") ?: doc.selectFirst(".Username")
            if (usernameEl != null && usernameEl.text().isNotBlank()) {
                author = usernameEl.text().trim()
            }

            val avatarEl = doc.selectFirst(".Avatar img")
            if (avatarEl != null && avatarEl.hasAttr("src")) {
                avatarUrl = avatarEl.attr("src")
            }

            // Caption from JSON edge_media_to_caption
            val capMatcher = Pattern.compile(
                "\\\\?[\"']edge_media_to_caption\\\\?[\"']\\s*:\\s*\\{\\s*\\\\?[\"']edges\\\\?[\"']\\s*:\\s*\\[\\s*\\{\\s*\\\\?[\"']node\\\\?[\"']\\s*:\\s*\\{\\s*\\\\?[\"']text\\\\?[\"']\\s*:\\s*\\\\?[\"']((?:\\\\\\\\.|[^\"'\\\\])+)\\\\?[\"']"
            ).matcher(html)
            if (capMatcher.find()) {
                val rawCap = capMatcher.group(1) ?: ""
                caption = unescapeJsonString(rawCap)
            } else {
                val captionEl = doc.selectFirst(".Caption") ?: doc.selectFirst(".CaptionComments")
                if (captionEl != null && captionEl.text().isNotBlank()) {
                    caption = captionEl.text().trim()
                }
            }
        } catch (_: Exception) {}

        return Triple(author, caption, avatarUrl)
    }

    private fun cleanJsonUrl(raw: String): String {
        return raw.replace("\\\\\\/", "/")
            .replace("\\/", "/")
            .replace("\\u0026", "&")
            .replace("&amp;", "&")
            .replace("\\u00253D", "%3D")
    }

    private fun unescapeJsonString(raw: String): String {
        val replaced = raw.replace("\\\\n", "\n")
            .replace("\\n", "\n")
            .replace("\\\\r", "\r")
            .replace("\\r", "\r")
            .replace("\\\\t", "\t")
            .replace("\\t", "\t")
            .replace("\\\\\"", "\"")
            .replace("\\\"", "\"")
            .replace("\\\\\\/", "/")
            .replace("\\/", "/")

        // Replace unicode escapes \uXXXX
        val unicodePattern = Pattern.compile("\\\\u([0-9a-fA-F]{4})")
        val matcher = unicodePattern.matcher(replaced)
        val sb = StringBuffer()
        while (matcher.find()) {
            val hex = matcher.group(1) ?: "0000"
            val charCode = hex.toInt(16)
            matcher.appendReplacement(sb, Matcher.quoteReplacement(charCode.toChar().toString()))
        }
        matcher.appendTail(sb)
        return sb.toString()
    }

    private fun normalizeInstagramUrl(url: String): String {
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
        } catch (_: Exception) {}
        return null
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
