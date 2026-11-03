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
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class PostExtractorFacade {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val instagramExtractor = InstagramExtractor(client)
    private val facebookExtractor = FacebookExtractor(client)

    suspend fun extractFromText(text: String, cookieHeader: String? = null): Result<PostData> {
        val extractedUrl = findFirstUrl(text) ?: return Result.failure(
            IllegalArgumentException("未在輸入內容中找到有效的網址，請確認貼文連結")
        )
        return extractFromUrl(extractedUrl, cookieHeader)
    }

    suspend fun extractFromUrl(url: String, cookieHeader: String? = null): Result<PostData> {
        val platform = Platform.fromUrl(url)
        // Automatically inject saved browser session cookies if none explicitly supplied
        val effectiveCookie = cookieHeader ?: CookieHelper.getCookies(url)

        return when (platform) {
            Platform.INSTAGRAM -> instagramExtractor.extract(url, effectiveCookie)
            Platform.FACEBOOK -> facebookExtractor.extract(url, effectiveCookie)
            Platform.UNKNOWN -> extractGenericOpenGraph(url, effectiveCookie)
        }
    }

    private suspend fun extractGenericOpenGraph(url: String, cookieHeader: String?): Result<PostData> = withContext(Dispatchers.IO) {
        try {
            val reqBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")

            if (!cookieHeader.isNullOrBlank()) {
                reqBuilder.header("Cookie", cookieHeader)
            }

            val res = client.newCall(reqBuilder.build()).execute()
            if (!res.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP Error ${res.code}"))
            }

            val html = res.body?.string() ?: ""
            val doc = Jsoup.parse(html)
            val title = doc.select("meta[property=og:title]").attr("content").ifBlank { doc.title() }
            val desc = doc.select("meta[property=og:description]").attr("content")
            val img = doc.select("meta[property=og:image]").attr("content")
            val video = doc.select("meta[property=og:video]").attr("content")

            val mediaList = mutableListOf<PostMedia>()
            if (video.isNotBlank()) {
                mediaList.add(PostMedia(video, MediaType.VIDEO, img, "video_01.mp4"))
            } else if (img.isNotBlank()) {
                mediaList.add(PostMedia(img, MediaType.IMAGE, img, "image_01.jpg"))
            }

            val post = PostData(
                id = "post_" + System.currentTimeMillis(),
                platform = Platform.UNKNOWN,
                originalUrl = url,
                author = title.take(30),
                caption = desc.ifBlank { title },
                mediaList = mediaList
            )
            Result.success(post)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        fun findFirstUrl(text: String): String? {
            val urlPattern = Pattern.compile("https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+")
            val matcher = urlPattern.matcher(text)
            return if (matcher.find()) {
                matcher.group(0)
            } else null
        }
    }
}
