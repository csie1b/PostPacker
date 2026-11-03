package com.postpacker.app.viewmodel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.postpacker.app.extractor.CookieHelper
import com.postpacker.app.extractor.PostExtractorFacade
import com.postpacker.app.model.PackagedArchive
import com.postpacker.app.model.Platform
import com.postpacker.app.model.PostData
import com.postpacker.app.packager.MediaGallerySaver
import com.postpacker.app.packager.ZipPackager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel : ViewModel() {

    private val extractor = PostExtractorFacade()
    private val gson = Gson()

    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    private val _isExtracting = MutableStateFlow(false)
    val isExtracting: StateFlow<Boolean> = _isExtracting.asStateFlow()

    private val _extractionError = MutableStateFlow<String?>(null)
    val extractionError: StateFlow<String?> = _extractionError.asStateFlow()

    private val _currentPost = MutableStateFlow<PostData?>(null)
    val currentPost: StateFlow<PostData?> = _currentPost.asStateFlow()

    private val _isPackaging = MutableStateFlow(false)
    val isPackaging: StateFlow<Boolean> = _isPackaging.asStateFlow()

    private val _packageProgressText = MutableStateFlow("")
    val packageProgressText: StateFlow<String> = _packageProgressText.asStateFlow()

    private val _lastPackagedZip = MutableStateFlow<File?>(null)
    val lastPackagedZip: StateFlow<File?> = _lastPackagedZip.asStateFlow()

    private val _historyList = MutableStateFlow<List<PackagedArchive>>(emptyList())
    val historyList: StateFlow<List<PackagedArchive>> = _historyList.asStateFlow()

    private val _showBrowserDialog = MutableStateFlow(false)
    val showBrowserDialog: StateFlow<Boolean> = _showBrowserDialog.asStateFlow()

    private val _browserUrl = MutableStateFlow("")
    val browserUrl: StateFlow<String> = _browserUrl.asStateFlow()

    private val _isIgLoggedIn = MutableStateFlow(false)
    val isIgLoggedIn: StateFlow<Boolean> = _isIgLoggedIn.asStateFlow()

    private val _isFbLoggedIn = MutableStateFlow(false)
    val isFbLoggedIn: StateFlow<Boolean> = _isFbLoggedIn.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    fun onUrlChanged(newUrl: String) {
        _urlInput.value = newUrl
        if (_extractionError.value != null) {
            _extractionError.value = null
        }
    }

    fun refreshLoginStatus() {
        _isIgLoggedIn.value = CookieHelper.isInstagramLoggedIn()
        _isFbLoggedIn.value = CookieHelper.isFacebookLoggedIn()
    }

    fun clearCookies() {
        CookieHelper.clearAllCookies {
            refreshLoginStatus()
            viewModelScope.launch {
                _toastEvent.emit("已清除所有瀏覽器 Cookie 與登入紀錄")
            }
        }
    }

    fun openLoginFor(platform: Platform) {
        when (platform) {
            Platform.INSTAGRAM -> {
                _browserUrl.value = "https://www.instagram.com/accounts/login/"
                _showBrowserDialog.value = true
            }
            Platform.FACEBOOK -> {
                _browserUrl.value = "https://m.facebook.com/login/"
                _showBrowserDialog.value = true
            }
            Platform.UNKNOWN -> openBrowserHelper()
        }
    }

    fun handleIncomingShare(sharedText: String) {
        val foundUrl = PostExtractorFacade.findFirstUrl(sharedText)
        if (foundUrl != null) {
            _urlInput.value = foundUrl
            extractPost(foundUrl)
        } else {
            _urlInput.value = sharedText
        }
    }

    fun extractPost(urlToExtract: String? = null, cookie: String? = null) {
        val target = (urlToExtract ?: _urlInput.value).trim()
        if (target.isBlank()) {
            _extractionError.value = "請先貼上貼文網址"
            return
        }

        viewModelScope.launch {
            _isExtracting.value = true
            _extractionError.value = null
            _currentPost.value = null

            val result = extractor.extractFromText(target, cookie)
            result.onSuccess { post ->
                _currentPost.value = post
                _isExtracting.value = false
            }.onFailure { err ->
                _extractionError.value = err.message ?: "解析貼文失敗，請檢查網址或使用內嵌瀏覽器輔助"
                _isExtracting.value = false
            }
        }
    }

    fun packageToZip(context: Context) {
        val post = _currentPost.value ?: return
        viewModelScope.launch {
            _isPackaging.value = true
            _packageProgressText.value = "開始準備打包..."

            val packager = ZipPackager(context.applicationContext)
            val result = packager.packagePost(post) { current, total, stepName ->
                _packageProgressText.value = "[$current/$total] $stepName"
            }

            result.onSuccess { zipFile ->
                _lastPackagedZip.value = zipFile
                _isPackaging.value = false
                _toastEvent.emit("🎉 貼文打包成功！已儲存至「下載 (Download/PostPacker)」資料夾")

                // Add to history
                val archive = PackagedArchive(
                    id = post.id,
                    platform = post.platform,
                    author = post.author,
                    captionPreview = post.caption.take(100),
                    zipFile = zipFile,
                    mediaCount = post.mediaList.size
                )
                val updated = listOf(archive) + _historyList.value.filter { it.id != post.id }
                _historyList.value = updated
                saveHistoryToPrefs(context, updated)
            }.onFailure { err ->
                _isPackaging.value = false
                _toastEvent.emit("❌ 打包失敗: ${err.message}")
            }
        }
    }

    fun saveToGallery(context: Context) {
        val post = _currentPost.value ?: return
        if (post.mediaList.isEmpty()) {
            viewModelScope.launch { _toastEvent.emit("此貼文沒有可儲存的媒體檔案") }
            return
        }

        viewModelScope.launch {
            _isPackaging.value = true
            _packageProgressText.value = "正在下載並存入相簿..."

            val saver = MediaGallerySaver(context.applicationContext)
            val result = saver.savePostMediaToGallery(post) { current, total, name ->
                _packageProgressText.value = name
            }

            _isPackaging.value = false
            result.onSuccess { count ->
                _toastEvent.emit("🎉 已成功儲存 $count 個媒體至手機相簿 (Pictures/PostPacker)")
            }.onFailure { err ->
                _toastEvent.emit("❌ 儲存相簿失敗: ${err.message}")
            }
        }
    }

    fun copyCaption(context: Context) {
        val caption = _currentPost.value?.caption
        if (!caption.isNullOrBlank()) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Post Caption", caption)
            clipboard.setPrimaryClip(clip)
            viewModelScope.launch {
                _toastEvent.emit("📋 文案已複製到剪貼簿")
            }
        } else {
            viewModelScope.launch {
                _toastEvent.emit("貼文無文字內容可複製")
            }
        }
    }

    fun shareZipFile(context: Context, zipFile: File) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val uri: Uri = FileProvider.getUriForFile(context, authority, zipFile)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "社群貼文打包檔案: ${zipFile.name}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "分享貼文壓縮包"))
        } catch (e: Exception) {
            viewModelScope.launch {
                _toastEvent.emit("分享失敗: ${e.message}")
            }
        }
    }

    fun openZipFile(context: Context, zipFile: File) {
        if (!zipFile.exists()) {
            viewModelScope.launch {
                _toastEvent.emit("⚠️ 找不到檔案，可能已被移動或刪除")
            }
            return
        }
        try {
            val authority = "${context.packageName}.fileprovider"
            val uri: Uri = FileProvider.getUriForFile(context, authority, zipFile)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/zip")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, "開啟 ZIP 壓縮檔")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            try {
                val authority = "${context.packageName}.fileprovider"
                val uri: Uri = FileProvider.getUriForFile(context, authority, zipFile)
                val genericIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                genericIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(Intent.createChooser(genericIntent, "開啟檔案"))
            } catch (err: Exception) {
                viewModelScope.launch {
                    _toastEvent.emit("無法開啟：請確認手機有安裝解壓縮或檔案總管應用程式")
                }
            }
        }
    }

    fun openBrowserHelper() {
        val currentInput = _urlInput.value.trim()
        val url = if (currentInput.isNotBlank() && PostExtractorFacade.findFirstUrl(currentInput) != null) {
            PostExtractorFacade.findFirstUrl(currentInput)!!
        } else {
            "https://www.instagram.com"
        }
        _browserUrl.value = url
        _showBrowserDialog.value = true
    }

    fun closeBrowserHelper() {
        _showBrowserDialog.value = false
        refreshLoginStatus()
    }

    fun onBrowserExtracted(post: PostData) {
        _showBrowserDialog.value = false
        _currentPost.value = post
        _urlInput.value = post.originalUrl
        refreshLoginStatus()
    }

    fun deleteHistoryItem(context: Context, archive: PackagedArchive) {
        val updated = _historyList.value.filter { it.zipFile.absolutePath != archive.zipFile.absolutePath }
        _historyList.value = updated
        if (archive.zipFile.exists()) {
            archive.zipFile.delete()
        }
        saveHistoryToPrefs(context, updated)
    }

    fun loadHistory(context: Context) {
        refreshLoginStatus()
        try {
            val prefs = context.getSharedPreferences("postpacker_prefs", Context.MODE_PRIVATE)
            val json = prefs.getString("history_json", null) ?: return
            val type = object : TypeToken<List<ArchiveDto>>() {}.type
            val dtos: List<ArchiveDto> = gson.fromJson(json, type)
            val list = dtos.mapNotNull { dto ->
                val f = File(dto.filePath)
                if (f.exists()) {
                    PackagedArchive(
                        id = dto.id,
                        platform = dto.platform,
                        author = dto.author,
                        captionPreview = dto.captionPreview,
                        zipFile = f,
                        mediaCount = dto.mediaCount,
                        packagedTimestamp = dto.timestamp
                    )
                } else null
            }
            _historyList.value = list
        } catch (_: Exception) {}
    }

    private fun saveHistoryToPrefs(context: Context, list: List<PackagedArchive>) {
        try {
            val dtos = list.map {
                ArchiveDto(
                    id = it.id,
                    platform = it.platform,
                    author = it.author,
                    captionPreview = it.captionPreview,
                    filePath = it.zipFile.absolutePath,
                    mediaCount = it.mediaCount,
                    timestamp = it.packagedTimestamp
                )
            }
            val prefs = context.getSharedPreferences("postpacker_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("history_json", gson.toJson(dtos)).apply()
        } catch (_: Exception) {}
    }

    private data class ArchiveDto(
        val id: String,
        val platform: com.postpacker.app.model.Platform,
        val author: String,
        val captionPreview: String,
        val filePath: String,
        val mediaCount: Int,
        val timestamp: Long
    )
}
