package io.celox.clipvault.ui.history

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import io.celox.clipvault.R
import io.celox.clipvault.ClipVaultApp
import io.celox.clipvault.data.ClipEntry
import io.celox.clipvault.service.ClipAccessibilityService
import io.celox.clipvault.util.ContentType
import io.celox.clipvault.util.SmartActionType
import io.celox.clipvault.util.SortOrder
import io.celox.clipvault.util.detectContentType
import io.celox.clipvault.util.isValidIban
import io.celox.clipvault.ui.settings.SettingsActivity
import io.celox.clipvault.ui.statistics.StatisticsActivity
import io.celox.clipvault.ui.theme.ClipVaultTheme
import kotlinx.coroutines.launch
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

        // Handle Share Target intent
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!text.isNullOrBlank()) {
                lifecycleScope.launch {
                    app.repository?.insert(text)
                    Toast.makeText(this@HistoryActivity, getString(R.string.share_received), Toast.LENGTH_SHORT).show()
                }
            }
        }

        requestNotificationPermission()

        setContent {
            ClipVaultTheme(amoledMode = app.keyStoreManager.isAmoledMode()) {
                val isUnlocked = viewModel?.isUnlocked?.collectAsState()?.value ?: !app.isAppLockEnabled
                val appLockEnabled = app.isAppLockEnabled
                HistoryScreen(
                    viewModel = viewModel,
                    isUnlocked = isUnlocked,
                    appLockEnabled = appLockEnabled,
                    accessibilityEnabled = accessibilityEnabled.value,
                    onOpenAccessibilitySettings = { openAccessibilitySettings() },
                    onOpenAppDetailSettings = { openAppDetailSettings() },
                    onCopyToClipboard = { text -> copyToClipboard(text) },
                    onSmartAction = { type, content -> handleSmartAction(type, content) },
                    onLock = { viewModel?.lock() },
                    onRequestUnlock = { requestUnlock() },
                    onOpenSettings = {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    },
                    onOpenStatistics = {
                        startActivity(Intent(this, StatisticsActivity::class.java))
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
                                this, getString(R.string.wrong_password), Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        accessibilityEnabled.value = true // TEMP: for screenshots

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
            .setTitle(getString(R.string.biometric_title))
            .setSubtitle(getString(R.string.biometric_subtitle))
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

    private fun openAppDetailSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
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

    private fun handleSmartAction(actionType: SmartActionType, content: String) {
        when (actionType) {
            SmartActionType.COPY -> copyToClipboard(content)
            SmartActionType.OPEN_BROWSER -> {
                try {
                    val url = if (content.trim().startsWith("http")) content.trim()
                    else "https://${content.trim()}"
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: Exception) {
                    showBriefToast(getString(R.string.action_failed))
                }
            }
            SmartActionType.CALL -> {
                try {
                    startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${content.trim()}")))
                } catch (_: Exception) {
                    showBriefToast(getString(R.string.action_failed))
                }
            }
            SmartActionType.SEND_SMS -> {
                try {
                    startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${content.trim()}")))
                } catch (_: Exception) {
                    showBriefToast(getString(R.string.action_failed))
                }
            }
            SmartActionType.SEND_EMAIL -> {
                try {
                    startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${content.trim()}")))
                } catch (_: Exception) {
                    showBriefToast(getString(R.string.action_failed))
                }
            }
            SmartActionType.SHARE -> {
                try {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, content)
                    }
                    startActivity(Intent.createChooser(shareIntent, null))
                } catch (_: Exception) {
                    showBriefToast(getString(R.string.action_failed))
                }
            }
            SmartActionType.OPEN_MAPS -> {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(content.trim())}")))
                } catch (_: Exception) {
                    showBriefToast(getString(R.string.action_failed))
                }
            }
        }
    }

    private fun copyToClipboard(text: String) {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("ClipVault", text))
            showBriefToast(getString(R.string.copied))
        } catch (e: Exception) {
            showBriefToast(getString(R.string.copy_failed))
        }
    }

    private fun showBriefToast(message: String) {
        val toast = Toast.makeText(this, message, Toast.LENGTH_SHORT)
        toast.show()
        Handler(Looper.getMainLooper()).postDelayed({ toast.cancel() }, 800)
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
    onOpenAccessibilitySettings: () -> Unit,
    onOpenAppDetailSettings: () -> Unit,
    onCopyToClipboard: (String) -> Unit,
    onSmartAction: (SmartActionType, String) -> Unit,
    onLock: () -> Unit,
    onRequestUnlock: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStatistics: () -> Unit,
    showPasswordFallback: Boolean,
    onPasswordFallbackDismiss: () -> Unit,
    onPasswordFallbackSubmit: (String) -> Unit
) {
    val entries by (viewModel?.entries?.collectAsState()
        ?: remember { mutableStateOf(emptyList()) })
    val searchQuery by (viewModel?.searchQuery?.collectAsState()
        ?: remember { mutableStateOf("") })
    val selectedContentType by (viewModel?.selectedContentType?.collectAsState()
        ?: remember { mutableStateOf(null) })
    val contentTypeCounts by (viewModel?.contentTypeCounts?.collectAsState()
        ?: remember { mutableStateOf(emptyMap()) })
    val sortOrder by (viewModel?.sortOrder?.collectAsState()
        ?: remember { mutableStateOf(SortOrder.NEWEST) })
    val selectionMode by (viewModel?.selectionMode?.collectAsState()
        ?: remember { mutableStateOf(false) })
    val selectedIds by (viewModel?.selectedIds?.collectAsState()
        ?: remember { mutableStateOf(emptySet()) })
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var selectedEntry by remember { mutableStateOf<ClipEntry?>(null) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val deletedMsg = stringResource(R.string.entry_deleted)
    val undoLabel = stringResource(R.string.undo)
    val pinnedMsg = stringResource(R.string.action_pinned)
    val unpinnedMsg = stringResource(R.string.action_unpinned)

    // Back handler for selection mode
    BackHandler(enabled = selectionMode) {
        viewModel?.exitSelectionMode()
    }

    Scaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            if (selectionMode) {
                // Selection mode top bar
                TopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.batch_selected_count, selectedIds.size),
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel?.exitSelectionMode() }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel?.selectAll() }) {
                            Icon(Icons.Default.SelectAll, contentDescription = stringResource(R.string.batch_select_all))
                        }
                        IconButton(onClick = { viewModel?.pinSelected() }) {
                            Icon(Icons.Filled.PushPin, contentDescription = stringResource(R.string.action_pinned))
                        }
                        IconButton(onClick = { viewModel?.deleteSelected() }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_button))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            Text(
                                when {
                                    appLockEnabled && !isUnlocked -> stringResource(R.string.status_locked)
                                    accessibilityEnabled -> stringResource(R.string.status_active, entries.size)
                                    else -> stringResource(R.string.status_inactive)
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
                                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
                            }
                            // Sort button with indicator
                            Box {
                                IconButton(onClick = { showSortMenu = true }) {
                                    if (sortOrder != SortOrder.NEWEST) {
                                        BadgedBox(badge = {
                                            Badge(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(8.dp)
                                            )
                                        }) {
                                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null)
                                        }
                                    } else {
                                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null)
                                    }
                                }
                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false }
                                ) {
                                    SortOrder.entries.forEach { order ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(stringResource(order.labelRes))
                                                    if (sortOrder == order) {
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text("\u2713", color = MaterialTheme.colorScheme.primary)
                                                    }
                                                }
                                            },
                                            onClick = {
                                                viewModel?.setSortOrder(order)
                                                showSortMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = { showDeleteAllDialog = true }) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.delete_all))
                            }
                            if (appLockEnabled) {
                                IconButton(onClick = onLock) {
                                    Icon(Icons.Default.Lock, contentDescription = stringResource(R.string.lock))
                                }
                            }
                        } else if (appLockEnabled) {
                            IconButton(onClick = onRequestUnlock) {
                                Icon(Icons.Default.LockOpen, contentDescription = stringResource(R.string.unlock))
                            }
                        }
                        Box {
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = null)
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_statistics)) },
                                    onClick = {
                                        showOverflowMenu = false
                                        onOpenStatistics()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.guide)) },
                                    onClick = {
                                        showOverflowMenu = false
                                        showGuide = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.settings)) },
                                    onClick = {
                                        showOverflowMenu = false
                                        onOpenSettings()
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Setup banner if accessibility not enabled (only show when unlocked)
            if (isUnlocked && !accessibilityEnabled) {
                SetupBanner(
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    onOpenAppDetailSettings = onOpenAppDetailSettings
                )
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
                        placeholder = { Text(stringResource(R.string.search_placeholder)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        }
                    )
                }

                // Content Type Filter Chips
                if (contentTypeCounts.isNotEmpty()) {
                    ContentTypeFilterBar(
                        counts = contentTypeCounts,
                        selectedType = selectedContentType,
                        onSelectType = { viewModel?.setContentTypeFilter(it) }
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
                        Text("\uD83D\uDCCB", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.no_clips_yet),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            if (accessibilityEnabled)
                                stringResource(R.string.hint_copy_something)
                            else
                                stringResource(R.string.hint_activate_accessibility),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
            } else {
                val favorites = entries.filter { it.pinned }
                val nonFavorites = entries.filter { !it.pinned }
                var favoritesExpanded by remember { mutableStateOf(true) }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Favorites Accordion (hidden in selection mode)
                    if (favorites.isNotEmpty() && !selectionMode) {
                        item(key = "favorites_header") {
                            val chevronRotation by animateFloatAsState(
                                targetValue = if (favoritesExpanded) 0f else -90f,
                                label = "chevron"
                            )
                            Card(
                                onClick = { favoritesExpanded = !favoritesExpanded },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.Star,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        stringResource(R.string.favorites_count, favorites.size),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    Icon(
                                        Icons.Default.KeyboardArrowDown,
                                        contentDescription = if (favoritesExpanded) stringResource(R.string.collapse) else stringResource(R.string.expand),
                                        modifier = Modifier
                                            .size(24.dp)
                                            .rotate(chevronRotation),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }

                        if (favoritesExpanded) {
                            items(items = favorites, key = { it.id }) { entry ->
                                SwipeableClipContainer(
                                    entry = entry,
                                    onDelete = {
                                        viewModel?.delete(entry)
                                        scope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message = deletedMsg,
                                                actionLabel = undoLabel,
                                                duration = SnackbarDuration.Short
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                viewModel?.reInsert(entry)
                                            }
                                        }
                                    },
                                    onTogglePin = {
                                        viewModel?.togglePin(entry)
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                message = if (entry.pinned) unpinnedMsg else pinnedMsg,
                                                duration = SnackbarDuration.Short
                                            )
                                        }
                                    }
                                ) {
                                    ClipEntryCard(
                                        entry = entry,
                                        onCopy = { onCopyToClipboard(entry.content) },
                                        onLongPress = { selectedEntry = entry },
                                        onToggleFavorite = { viewModel?.togglePin(entry) }
                                    )
                                }
                            }
                        }
                    }

                    // All entries in selection mode, or non-favorites in normal mode
                    val displayEntries = if (selectionMode) entries else nonFavorites
                    items(items = displayEntries, key = { it.id }) { entry ->
                        if (selectionMode) {
                            SelectableClipEntryCard(
                                entry = entry,
                                isSelected = entry.id in selectedIds,
                                onToggleSelection = { viewModel?.toggleSelection(entry.id) }
                            )
                        } else {
                            SwipeableClipContainer(
                                entry = entry,
                                onDelete = {
                                    viewModel?.delete(entry)
                                    scope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = deletedMsg,
                                            actionLabel = undoLabel,
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            viewModel?.reInsert(entry)
                                        }
                                    }
                                },
                                onTogglePin = {
                                    viewModel?.togglePin(entry)
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = if (entry.pinned) unpinnedMsg else pinnedMsg,
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                }
                            ) {
                                ClipEntryCard(
                                    entry = entry,
                                    onCopy = { onCopyToClipboard(entry.content) },
                                    onLongPress = { viewModel?.enterSelectionMode(entry.id) },
                                    onToggleFavorite = { viewModel?.togglePin(entry) }
                                )
                            }
                        }
                    }

                    item(key = "footer") {
                        Text(
                            stringResource(R.string.copyright_footer),
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
            title = { Text(stringResource(R.string.delete_all_title)) },
            text = { Text(stringResource(R.string.delete_all_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel?.deleteAllUnpinned()
                    showDeleteAllDialog = false
                }) {
                    Text(stringResource(R.string.delete_button), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text(stringResource(R.string.cancel))
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

    if (showGuide) {
        GuideDialog(onDismiss = { showGuide = false })
    }

    selectedEntry?.let { entry ->
        SmartActionBottomSheet(
            entry = entry,
            onDismiss = { selectedEntry = null },
            onAction = onSmartAction
        )
    }
}

// --- Content Type Filter Bar ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentTypeFilterBar(
    counts: Map<ContentType, Int>,
    selectedType: ContentType?,
    onSelectType: (ContentType?) -> Unit
) {
    val sortedTypes = remember(counts) {
        counts.entries
            .filter { it.value > 0 }
            .sortedByDescending { it.value }
            .map { it.key }
    }
    val totalCount = remember(counts) { counts.values.sum() }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp)
    ) {
        // "All" chip
        item(key = "all") {
            FilterChip(
                selected = selectedType == null,
                onClick = { onSelectType(null) },
                label = {
                    Text(
                        stringResource(R.string.filter_all) + " ($totalCount)",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            )
        }

        items(items = sortedTypes, key = { it.name }) { type ->
            val count = counts[type] ?: 0
            FilterChip(
                selected = selectedType == type,
                onClick = {
                    onSelectType(if (selectedType == type) null else type)
                },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = type.icon,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (selectedType == type) MaterialTheme.colorScheme.onSecondaryContainer else type.color
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            stringResource(type.labelRes) + " ($count)",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = type.color.copy(alpha = 0.2f),
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}

// --- Guide Dialog ---

@Composable
fun GuideDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.guide_title), fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                GuideSection(
                    title = stringResource(R.string.guide_setup_title),
                    text = stringResource(R.string.guide_setup_text)
                )
                GuideSection(
                    title = stringResource(R.string.guide_copy_title),
                    text = stringResource(R.string.guide_copy_text)
                )
                GuideSection(
                    title = stringResource(R.string.guide_favorites_title),
                    text = stringResource(R.string.guide_favorites_text)
                )
                GuideSection(
                    title = stringResource(R.string.guide_delete_title),
                    text = stringResource(R.string.guide_delete_text)
                )
                GuideSection(
                    title = stringResource(R.string.guide_filter_title),
                    text = stringResource(R.string.guide_filter_text)
                )
                GuideSection(
                    title = stringResource(R.string.guide_search_title),
                    text = stringResource(R.string.guide_search_text)
                )
                GuideSection(
                    title = stringResource(R.string.guide_applock_title),
                    text = stringResource(R.string.guide_applock_text)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.guide_understood))
            }
        }
    )
}

