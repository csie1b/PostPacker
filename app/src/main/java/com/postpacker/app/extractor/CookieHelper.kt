package com.postpacker.app.extractor

import android.webkit.CookieManager

object CookieHelper {

    fun getCookies(url: String): String? {
        return try {
            CookieManager.getInstance().getCookie(url)
        } catch (_: Exception) {
            null
        }
    }

    fun isInstagramLoggedIn(): Boolean {
        val cookies = getCookies("https://www.instagram.com") ?: return false
        return cookies.contains("sessionid=")
    }

    fun isFacebookLoggedIn(): Boolean {
        val cookies = getCookies("https://www.facebook.com") ?: return false
        return cookies.contains("c_user=")
    }

    fun getInstagramUserId(): String? {
        val cookies = getCookies("https://www.instagram.com") ?: return null
        val match = Regex("ds_user_id=([^;]+)").find(cookies)
        return match?.groupValues?.get(1)
    }

    fun clearAllCookies(onComplete: () -> Unit = {}) {
        try {
            val cm = CookieManager.getInstance()
            cm.removeAllCookies {
                cm.flush()
                onComplete()
            }
        } catch (_: Exception) {
            onComplete()
        }
    }
}
