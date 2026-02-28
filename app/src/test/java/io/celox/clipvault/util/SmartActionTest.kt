package io.celox.clipvault.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartActionTest {

    @Test
    fun `every content type has COPY as first and SHARE as last action`() {
        ContentType.entries.forEach { type ->
            val actions = resolveSmartActions(type)
            assertEquals(
                "COPY should be first for $type",
                SmartActionType.COPY,
                actions.first().actionType
            )
            assertEquals(
                "SHARE should be last for $type",
                SmartActionType.SHARE,
                actions.last().actionType
            )
        }
    }

    @Test
    fun `URL has OPEN_BROWSER action`() {
        val actions = resolveSmartActions(ContentType.URL)
        assertTrue(actions.any { it.actionType == SmartActionType.OPEN_BROWSER })
    }

    @Test
    fun `PHONE has CALL and SEND_SMS actions`() {
        val actions = resolveSmartActions(ContentType.PHONE)
        assertTrue(actions.any { it.actionType == SmartActionType.CALL })
        assertTrue(actions.any { it.actionType == SmartActionType.SEND_SMS })
    }

    @Test
    fun `EMAIL has SEND_EMAIL action`() {
        val actions = resolveSmartActions(ContentType.EMAIL)
        assertTrue(actions.any { it.actionType == SmartActionType.SEND_EMAIL })
    }

    @Test
    fun `COORDINATES has OPEN_MAPS action`() {
        val actions = resolveSmartActions(ContentType.COORDINATES)
        assertTrue(actions.any { it.actionType == SmartActionType.OPEN_MAPS })
    }

    @Test
    fun `ADDRESS has OPEN_MAPS action`() {
        val actions = resolveSmartActions(ContentType.ADDRESS)
        assertTrue(actions.any { it.actionType == SmartActionType.OPEN_MAPS })
    }

    @Test
    fun `TEXT has only COPY and SHARE`() {
        val actions = resolveSmartActions(ContentType.TEXT)
        assertEquals(2, actions.size)
        assertEquals(SmartActionType.COPY, actions[0].actionType)
        assertEquals(SmartActionType.SHARE, actions[1].actionType)
    }

    @Test
    fun `all social media types have OPEN_BROWSER action`() {
        val socialTypes = listOf(
            ContentType.INSTAGRAM,
            ContentType.FACEBOOK,
            ContentType.YOUTUBE,
            ContentType.TWITTER,
            ContentType.TIKTOK,
            ContentType.LINKEDIN,
            ContentType.GITHUB
        )
        socialTypes.forEach { type ->
            val actions = resolveSmartActions(type)
            assertTrue(
                "$type should have OPEN_BROWSER",
                actions.any { it.actionType == SmartActionType.OPEN_BROWSER }
            )
        }
    }
}