@Composable
private fun GuideSection(title: String, text: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
                stringResource(R.string.app_locked),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.unlock_to_see_clips),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = onRequestUnlock) {
                Text(stringResource(R.string.unlock))
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
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = formatTimestamp(LocalContext.current, entry.timestamp),
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
        title = { Text(stringResource(R.string.enter_password)) },
        text = {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.password_label)) },
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
                Text(stringResource(R.string.unlock))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

// --- Setup Banner ---

@Composable
fun SetupBanner(
    onOpenAccessibilitySettings: () -> Unit,
    onOpenAppDetailSettings: () -> Unit
) {
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.setup_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )

            // Step 1: Restricted settings (Android 13+, sideloaded APKs)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Text(
                    stringResource(R.string.setup_step1_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    stringResource(R.string.setup_step1_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                )
                OutlinedButton(
                    onClick = onOpenAppDetailSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.setup_open_app_info))
                }

                Text(
                    stringResource(R.string.setup_step2_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }

            Text(
                stringResource(R.string.setup_accessibility_text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
            )
            Button(
                onClick = onOpenAccessibilitySettings,
                modifier = Modifier.fillMaxWidth(),
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
                Text(stringResource(R.string.setup_open_accessibility))
            }
        }
    }
}

@Composable
private fun ContentTypeIcon(contentType: ContentType, modifier: Modifier = Modifier) {
    Icon(
        imageVector = contentType.icon,
        contentDescription = stringResource(contentType.labelRes),
        modifier = modifier.size(18.dp),
        tint = contentType.color
    )
}

