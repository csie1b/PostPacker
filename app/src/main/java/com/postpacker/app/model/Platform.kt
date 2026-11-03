package com.postpacker.app.model

enum class Platform(val displayName: String) {
    INSTAGRAM("Instagram"),
    FACEBOOK("Facebook"),
    UNKNOWN("未知平台");

    companion object {
        fun fromUrl(url: String): Platform {
            val lower = url.lowercase()
            return when {
                lower.contains("instagram.com") || lower.contains("instagr.am") -> INSTAGRAM
                lower.contains("facebook.com") || lower.contains("fb.watch") || lower.contains("fb.com") -> FACEBOOK
                else -> UNKNOWN
            }
        }
    }
}
