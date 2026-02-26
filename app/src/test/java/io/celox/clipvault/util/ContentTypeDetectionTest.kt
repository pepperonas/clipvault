package io.celox.clipvault.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ContentTypeDetectionTest {

    @Test
    fun `detects Instagram links`() {
        assertEquals(ContentTypeLabel.INSTAGRAM, detectContentTypeLabel("https://www.instagram.com/p/abc123"))
        assertEquals(ContentTypeLabel.INSTAGRAM, detectContentTypeLabel("https://instagr.am/p/abc123"))
    }

    @Test
    fun `detects Facebook links`() {
        assertEquals(ContentTypeLabel.FACEBOOK, detectContentTypeLabel("https://facebook.com/post/123"))
        assertEquals(ContentTypeLabel.FACEBOOK, detectContentTypeLabel("https://fb.com/page"))
        assertEquals(ContentTypeLabel.FACEBOOK, detectContentTypeLabel("https://fb.me/story"))
    }

    @Test
    fun `detects YouTube links`() {
        assertEquals(ContentTypeLabel.YOUTUBE, detectContentTypeLabel("https://www.youtube.com/watch?v=abc"))
        assertEquals(ContentTypeLabel.YOUTUBE, detectContentTypeLabel("https://youtu.be/abc123"))
    }

    @Test
    fun `detects X (Twitter) links`() {
        assertEquals(ContentTypeLabel.TWITTER, detectContentTypeLabel("https://twitter.com/user/status/123"))
        assertEquals(ContentTypeLabel.TWITTER, detectContentTypeLabel("https://x.com/user/status/123"))
        assertEquals(ContentTypeLabel.TWITTER, detectContentTypeLabel("https://t.co/abc123"))
    }

    @Test
    fun `detects TikTok links`() {
        assertEquals(ContentTypeLabel.TIKTOK, detectContentTypeLabel("https://www.tiktok.com/@user/video/123"))
        assertEquals(ContentTypeLabel.TIKTOK, detectContentTypeLabel("https://vm.tiktok.com/abc123"))
    }

    @Test
    fun `detects LinkedIn links`() {
        assertEquals(ContentTypeLabel.LINKEDIN, detectContentTypeLabel("https://www.linkedin.com/in/user"))
        assertEquals(ContentTypeLabel.LINKEDIN, detectContentTypeLabel("https://lnkd.in/abc123"))
    }

    @Test
    fun `detects GitHub and GitLab links`() {
        assertEquals(ContentTypeLabel.GITHUB, detectContentTypeLabel("https://github.com/user/repo"))
        assertEquals(ContentTypeLabel.GITHUB, detectContentTypeLabel("https://gitlab.com/user/repo"))
    }

    @Test
    fun `detects generic URLs`() {
        assertEquals(ContentTypeLabel.URL, detectContentTypeLabel("https://example.com/page"))
        assertEquals(ContentTypeLabel.URL, detectContentTypeLabel("http://test.org"))
        assertEquals(ContentTypeLabel.URL, detectContentTypeLabel("www.example.com"))
    }

    @Test
    fun `detects email addresses`() {
        assertEquals(ContentTypeLabel.EMAIL, detectContentTypeLabel("user@example.com"))
        assertEquals(ContentTypeLabel.EMAIL, detectContentTypeLabel("test.user+tag@domain.co.uk"))
    }

    @Test
    fun `detects phone numbers`() {
        assertEquals(ContentTypeLabel.PHONE, detectContentTypeLabel("+49 170 1234567"))
        assertEquals(ContentTypeLabel.PHONE, detectContentTypeLabel("(030) 123-4567"))
        assertEquals(ContentTypeLabel.PHONE, detectContentTypeLabel("0170 1234567"))
    }

    @Test
    fun `detects plain text`() {
        assertEquals(ContentTypeLabel.TEXT, detectContentTypeLabel("Hello World"))
        assertEquals(ContentTypeLabel.TEXT, detectContentTypeLabel("Just some text"))
        assertEquals(ContentTypeLabel.TEXT, detectContentTypeLabel("Kotlin is great"))
    }

    @Test
    fun `handles whitespace and case insensitivity`() {
        assertEquals(ContentTypeLabel.INSTAGRAM, detectContentTypeLabel("  HTTPS://INSTAGRAM.COM/p/abc  "))
        assertEquals(ContentTypeLabel.YOUTUBE, detectContentTypeLabel("  YouTube.com/watch?v=123  "))
    }

    @Test
    fun `social media takes priority over generic URL`() {
        // Instagram URL should be detected as Instagram, not generic URL
        assertEquals(ContentTypeLabel.INSTAGRAM, detectContentTypeLabel("https://instagram.com/p/test"))
        assertEquals(ContentTypeLabel.GITHUB, detectContentTypeLabel("https://github.com/user/repo"))
    }
}
