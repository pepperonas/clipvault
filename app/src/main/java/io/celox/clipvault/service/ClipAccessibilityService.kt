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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Accessibility Service that captures clipboard changes.
 *
 * Three strategies for maximum compatibility:
 * 1. ClipboardManager.OnPrimaryClipChangedListener (primary)
 * 2. Accessibility event-triggered clipboard read (fallback)
 * 3. Periodic polling every 2 seconds as final fallback (Android 16+)
 */
class ClipAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "ClipAccessibility"
        private const val POLL_INTERVAL_MS = 2000L
        private const val DEBOUNCE_MS = 500L
        var isRunning = false
            private set
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var clipboardManager: ClipboardManager

    // Thread-safe clipboard state via Mutex
    private val clipMutex = Mutex()
    @Volatile private var lastClipText: String? = null
    @Volatile private var lastClipTime: Long = 0

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        Log.d(TAG, "Listener fired")
        processClipboard("listener")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true

        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.addPrimaryClipChangedListener(clipListener)

        ClipVaultService.start(this)
        startClipboardPolling()

        Log.i(TAG, "Service connected, listener + polling active")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        processClipboard("event")
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
        ClipVaultService.stop(this)
        Log.i(TAG, "Service destroyed")
    }

    private fun startClipboardPolling() {
        serviceScope.launch {
            Log.d(TAG, "Polling coroutine started")
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                processClipboard("poll")
            }
        }
    }

    /**
     * Single unified method for all capture strategies.
     * Reads clipboard, deduplicates via Mutex, and saves.
     */
    private fun processClipboard(source: String) {
        val text = readClipboardText() ?: return

        serviceScope.launch {
            clipMutex.withLock {
                val now = System.currentTimeMillis()
                if (text == lastClipText && now - lastClipTime < DEBOUNCE_MS) return@launch
                lastClipText = text
                lastClipTime = now
            }

            Log.d(TAG, "New clip via $source (${text.length} chars)")
            saveClip(text)
        }
    }

    /**
     * Reads current clipboard text. Returns null if unavailable or empty.
     */
    private fun readClipboardText(): String? {
        return try {
            if (!::clipboardManager.isInitialized) return null
            val clip = clipboardManager.primaryClip ?: return null
            if (clip.itemCount == 0) return null

            val text = clip.getItemAt(0).coerceToText(this).toString()
            if (text.isBlank()) null else text
        } catch (e: SecurityException) {
            Log.w(TAG, "Clipboard access denied (Android restriction): ${e.message}")
            null
        } catch (e: Exception) {
            Log.w(TAG, "Clipboard read failed: ${e.message}")
            null
        }
    }

    private fun saveClip(text: String) {
        val app = application as ClipVaultApp
        val repo = app.repository
        if (repo == null) {
            Log.w(TAG, "DB not open yet, retrying in 500ms")
            serviceScope.launch {
                delay(500)
                val retryRepo = (application as ClipVaultApp).repository
                if (retryRepo != null) {
                    insertAndNotify(retryRepo, text)
                } else {
                    Log.e(TAG, "DB still not open, dropping clip")
                }
            }
            return
        }
        serviceScope.launch {
            insertAndNotify(repo, text)
        }
    }

    private suspend fun insertAndNotify(repo: io.celox.clipvault.data.ClipRepository, text: String) {
        try {
            val id = repo.insert(text)
            Log.d(TAG, "Saved clip id=$id len=${text.length}")
            withContext(Dispatchers.Main) {
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
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save clip: ${e.message}", e)
        }
    }
}