// --- Clip Entry Card (Unlocked) ---

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClipEntryCard(
    entry: ClipEntry,
    onCopy: () -> Unit,
    onLongPress: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val contentType = remember(entry.content) { detectContentType(entry.content) }
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .background(
                if (entry.pinned) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
                RoundedCornerShape(12.dp)
            )
            .animateContentSize()
            .combinedClickable(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onCopy()
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress()
                }
            )
            .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ContentTypeIcon(
            contentType = contentType,
            modifier = Modifier.padding(end = 10.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatTimestamp(LocalContext.current, entry.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = entry.content,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (contentType == ContentType.IBAN) {
                val isValid = remember(entry.content) { isValidIban(entry.content) }
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isValid) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = if (isValid) Color(0xFF4CAF50) else Color(0xFFF44336)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isValid) stringResource(R.string.iban_valid) else stringResource(R.string.iban_invalid),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isValid) Color(0xFF4CAF50) else Color(0xFFF44336)
                    )
                }
            }
        }
        val chevronRotation by animateFloatAsState(
            targetValue = if (expanded) 180f else 0f,
            label = "expand_chevron"
        )
        IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) stringResource(R.string.collapse) else stringResource(R.string.expand),
                modifier = Modifier.size(18.dp).rotate(chevronRotation),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
        IconButton(onClick = onToggleFavorite, modifier = Modifier.size(40.dp)) {
            Icon(
                if (entry.pinned) Icons.Filled.Star else Icons.Outlined.Star,
                contentDescription = stringResource(R.string.favorite),
                modifier = Modifier.size(20.dp),
                tint = if (entry.pinned)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )
        }
    }
}

