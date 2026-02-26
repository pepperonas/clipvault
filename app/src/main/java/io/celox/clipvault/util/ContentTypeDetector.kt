package io.celox.clipvault.util

enum class ContentTypeLabel {
    INSTAGRAM, FACEBOOK, YOUTUBE, TWITTER, TIKTOK, LINKEDIN, GITHUB, URL, EMAIL, PHONE, TEXT
}

fun detectContentTypeLabel(content: String): ContentTypeLabel {
    val trimmed = content.trim().lowercase()
    return when {
        trimmed.contains("instagram.com") || trimmed.contains("instagr.am") -> ContentTypeLabel.INSTAGRAM
        trimmed.contains("facebook.com") || trimmed.contains("fb.com") || trimmed.contains("fb.me") -> ContentTypeLabel.FACEBOOK
        trimmed.contains("youtube.com") || trimmed.contains("youtu.be") -> ContentTypeLabel.YOUTUBE
        trimmed.contains("twitter.com") || trimmed.contains("x.com") || trimmed.contains("t.co/") -> ContentTypeLabel.TWITTER
        trimmed.contains("tiktok.com") || trimmed.contains("vm.tiktok.com") -> ContentTypeLabel.TIKTOK
        trimmed.contains("linkedin.com") || trimmed.contains("lnkd.in") -> ContentTypeLabel.LINKEDIN
        trimmed.contains("github.com") || trimmed.contains("gitlab.com") -> ContentTypeLabel.GITHUB
        trimmed.matches(Regex(".*https?://.*")) || trimmed.matches(Regex(".*www\\..*")) -> ContentTypeLabel.URL
        trimmed.matches(Regex(".*[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}.*")) -> ContentTypeLabel.EMAIL
        trimmed.matches(Regex(".*[+]?[0-9\\s\\-()]{7,}.*")) -> ContentTypeLabel.PHONE
        else -> ContentTypeLabel.TEXT
    }
}
