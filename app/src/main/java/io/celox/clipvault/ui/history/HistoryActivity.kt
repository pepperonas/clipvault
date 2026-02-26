package io.celox.clipvault.ui.history

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import io.celox.clipvault.ClipVaultApp
import io.celox.clipvault.data.ClipEntry
import io.celox.clipvault.licensing.LicenseManager
import io.celox.clipvault.service.ClipAccessibilityService
import io.celox.clipvault.ui.settings.SettingsActivity
import io.celox.clipvault.ui.theme.ClipVaultTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : FragmentActivity() {

    private var viewModel: HistoryViewModel? = null
    private var accessibilityEnabled = mutableStateOf(false)
    private var showPasswordFallback = mutableStateOf(false)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as ClipVaultApp

        // DB is always open (always encrypted) after App.onCreate
        if (app.repository != null) {
            initViewModel(app)
        }

        // If app lock is not enabled, start unlocked
        if (!app.isAppLockEnabled) {
            viewModel?.unlock()
        }

        requestNotificationPermission()

        setContent {
            ClipVaultTheme {
                val isUnlocked = viewModel?.isUnlocked?.collectAsState()?.value ?: !app.isAppLockEnabled
                val appLockEnabled = app.isAppLockEnabled
                val isLicensed = app.licenseManager.isActivated()

                HistoryScreen(
                    viewModel = viewModel,
                    isUnlocked = isUnlocked,
                    appLockEnabled = appLockEnabled,
                    accessibilityEnabled = accessibilityEnabled.value,
                    isLicensed = isLicensed,
                    onOpenAccessibilitySettings = { openAccessibilitySettings() },
                    onCopyToClipboard = { text -> copyToClipboard(text) },
                    onLock = { viewModel?.lock() },
                    onRequestUnlock = { requestUnlock() },
                    onOpenSettings = {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    },
                    showPasswordFallback = showPasswordFallback.value,
                    onPasswordFallbackDismiss = { showPasswordFallback.value = false },
                    onPasswordFallbackSubmit = { password ->
                        val stored = app.keyStoreManager.retrieveAppLockPassword()
                        if (stored == password) {
                            showPasswordFallback.value = false
                            viewModel?.unlock()
                        } else {
                            Toast.makeText(
                                this, "Falsches Passwort", Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        accessibilityEnabled.value = isAccessibilityServiceEnabled()

        val app = application as ClipVaultApp

        // Re-init ViewModel if DB was reopened
        if (app.repository != null && viewModel == null) {
            initViewModel(app)
        }

        if (app.isAppLockEnabled && viewModel?.isUnlocked?.value != true) {
            requestUnlock()
        } else if (!app.isAppLockEnabled) {
            viewModel?.unlock()
        }
    }

    override fun onStop() {
        super.onStop()
        val app = application as ClipVaultApp
        // Only auto-lock when app lock is enabled
        if (!isChangingConfigurations && app.isAppLockEnabled) {
            viewModel?.lock()
        }
    }

    private fun initViewModel(app: ClipVaultApp) {
        val repo = app.repository ?: return
        viewModel = ViewModelProvider(
            this, HistoryViewModel.Factory(repo)
        )[HistoryViewModel::class.java]
    }

    private fun requestUnlock() {
        val app = application as ClipVaultApp
        if (!app.isAppLockEnabled) {
            viewModel?.unlock()
            return
        }

        // Generated password: always use biometric (user doesn't know the password)
        if (app.keyStoreManager.isAppLockPasswordGenerated()) {
            showBiometricPrompt()
            return
        }

        if (app.keyStoreManager.isAppLockBiometricEnabled()) {
            showBiometricPrompt()
        } else {
            showPasswordFallback.value = true
        }
    }

    private fun showBiometricPrompt() {
        val biometricManager = BiometricManager.from(this)
        val canAuthenticate = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )

        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            val app = application as ClipVaultApp
            if (!app.keyStoreManager.isAppLockPasswordGenerated()) {
                showPasswordFallback.value = true
            }
            return
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("ClipVault entsperren")
            .setSubtitle("Authentifiziere dich, um deine Clips zu sehen")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        val biometricPrompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    viewModel?.unlock()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_USER_CANCELED
                    ) {
                        val app = application as ClipVaultApp
                        // Generated password: no fallback possible
                        if (!app.keyStoreManager.isAppLockPasswordGenerated()) {
                            showPasswordFallback.value = true
                        }
                    }
                }
            }
        )

        biometricPrompt.authenticate(promptInfo)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val serviceName = "${packageName}/${ClipAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(serviceName)
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("ClipVault", text))
        Toast.makeText(this, "Kopiert!", Toast.LENGTH_SHORT).show()
    }
}

