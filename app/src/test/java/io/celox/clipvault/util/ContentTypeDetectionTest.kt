package io.celox.clipvault.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentTypeDetectionTest {

    @Test
    fun `detects Instagram links`() {
        assertEquals(ContentType.INSTAGRAM, detectContentType("https://www.instagram.com/p/abc123"))
        assertEquals(ContentType.INSTAGRAM, detectContentType("https://instagr.am/p/abc123"))
    }

    @Test
    fun `detects Facebook links`() {
        assertEquals(ContentType.FACEBOOK, detectContentType("https://facebook.com/post/123"))
        assertEquals(ContentType.FACEBOOK, detectContentType("https://fb.com/page"))
        assertEquals(ContentType.FACEBOOK, detectContentType("https://fb.me/story"))
    }

    @Test
    fun `detects YouTube links`() {
        assertEquals(ContentType.YOUTUBE, detectContentType("https://www.youtube.com/watch?v=abc"))
        assertEquals(ContentType.YOUTUBE, detectContentType("https://youtu.be/abc123"))
    }

    @Test
    fun `detects X (Twitter) links`() {
        assertEquals(ContentType.TWITTER, detectContentType("https://twitter.com/user/status/123"))
        assertEquals(ContentType.TWITTER, detectContentType("https://x.com/user/status/123"))
        assertEquals(ContentType.TWITTER, detectContentType("https://t.co/abc123"))
    }

    @Test
    fun `detects TikTok links`() {
        assertEquals(ContentType.TIKTOK, detectContentType("https://www.tiktok.com/@user/video/123"))
        assertEquals(ContentType.TIKTOK, detectContentType("https://vm.tiktok.com/abc123"))
    }

    @Test
    fun `detects LinkedIn links`() {
        assertEquals(ContentType.LINKEDIN, detectContentType("https://www.linkedin.com/in/user"))
        assertEquals(ContentType.LINKEDIN, detectContentType("https://lnkd.in/abc123"))
    }

    @Test
    fun `detects GitHub and GitLab links`() {
        assertEquals(ContentType.GITHUB, detectContentType("https://github.com/user/repo"))
        assertEquals(ContentType.GITHUB, detectContentType("https://gitlab.com/user/repo"))
    }

    @Test
    fun `detects generic URLs`() {
        assertEquals(ContentType.URL, detectContentType("https://example.com/page"))
        assertEquals(ContentType.URL, detectContentType("http://test.org"))
        assertEquals(ContentType.URL, detectContentType("www.example.com"))
    }

    @Test
    fun `detects email addresses`() {
        assertEquals(ContentType.EMAIL, detectContentType("user@example.com"))
        assertEquals(ContentType.EMAIL, detectContentType("test.user+tag@domain.co.uk"))
    }

    @Test
    fun `detects phone numbers`() {
        assertEquals(ContentType.PHONE, detectContentType("+49 170 1234567"))
        assertEquals(ContentType.PHONE, detectContentType("(030) 123-4567"))
        assertEquals(ContentType.PHONE, detectContentType("0170 1234567"))
    }

    @Test
    fun `detects plain text`() {
        assertEquals(ContentType.TEXT, detectContentType("Hello World"))
        assertEquals(ContentType.TEXT, detectContentType("Just some text"))
        assertEquals(ContentType.TEXT, detectContentType("Kotlin is great"))
    }

    @Test
    fun `handles whitespace and case insensitivity`() {
        assertEquals(ContentType.INSTAGRAM, detectContentType("  HTTPS://INSTAGRAM.COM/p/abc  "))
        assertEquals(ContentType.YOUTUBE, detectContentType("  YouTube.com/watch?v=123  "))
    }

    @Test
    fun `social media takes priority over generic URL`() {
        assertEquals(ContentType.INSTAGRAM, detectContentType("https://instagram.com/p/test"))
        assertEquals(ContentType.GITHUB, detectContentType("https://github.com/user/repo"))
    }

    // --- New content type tests ---

    @Test
    fun `detects JSON content`() {
        assertEquals(ContentType.JSON, detectContentType("""{"name": "test", "value": 42}"""))
        assertEquals(ContentType.JSON, detectContentType("""[{"id": 1}, {"id": 2}]"""))
        assertEquals(ContentType.JSON, detectContentType("""{ "key": "value" }"""))
    }

    @Test
    fun `detects color hex codes`() {
        assertEquals(ContentType.COLOR_HEX, detectContentType("#FF5733"))
        assertEquals(ContentType.COLOR_HEX, detectContentType("#fff"))
        assertEquals(ContentType.COLOR_HEX, detectContentType("#FF5733AA"))
    }

    @Test
    fun `detects coordinates`() {
        assertEquals(ContentType.COORDINATES, detectContentType("48.8566, 2.3522"))
        assertEquals(ContentType.COORDINATES, detectContentType("-33.8688, 151.2093"))
        assertEquals(ContentType.COORDINATES, detectContentType("52.5200, 13.4050"))
    }

    @Test
    fun `detects IBAN numbers`() {
        assertEquals(ContentType.IBAN, detectContentType("DE89370400440532013000"))
        assertEquals(ContentType.IBAN, detectContentType("DE89 3704 0044 0532 0130 00"))
        assertEquals(ContentType.IBAN, detectContentType("GB29NWBK60161331926819"))
    }

    @Test
    fun `detects markdown content`() {
        assertEquals(ContentType.MARKDOWN, detectContentType("# Hello World"))
        assertEquals(ContentType.MARKDOWN, detectContentType("This is **bold** text"))
        assertEquals(ContentType.MARKDOWN, detectContentType("[click here](https://example.com)"))
    }

    @Test
    fun `detects code snippets`() {
        assertEquals(ContentType.CODE, detectContentType("fun main() { println(\"Hello\") }"))
        assertEquals(ContentType.CODE, detectContentType("def calculate(x):"))
        assertEquals(ContentType.CODE, detectContentType("const value = items.map(x => x.id)"))
        assertEquals(ContentType.CODE, detectContentType("import java.util.List"))
    }

    @Test
    fun `detects addresses`() {
        assertEquals(ContentType.ADDRESS, detectContentType("123 Main Street"))
        assertEquals(ContentType.ADDRESS, detectContentType("42 Berliner Straße"))
        assertEquals(ContentType.ADDRESS, detectContentType("15 Hauptweg"))
        assertEquals(ContentType.ADDRESS, detectContentType("7 Marktplatz"))
    }

    // --- Priority tests ---

    @Test
    fun `social media has higher priority than JSON`() {
        // A URL that looks like it could be JSON shouldn't be detected as JSON
        assertEquals(ContentType.GITHUB, detectContentType("https://github.com/user/repo"))
    }

    @Test
    fun `JSON has higher priority than code`() {
        // JSON with "import" keyword inside should still be JSON
        assertEquals(ContentType.JSON, detectContentType("""{"import": "module"}"""))
    }

    @Test
    fun `color hex is not confused with text`() {
        // Only exact hex patterns should match
        assertEquals(ContentType.TEXT, detectContentType("#notahex"))
        assertEquals(ContentType.COLOR_HEX, detectContentType("#ABC"))
    }

    @Test
    fun `code has higher priority than address`() {
        // "import something" should be code, not address
        assertEquals(ContentType.CODE, detectContentType("import java.util.List"))
    }

    // --- Edge-case tests ---

    @Test
    fun `empty string and whitespace return TEXT`() {
        assertEquals(ContentType.TEXT, detectContentType(""))
        assertEquals(ContentType.TEXT, detectContentType("   "))
        assertEquals(ContentType.TEXT, detectContentType("\t\n"))
    }

    @Test
    fun `URL with coordinate-like numbers is URL not COORDINATES`() {
        assertEquals(ContentType.URL, detectContentType("https://example.com/48.8566,2.3522"))
        assertEquals(ContentType.URL, detectContentType("http://maps.google.com/-33.8688,151.2093"))
    }

    @Test
    fun `invalid hex colors return TEXT`() {
        assertEquals(ContentType.TEXT, detectContentType("#GGG"))
        assertEquals(ContentType.TEXT, detectContentType("#12345"))
        assertEquals(ContentType.TEXT, detectContentType("#"))
    }

    @Test
    fun `too short IBAN returns TEXT`() {
        assertEquals(ContentType.TEXT, detectContentType("DE89"))
    }

    @Test
    fun `too long IBAN matches PHONE due to digit count`() {
        // Long numeric string matches phone pattern, not IBAN
        assertEquals(ContentType.PHONE, detectContentType("DE8937040044053201300012345678901234567"))
    }

    @Test
    fun `IBAN with invalid characters does not match IBAN`() {
        // "DE89 3704 0044 @@@@" — the digit portion matches phone
        val result = detectContentType("DE89 3704 0044 @@@@")
        assertTrue(result != ContentType.IBAN)
    }

    @Test
    fun `markdown with URL is MARKDOWN not URL`() {
        assertEquals(ContentType.MARKDOWN, detectContentType("[link](https://example.com)"))
        assertEquals(ContentType.MARKDOWN, detectContentType("Click [here](http://test.com) to continue"))
    }

    @Test
    fun `multi-line code snippet is CODE`() {
        assertEquals(ContentType.CODE, detectContentType("fun main() {\n    println(\"Hello\")\n}"))
        assertEquals(ContentType.CODE, detectContentType("const x = 1\nconst y = 2"))
    }

    @Test
    fun `social media URL mixed with text is detected`() {
        assertEquals(ContentType.INSTAGRAM, detectContentType("Check out https://instagram.com/p/test123"))
        assertEquals(ContentType.YOUTUBE, detectContentType("Watch this: youtube.com/watch?v=abc"))
    }

    @Test
    fun `JSON-like text that is not JSON returns TEXT`() {
        assertEquals(ContentType.TEXT, detectContentType("I said {hello} to you"))
        assertEquals(ContentType.TEXT, detectContentType("{no quotes here}"))
    }
}