// --- Selectable Clip Entry Card (Batch Mode) ---

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SelectableClipEntryCard(
    entry: ClipEntry,
    isSelected: Boolean,
    onToggleSelection: () -> Unit
) {
    val contentType = remember(entry.content) { detectContentType(entry.content) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .background(
                if (isSelected)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else if (entry.pinned)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                RoundedCornerShape(12.dp)
            )
            .combinedClickable(
                onClick = onToggleSelection
            )
            .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggleSelection() },
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatTimestamp(LocalContext.current, entry.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                if (entry.pinned) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = entry.content,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (contentType == ContentType.IBAN) {
                val isValid = remember(entry.content) { isValidIban(entry.content) }
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isValid) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = if (isValid) Color(0xFF4CAF50) else Color(0xFFF44336)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isValid) stringResource(R.string.iban_valid) else stringResource(R.string.iban_invalid),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isValid) Color(0xFF4CAF50) else Color(0xFFF44336)
                    )
                }
            }
        }
        ContentTypeIcon(
            contentType = contentType,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

// --- Swipeable Clip Container (Delete left, Pin right) ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableClipContainer(
    entry: ClipEntry,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit,
    content: @Composable () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var isDeleted by remember { mutableStateOf(false) }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> {
                    if (!isDeleted) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        isDeleted = true
                        true
                    } else false
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onTogglePin()
                    false // Don't dismiss — card stays visible
                }
                else -> false
            }
        },
        positionalThreshold = { it * 0.4f }
    )

    LaunchedEffect(isDeleted) {
        if (isDeleted) {
            onDelete()
        }
    }

    if (!isDeleted) {
        SwipeToDismissBox(
            state = dismissState,
            enableDismissFromStartToEnd = true,
            enableDismissFromEndToStart = true,
            backgroundContent = {
                val direction = dismissState.dismissDirection

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                        .background(
                            when (direction) {
                                SwipeToDismissBoxValue.StartToEnd -> {
                                    if (entry.pinned) Color(0xFF9E9E9E) else Color(0xFFFFB300)
                                }
                                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error
                                else -> Color.Transparent
                            },
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 20.dp),
                    contentAlignment = when (direction) {
                        SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                        else -> Alignment.CenterEnd
                    }
                ) {
                    when (direction) {
                        SwipeToDismissBoxValue.StartToEnd -> {
                            Icon(
                                if (entry.pinned) Icons.Outlined.PushPin else Icons.Filled.PushPin,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }
                        SwipeToDismissBoxValue.EndToStart -> {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete_button),
                                tint = MaterialTheme.colorScheme.onError
                            )
                        }
                        else -> {}
                    }
                }
            }
        ) {
            content()
        }
    }
}

// --- Helpers ---

private fun formatTimestamp(context: Context, timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> context.getString(R.string.time_just_now)
        diff < 3_600_000 -> context.getString(R.string.time_minutes_ago, diff / 60_000)
        diff < 86_400_000 -> context.getString(R.string.time_hours_ago, diff / 3_600_000)
        else -> {
            val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}