// --- Main History Screen ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel?,
    isUnlocked: Boolean,
    appLockEnabled: Boolean,
    accessibilityEnabled: Boolean,
    isLicensed: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onCopyToClipboard: (String) -> Unit,
    onLock: () -> Unit,
    onRequestUnlock: () -> Unit,
    onOpenSettings: () -> Unit,
    showPasswordFallback: Boolean,
    onPasswordFallbackDismiss: () -> Unit,
    onPasswordFallbackSubmit: (String) -> Unit
) {
    val entries by (viewModel?.entries?.collectAsState()
        ?: remember { mutableStateOf(emptyList()) })
    val searchQuery by (viewModel?.searchQuery?.collectAsState()
        ?: remember { mutableStateOf("") })
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ClipVault", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(
                            when {
                                appLockEnabled && !isUnlocked -> "Gesperrt"
                                accessibilityEnabled -> "Aktiv . ${entries.size} Eintraege"
                                else -> "Inaktiv . Accessibility aktivieren"
                            },
                            fontSize = 12.sp,
                            color = when {
                                appLockEnabled && !isUnlocked -> MaterialTheme.colorScheme.onSurfaceVariant
                                accessibilityEnabled -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.error
                            }
                        )
                    }
                },
                actions = {
                    if (isUnlocked) {
                        IconButton(onClick = { showSearch = !showSearch }) {
                            Icon(Icons.Default.Search, contentDescription = "Suchen")
                        }
                        IconButton(onClick = { showDeleteAllDialog = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Alle loeschen")
                        }
                        if (appLockEnabled) {
                            IconButton(onClick = onLock) {
                                Icon(Icons.Default.Lock, contentDescription = "Sperren")
                            }
                        }
                    } else if (appLockEnabled) {
                        IconButton(onClick = onRequestUnlock) {
                            Icon(Icons.Default.LockOpen, contentDescription = "Entsperren")
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Einstellungen")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Setup banner if accessibility not enabled (only show when unlocked)
            if (isUnlocked && !accessibilityEnabled) {
                SetupBanner(onOpenSettings = onOpenAccessibilitySettings)
            }

            // License banner when not licensed and at clip limit
            if (isUnlocked && !isLicensed && entries.size >= LicenseManager.getMaxFreeClips()) {
                LicenseBanner()
            }

            // Search bar (only when unlocked)
            if (isUnlocked) {
                AnimatedVisibility(visible = showSearch) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel?.onSearchQueryChanged(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text("Suchen...") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        }
                    )
                }
            }

            if (appLockEnabled && !isUnlocked) {
                // Locked: show masked entries
                if (entries.isEmpty()) {
                    LockedEmptyState(onRequestUnlock = onRequestUnlock)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(items = entries, key = { it.id }) { entry ->
                            LockedClipEntryCard(entry = entry)
                        }
                    }
                }
            } else if (entries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("clipboard emoji", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Noch keine Clips",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            if (accessibilityEnabled)
                                "Kopiere etwas -- es wird automatisch gespeichert"
                            else
                                "Aktiviere den Accessibility Service,\ndann werden Clips automatisch erfasst",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(items = entries, key = { it.id }) { entry ->
                        ClipEntryCard(
                            entry = entry,
                            onCopy = { onCopyToClipboard(entry.content) },
                            onPin = { viewModel?.togglePin(entry) },
                            onDelete = { viewModel?.delete(entry) }
                        )
                    }

                    item {
                        Text(
                            "(c) 2026 Martin Pfeffer | celox.io",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("Alle loeschen?") },
            text = { Text("Alle nicht-angepinnten Clips werden geloescht. Das kann nicht rueckgaengig gemacht werden.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel?.deleteAllUnpinned()
                    showDeleteAllDialog = false
                }) {
                    Text("Loeschen", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    if (showPasswordFallback) {
        PasswordFallbackDialog(
            onDismiss = onPasswordFallbackDismiss,
            onSubmit = onPasswordFallbackSubmit
        )
    }
}

// --- License Banner ---

@Composable
fun LicenseBanner() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Clip-Limit erreicht",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Maximal ${LicenseManager.getMaxFreeClips()} Clips in der kostenlosen Version. Aktiviere eine Lizenz in den Einstellungen fuer unbegrenzte Clips.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
        }
    }
}

// --- Locked State Components ---

@Composable
fun LockedEmptyState(onRequestUnlock: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "ClipVault ist gesperrt",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Entsperre die App, um deine Clips zu sehen",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = onRequestUnlock) {
                Text("Entsperren")
            }
        }
    }
}

@Composable
fun LockedClipEntryCard(entry: ClipEntry) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (entry.pinned)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = formatTimestamp(entry.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "...........",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}

// --- Password Fallback Dialog ---

@Composable
fun PasswordFallbackDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Passwort eingeben") },
        text = {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Passwort") },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Default.VisibilityOff
                            else Icons.Default.Visibility,
                            contentDescription = null
                        )
                    }
                },
                shape = RoundedCornerShape(12.dp)
            )
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(password) }) {
                Text("Entsperren")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}

// --- Setup Banner ---

@Composable
fun SetupBanner(onOpenSettings: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Accessibility Service aktivieren",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "ClipVault benoetigt den Accessibility Service, um Kopier-Aktionen automatisch zu erkennen. Aktiviere \"ClipVault\" in den Bedienungshilfen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onOpenSettings,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Einstellungen oeffnen")
            }
        }
    }
}

// --- Clip Entry Card (Unlocked) ---

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClipEntryCard(
    entry: ClipEntry,
    onCopy: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .animateContentSize()
            .combinedClickable(
                onClick = onCopy,
                onLongClick = { expanded = !expanded }
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (entry.pinned)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = formatTimestamp(entry.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = entry.content,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )

            AnimatedVisibility(visible = expanded || entry.pinned) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Kopieren",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = onPin, modifier = Modifier.size(36.dp)) {
                        Icon(
                            if (entry.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = "Anpinnen",
                            modifier = Modifier.size(18.dp),
                            tint = if (entry.pinned)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Loeschen",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

// --- Helpers ---

private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> "Gerade eben"
        diff < 3_600_000 -> "Vor ${diff / 60_000} Min."
        diff < 86_400_000 -> "Vor ${diff / 3_600_000} Std."
        else -> {
            val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)
            sdf.format(Date(timestamp))
        }
    }
}
