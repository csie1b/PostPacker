package com.postpacker.app.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.postpacker.app.model.Platform
import com.postpacker.app.ui.components.InAppBrowserDialog
import com.postpacker.app.ui.components.PostPreviewCard
import com.postpacker.app.ui.theme.FacebookBlue
import com.postpacker.app.ui.theme.InstagramPurple
import com.postpacker.app.viewmodel.MainViewModel

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val urlInput by viewModel.urlInput.collectAsState()
    val isExtracting by viewModel.isExtracting.collectAsState()
    val extractionError by viewModel.extractionError.collectAsState()
    val currentPost by viewModel.currentPost.collectAsState()
    val isPackaging by viewModel.isPackaging.collectAsState()
    val packageProgressText by viewModel.packageProgressText.collectAsState()
    val lastZipFile by viewModel.lastPackagedZip.collectAsState()
    val showBrowserDialog by viewModel.showBrowserDialog.collectAsState()
    val browserUrl by viewModel.browserUrl.collectAsState()
    val isIgLoggedIn by viewModel.isIgLoggedIn.collectAsState()
    val isFbLoggedIn by viewModel.isFbLoggedIn.collectAsState()

    val scrollState = rememberScrollState()

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            // App Title Banner
            Text(
                text = "PostPacker",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Instagram & Facebook 貼文多媒體一鍵打包助手",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Prominent Account Login & Cookie Status Card
            AccountLoginCard(
                isIgLoggedIn = isIgLoggedIn,
                isFbLoggedIn = isFbLoggedIn,
                onLoginIg = { viewModel.openLoginFor(Platform.INSTAGRAM) },
                onLoginFb = { viewModel.openLoginFor(Platform.FACEBOOK) },
                onClearCookies = { viewModel.clearCookies() }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // URL Input Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { viewModel.onUrlChanged(it) },
                        label = { Text("貼文連結 (IG / FB)") },
                        placeholder = { Text("https://www.instagram.com/p/...") },
                        trailingIcon = {
                            if (urlInput.isNotBlank()) {
                                IconButton(onClick = { viewModel.onUrlChanged("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Paste from Clipboard
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                                if (clipText.isNotBlank()) {
                                    viewModel.handleIncomingShare(clipText)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("貼上剪貼簿", fontSize = 12.sp)
                        }

                        // Extract Button
                        Button(
                            onClick = { viewModel.extractPost() },
                            enabled = urlInput.isNotBlank() && !isExtracting,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isExtracting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("解析貼文", fontSize = 12.sp)
                            }
                        }
                    }

                    // In-App Browser Helper link
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.openBrowserHelper() },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("網頁瀏覽器輔助擷取", fontSize = 11.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Error Display
            AnimatedVisibility(visible = extractionError != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "解析失敗",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = extractionError ?: "",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // Post Preview Result
            currentPost?.let { post ->
                PostPreviewCard(
                    post = post,
                    lastZipFile = lastZipFile,
                    onPackageZip = { viewModel.packageToZip(context) },
                    onSaveToGallery = { viewModel.saveToGallery(context) },
                    onCopyCaption = { viewModel.copyCaption(context) },
                    onShareZip = { file -> viewModel.shareZipFile(context, file) }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Usage Guide Card
            if (currentPost == null) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Info",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "如何使用 PostPacker？",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "1. 📱 系統分享一鍵匯入：在 Instagram 或 Facebook App 中看到喜歡的貼文，點擊「分享至...」並選取「PostPacker」，App 會自動開啟並載入！\n\n" +
                                   "2. 🍪 關於登入 Cookies：公開貼文無需登入即可直接打包。若遇到私人貼文、輪播多圖或 Meta 登入牆，只需點擊上方的「登入 IG/FB」按鈕登入一次，後續所有背景請求皆會自動附帶您的登入憑證！\n\n" +
                                   "3. 📦 一鍵打包 ZIP：自動下載所有輪播高畫質圖片、影片，並產生含 Markdown、純文字文案及 JSON 檔案的壓縮包，隨時能備份或傳送到雲端。\n\n" +
                                   "4. 🖼️ 存入相簿：直接將所有圖片及影片保存至手機內建相簿。\n\n" +
                                   "5. 🌐 網頁輔助擷取：若遇到強防爬動態加密貼文，可開啟「網頁瀏覽器輔助擷取」直接從網頁 DOM 提取已解密的圖片影音。",
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Packaging Progress Dialog
        if (isPackaging) {
            Dialog(onDismissRequest = { /* Prevent dismiss while processing */ }) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "正在處理貼文...",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = packageProgressText,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // In-App Browser Helper Dialog
        if (showBrowserDialog) {
            InAppBrowserDialog(
                initialUrl = browserUrl,
                onDismiss = { viewModel.closeBrowserHelper() },
                onExtracted = { post -> viewModel.onBrowserExtracted(post) }
            )
        }
    }
}

@Composable
fun AccountLoginCard(
    isIgLoggedIn: Boolean,
    isFbLoggedIn: Boolean,
    onLoginIg: () -> Unit,
    onLoginFb: () -> Unit,
    onClearCookies: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Cookie,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "社群帳號授權登入 (Cookies)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "登入後可完整下載多圖輪播、Reels 影片與私密貼文",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Two distinct, prominent platform cards side by side
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Instagram Card
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isIgLoggedIn)
                            Color(0xFF2E7D32).copy(alpha = 0.12f)
                        else
                            MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isIgLoggedIn) Color(0xFF4CAF50).copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "📸 Instagram",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        // Status badge
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isIgLoggedIn) Color(0xFF2E7D32) else Color(0xFFFF9800).copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = if (isIgLoggedIn) "✅ 已具備憑證" else "⚠️ 未登入",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isIgLoggedIn) Color.White else Color(0xFFFF9800),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Large prominent login button
                        Button(
                            onClick = onLoginIg,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isIgLoggedIn) MaterialTheme.colorScheme.secondary else InstagramPurple
                            )
                        ) {
                            Text(
                                text = if (isIgLoggedIn) "重新驗證" else "登入 IG",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Facebook Card
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isFbLoggedIn)
                            Color(0xFF2E7D32).copy(alpha = 0.12f)
                        else
                            MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isFbLoggedIn) Color(0xFF4CAF50).copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "📘 Facebook",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        // Status badge
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isFbLoggedIn) Color(0xFF2E7D32) else Color(0xFFFF9800).copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = if (isFbLoggedIn) "✅ 已具備憑證" else "⚠️ 未登入",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isFbLoggedIn) Color.White else Color(0xFFFF9800),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Large prominent login button
                        Button(
                            onClick = onLoginFb,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isFbLoggedIn) MaterialTheme.colorScheme.secondary else FacebookBlue
                            )
                        ) {
                            Text(
                                text = if (isFbLoggedIn) "重新驗證" else "登入 FB",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            if (isIgLoggedIn || isFbLoggedIn) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onClearCookies,
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("🧹 清除所有 Cookies (登出)", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
