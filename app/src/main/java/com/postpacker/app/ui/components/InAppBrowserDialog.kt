package com.postpacker.app.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.gson.Gson
import com.postpacker.app.model.MediaType
import com.postpacker.app.model.Platform
import com.postpacker.app.model.PostData
import com.postpacker.app.model.PostMedia
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InAppBrowserDialog(
    initialUrl: String,
    onDismiss: () -> Unit,
    onExtracted: (PostData) -> Unit
) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var pageTitle by remember { mutableStateOf("載入中...") }
    var loadProgress by remember { mutableFloatStateOf(0f) }
    var currentUrl by remember { mutableStateOf(initialUrl) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top App Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = pageTitle,
                            maxLines = 1,
                            fontSize = 14.sp,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = currentUrl,
                            maxLines = 1,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = { webViewInstance?.reload() }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reload")
                    }

                    // Extract button
                    Button(
                        onClick = {
                            injectExtractionScript(webViewInstance) { post ->
                                onExtracted(post)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(imageVector = Icons.Default.FileDownload, contentDescription = "Extract")
                        Text("擷取貼文", fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                    }
                }

                if (loadProgress in 0.01f..0.99f) {
                    LinearProgressIndicator(
                        progress = { loadProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                    )
                }

                // Helpful tip banner for login pages
                if (currentUrl.contains("instagram.com/accounts/login")) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "💡 提示：若您的 IG 與 FB 有綁定，可點擊頁面上的「以 Facebook 帳號登入」；若未綁定，請輸入 IG 帳密登入一次，App 就會永久記住憑證！",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                // WebView
                Box(modifier = Modifier.weight(1f)) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    databaseEnabled = true
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    userAgentString = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
                                }
                                CookieManager.getInstance().setAcceptCookie(true)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        loadProgress = newProgress / 100f
                                    }

                                    override fun onReceivedTitle(view: WebView?, title: String?) {
                                        pageTitle = title ?: ""
                                    }
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        url?.let { currentUrl = it }
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        url?.let { currentUrl = it }
                                    }
                                }

                                addJavascriptInterface(
                                    JsExtractorBridge { postData ->
                                        onExtracted(postData)
                                    },
                                    "AndroidBridge"
                                )

                                loadUrl(initialUrl)
                                webViewInstance = this
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

private class JsExtractorBridge(private val onResult: (PostData) -> Unit) {
    @JavascriptInterface
    fun onPostExtracted(jsonString: String) {
        try {
            val gson = Gson()
            val raw = gson.fromJson(jsonString, RawExtractedData::class.java)

            val mediaList = raw.mediaUrls.mapIndexed { idx, url ->
                val isVideo = raw.mediaTypes.getOrNull(idx) == "video"
                PostMedia(
                    url = url,
                    type = if (isVideo) MediaType.VIDEO else MediaType.IMAGE,
                    thumbnailUrl = url,
                    suggestedFileName = if (isVideo) "video_${idx + 1}.mp4" else "image_${idx + 1}.jpg"
                )
            }

            val post = PostData(
                id = "inapp_" + System.currentTimeMillis(),
                platform = Platform.fromUrl(raw.url),
                originalUrl = raw.url,
                author = raw.author.ifBlank { "User" },
                caption = raw.caption,
                publishedAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()),
                mediaList = mediaList
            )
            onResult(post)
        } catch (_: Exception) {}
    }

    private data class RawExtractedData(
        val url: String = "",
        val author: String = "",
        val caption: String = "",
        val mediaUrls: List<String> = emptyList(),
        val mediaTypes: List<String> = emptyList()
    )
}

private fun injectExtractionScript(webView: WebView?, onExtracted: (PostData) -> Unit) {
    val js = """
        (function() {
            var mediaUrls = [];
            var mediaTypes = [];
            
            // 1. Scan videos
            var videos = document.querySelectorAll('video');
            videos.forEach(function(v) {
                var src = v.currentSrc || v.src;
                if (!src) {
                    var s = v.querySelector('source');
                    if (s) src = s.src;
                }
                if (src && !mediaUrls.includes(src)) {
                    mediaUrls.push(src);
                    mediaTypes.push('video');
                }
            });

            // 2. Scan images
            var imgs = document.querySelectorAll('article img, div[role="presentation"] img, img[srcset]');
            imgs.forEach(function(img) {
                var src = img.src;
                // Parse srcset to get highest resolution
                if (img.srcset) {
                    var parts = img.srcset.split(',');
                    var best = parts[parts.length - 1].trim().split(' ')[0];
                    if (best) src = best;
                }
                if (src && !src.includes('profile') && !src.includes('avatar') && !mediaUrls.includes(src)) {
                    mediaUrls.push(src);
                    mediaTypes.push('image');
                }
            });

            // 3. Caption & Author
            var author = "";
            var caption = "";
            var title = document.title;
            
            // Try extracting from Instagram article structure
            var article = document.querySelector('article');
            if (article) {
                var h2 = article.querySelector('h2');
                if (h2) author = h2.innerText;
                var captionSpan = article.querySelector('h1, div span[dir="auto"]');
                if (captionSpan) caption = captionSpan.innerText;
            }

            if (!caption) {
                var ogDesc = document.querySelector('meta[property="og:description"]');
                if (ogDesc) caption = ogDesc.getAttribute('content') || "";
            }

            var payload = {
                url: window.location.href,
                author: author || title,
                caption: caption,
                mediaUrls: mediaUrls,
                mediaTypes: mediaTypes
            };

            if (window.AndroidBridge) {
                window.AndroidBridge.onPostExtracted(JSON.stringify(payload));
            }
        })();
    """.trimIndent()

    webView?.evaluateJavascript(js, null)
}
