package io.celox.clipvault.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import io.celox.clipvault.ClipVaultApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Accessibility Service that captures clipboard changes.
 *
 * Three strategies for maximum compatibility:
 * 1. ClipboardManager.OnPrimaryClipChangedListener
 * 2. Accessibility event-triggered clipboard polling
 * 3. Periodic polling every 2 seconds as final fallback (Android 16+)
 */
class ClipAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "ClipAccessibility"
        private const val POLL_INTERVAL_MS = 2000L
        var isRunning = false
            private set
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var clipboardManager: ClipboardManager
    private var lastClipText: String? = null
    private var lastClipTime: Long = 0
    private var listenerFired = false

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        listenerFired = true
        Log.d(TAG, "Listener fired")
        handleClipboardChange("listener")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true

        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.addPrimaryClipChangedListener(clipListener)

        ClipVaultService.start(this)

        // Start polling fallback — if the listener works, this will mostly no-op
        startClipboardPolling()

        Log.i(TAG, "Service connected, listener + polling active")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        // Try to read clipboard on any accessibility event as mid-priority fallback
        tryReadClipboard("event")
    }

    override fun onInterrupt() {
        Log.w(TAG, "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        if (::clipboardManager.isInitialized) {
            clipboardManager.removePrimaryClipChangedListener(clipListener)
        }
        serviceScope.cancel()
        Log.i(TAG, "Service destroyed")
    }

    private fun startClipboardPolling() {
        serviceScope.launch {
            Log.d(TAG, "Polling coroutine started")
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                tryReadClipboard("poll")
            }
        }
    }

    private fun tryReadClipboard(source: String) {
        try {
            if (!::clipboardManager.isInitialized) return
            val clip = clipboardManager.primaryClip ?: return
            if (clip.itemCount == 0) return

            val text = clip.getItemAt(0).coerceToText(this).toString()
            if (text.isBlank()) return
            if (text == lastClipText) return

            Log.d(TAG, "New clip via $source")
            saveClip(text)
        } catch (_: Exception) {
            // Clipboard read can fail silently
        }
    }

    private fun handleClipboardChange(source: String) {
        try {
            val clip = clipboardManager.primaryClip ?: return
            if (clip.itemCount == 0) return

            val text = clip.getItemAt(0).coerceToText(this).toString()
            if (text.isBlank()) return

            val now = System.currentTimeMillis()
            if (text == lastClipText && now - lastClipTime < 500) return

            saveClip(text)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading clipboard", e)
        }
    }

    private fun saveClip(text: String) {
        lastClipText = text
        lastClipTime = System.currentTimeMillis()

        val app = application as ClipVaultApp
        val repo = app.repository ?: run {
            Log.w(TAG, "DB not open yet, dropping clip")
            return
        }
        serviceScope.launch {
            val id = repo.insert(text)
            Log.d(TAG, "Saved clip id=$id len=${text.length}")
            launch(Dispatchers.Main) {
                if (id == -1L) {
                    Toast.makeText(
                        this@ClipAccessibilityService,
                        "Clip-Limit erreicht. Lizenz aktivieren fuer unbegrenzte Clips.",
                        Toast.LENGTH_LONG
                    ).show()
                } else if (id > 0) {
                    Toast.makeText(
                        this@ClipAccessibilityService,
                        "Gespeichert: ${text.take(35)}${if (text.length > 35) "..." else ""}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}
