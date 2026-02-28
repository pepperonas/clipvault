package io.celox.clipvault.util

// Pre-compiled regex patterns for performance
private val COLOR_HEX_PATTERN = Regex("""^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$""")
private val COORDINATES_PATTERN = Regex("""-?\d{1,3}\.\d+\s*,\s*-?\d{1,3}\.\d+""")
private val IBAN_PATTERN = Regex("""^[A-Z]{2}\d{2}[A-Z0-9]{11,30}$""")
private val MARKDOWN_LINK_PATTERN = Regex("""\[.+]\(.+\)""")
private val MARKDOWN_PATTERN = Regex("""(^#{1,6}\s)|\*\*[^*]+\*\*""", RegexOption.MULTILINE)
private val CODE_PATTERN = Regex("""\b(fun |def |function |class |import |const |val |var |let |return |if\s*\(|for\s*\()|(=>|->)""")
private val ADDRESS_DE_PATTERN = Regex("""\d+\s+[\w\s\u00C0-\u024F]*(stra(?:ß|ss)e|str\.|weg|platz|gasse|allee)\b""", RegexOption.IGNORE_CASE)
private val ADDRESS_EN_PATTERN = Regex("""\d+\s+\w+\s+(street|st\.|avenue|ave\.|road|rd\.|boulevard|blvd\.|drive|dr\.|lane|ln\.)\b""", RegexOption.IGNORE_CASE)
private val URL_PATTERN = Regex("""https?://|www\.""")
private val EMAIL_PATTERN = Regex("""[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}""")
private val PHONE_PATTERN = Regex("""[+]?[0-9\s\-()]{7,}""")

fun detectContentType(content: String): ContentType {
    val trimmed = content.trim()
    val lowered = trimmed.lowercase()

    // Social media (highest priority — specific domains)
    return when {
        lowered.contains("instagram.com") || lowered.contains("instagr.am") -> ContentType.INSTAGRAM
        lowered.contains("facebook.com") || lowered.contains("fb.com") || lowered.contains("fb.me") -> ContentType.FACEBOOK
        lowered.contains("youtube.com") || lowered.contains("youtu.be") -> ContentType.YOUTUBE
        lowered.contains("twitter.com") || lowered.contains("x.com") || lowered.contains("t.co/") -> ContentType.TWITTER
        lowered.contains("tiktok.com") || lowered.contains("vm.tiktok.com") -> ContentType.TIKTOK
        lowered.contains("linkedin.com") || lowered.contains("lnkd.in") -> ContentType.LINKEDIN
        lowered.contains("github.com") || lowered.contains("gitlab.com") -> ContentType.GITHUB

        // JSON
        (trimmed.startsWith("{") && trimmed.contains("\":")) ||
                trimmed.startsWith("[{") -> ContentType.JSON

        // Color hex
        COLOR_HEX_PATTERN.matches(trimmed) -> ContentType.COLOR_HEX

        // Coordinates (but not if it's a URL containing numbers)
        COORDINATES_PATTERN.containsMatchIn(trimmed) && !URL_PATTERN.containsMatchIn(lowered) -> ContentType.COORDINATES

        // IBAN (2 letters + 2 digits + 11-30 alphanumeric, spaces ignored)
        isIban(trimmed) -> ContentType.IBAN

        // Markdown link [text](url) — checked separately since it may contain URLs
        MARKDOWN_LINK_PATTERN.containsMatchIn(trimmed) -> ContentType.MARKDOWN

        // Markdown headings and bold (but not plain URLs)
        MARKDOWN_PATTERN.containsMatchIn(trimmed) && !URL_PATTERN.containsMatchIn(lowered) -> ContentType.MARKDOWN

        // Code keywords
        CODE_PATTERN.containsMatchIn(trimmed) -> ContentType.CODE

        // Address (DE or EN pattern)
        ADDRESS_DE_PATTERN.containsMatchIn(trimmed) || ADDRESS_EN_PATTERN.containsMatchIn(trimmed) -> ContentType.ADDRESS

        // Generic URL
        URL_PATTERN.containsMatchIn(lowered) -> ContentType.URL

        // Email
        EMAIL_PATTERN.containsMatchIn(lowered) -> ContentType.EMAIL

        // Phone
        PHONE_PATTERN.containsMatchIn(trimmed) -> ContentType.PHONE

        else -> ContentType.TEXT
    }
}

private fun isIban(text: String): Boolean {
    val cleaned = text.uppercase().replace(" ", "")
    return cleaned.length in 15..34 && IBAN_PATTERN.matches(cleaned)
}

fun isValidIban(iban: String): Boolean {
    val cleaned = iban.uppercase().replace(" ", "")
    if (cleaned.length !in 15..34 || !IBAN_PATTERN.matches(cleaned)) return false
    // Move first 4 chars to end, replace letters with numbers (A=10..Z=35), check mod 97 == 1
    val rearranged = cleaned.substring(4) + cleaned.substring(0, 4)
    var remainder = 0
    for (c in rearranged) {
        val value = if (c.isLetter()) (c - 'A' + 10) else (c - '0')
        remainder = if (value >= 10) (remainder * 100 + value) % 97 else (remainder * 10 + value) % 97
    }
    return remainder == 1
}
