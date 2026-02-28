package io.celox.clipvault.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brightness2
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import io.celox.clipvault.ClipVaultApp
import io.celox.clipvault.R
import io.celox.clipvault.data.ClipEntry
import io.celox.clipvault.ui.about.AboutActivity
import io.celox.clipvault.ui.theme.ClipVaultTheme
import io.celox.clipvault.util.BackupCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : FragmentActivity() {

    private var onBiometricSuccess: (() -> Unit)? = null

    private fun showBiometricForDisable(onSuccess: () -> Unit) {
        val biometricManager = BiometricManager.from(this)
        val canAuthenticate = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )

        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(this, getString(R.string.action_failed), Toast.LENGTH_SHORT).show()
            return
        }

        onBiometricSuccess = onSuccess

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_title))
            .setSubtitle(getString(R.string.biometric_disable_subtitle))
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
                    onBiometricSuccess?.invoke()
                    onBiometricSuccess = null
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onBiometricSuccess = null
                }
            }
        )

        biometricPrompt.authenticate(promptInfo)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val app = application as ClipVaultApp
            val ksm = app.keyStoreManager
            var appLockEnabled by remember { mutableStateOf(ksm.isAppLockEnabled()) }
            var biometricEnabled by remember { mutableStateOf(ksm.isAppLockBiometricEnabled()) }
            var passwordGenerated by remember { mutableStateOf(ksm.isAppLockPasswordGenerated()) }
            var amoledMode by remember { mutableStateOf(ksm.isAmoledMode()) }
            var showAppLockDialog by remember { mutableStateOf(false) }
            var showDisableAppLockDialog by remember { mutableStateOf(false) }
            var showChangePasswordDialog by remember { mutableStateOf(false) }
            var autoCleanupDays by remember { mutableStateOf(ksm.getAutoCleanupDays()) }
            var showAutoCleanupDialog by remember { mutableStateOf(false) }
            var showExportPasswordDialog by remember { mutableStateOf(false) }
            var showImportPasswordDialog by remember { mutableStateOf(false) }
            var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
            val scope = rememberCoroutineScope()

            val versionName = try {
                packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
            } catch (_: Exception) { "?" }

            val exportLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/octet-stream")
            ) { uri ->
                if (uri != null) {
                    showExportPasswordDialog = true
                    pendingImportUri = uri // reuse for export URI
                }
            }

            val importLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) {
                    pendingImportUri = uri
                    showImportPasswordDialog = true
                }
            }

            ClipVaultTheme(amoledMode = amoledMode) {
                SettingsScreen(
                    appLockEnabled = appLockEnabled,
                    biometricEnabled = biometricEnabled,
                    passwordGenerated = passwordGenerated,
                    amoledMode = amoledMode,
                    autoCleanupDays = autoCleanupDays,
                    versionName = versionName,
                    onBack = { finish() },
                    onToggleAppLock = { enabled ->
                        if (enabled) {
                            showAppLockDialog = true
                        } else {
                            if (passwordGenerated) {
                                // Fingerprint mode: require biometric auth to disable
                                showBiometricForDisable {
                                    ksm.clearAppLock()
                                    appLockEnabled = false
                                    passwordGenerated = false
                                    biometricEnabled = ksm.isAppLockBiometricEnabled()
                                }
                            } else {
                                // Password mode: require password to disable
                                showDisableAppLockDialog = true
                            }
                        }
                    },
                    onChangePassword = { showChangePasswordDialog = true },
                    onToggleBiometric = { enabled ->
                        if (!enabled) {
                            // Require biometric auth to disable biometric unlock
                            showBiometricForDisable {
                                ksm.setAppLockBiometricEnabled(false)
                                biometricEnabled = false
                            }
                        } else {
                            ksm.setAppLockBiometricEnabled(true)
                            biometricEnabled = true
                        }
                    },
                    onToggleAmoled = { enabled ->
                        ksm.setAmoledMode(enabled)
                        amoledMode = enabled
                    },
                    onAutoCleanupClick = { showAutoCleanupDialog = true },
                    onOpenAbout = {
                        startActivity(Intent(this, AboutActivity::class.java))
                    },
                    onExport = {
                        val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                        exportLauncher.launch("clipvault_backup_$date.cvbk")
                    },
                    onImport = {
                        importLauncher.launch(arrayOf("*/*"))
                    }
                )

                if (showAutoCleanupDialog) {
                    AutoCleanupDialog(
                        currentDays = autoCleanupDays,
                        onDismiss = { showAutoCleanupDialog = false },
                        onConfirm = { days ->
                            showAutoCleanupDialog = false
                            ksm.setAutoCleanupDays(days)
                            autoCleanupDays = days
                        }
                    )
                }

                if (showAppLockDialog) {
                    AppLockSetupDialog(
                        onDismiss = { showAppLockDialog = false },
                        onConfirmGenerated = {
                            showAppLockDialog = false
                            val generatedPw = generateSecurePassword()
                            ksm.storeAppLockPassword(generatedPw)
                            ksm.setAppLockPasswordGenerated(true)
                            ksm.setAppLockBiometricEnabled(true)
                            ksm.setAppLockEnabled(true)
                            appLockEnabled = true
                            passwordGenerated = true
                            biometricEnabled = true
                        },
                        onConfirmManual = { password ->
                            showAppLockDialog = false
                            ksm.storeAppLockPassword(password)
                            ksm.setAppLockPasswordGenerated(false)
                            ksm.setAppLockEnabled(true)
                            appLockEnabled = true
                            passwordGenerated = false
                        }
                    )
                }

                if (showDisableAppLockDialog) {
                    DisableWithPasswordDialog(
                        onDismiss = { showDisableAppLockDialog = false },
                        onConfirm = { password ->
                            val stored = ksm.retrieveAppLockPassword()
                            if (stored == password) {
                                showDisableAppLockDialog = false
                                ksm.clearAppLock()
                                appLockEnabled = false
                                passwordGenerated = false
                                biometricEnabled = ksm.isAppLockBiometricEnabled()
                            } else {
                                Toast.makeText(this@SettingsActivity, getString(R.string.wrong_password), Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                if (showChangePasswordDialog) {
                    ChangePasswordDialog(
                        onDismiss = { showChangePasswordDialog = false },
                        onConfirm = { oldPw, newPw ->
                            showChangePasswordDialog = false
                            val stored = ksm.retrieveAppLockPassword()
                            if (stored == oldPw) {
                                ksm.storeAppLockPassword(newPw)
                            } else {
                                Toast.makeText(this, getString(R.string.wrong_password), Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                if (showExportPasswordDialog) {
                    BackupPasswordDialog(
                        isExport = true,
                        onDismiss = {
                            showExportPasswordDialog = false
                            pendingImportUri = null
                        },
                        onConfirm = { password ->
                            showExportPasswordDialog = false
                            val uri = pendingImportUri ?: return@BackupPasswordDialog
                            pendingImportUri = null
                            scope.launch {
                                try {
                                    val entries = withContext(Dispatchers.IO) {
                                        app.repository?.exportAll() ?: emptyList()
                                    }
                                    val json = buildExportJson(entries)
                                    val encrypted = withContext(Dispatchers.Default) {
                                        BackupCrypto.encrypt(json.toByteArray(Charsets.UTF_8), password)
                                    }
                                    withContext(Dispatchers.IO) {
                                        contentResolver.openOutputStream(uri)?.use { it.write(encrypted) }
                                    }
                                    Toast.makeText(this@SettingsActivity, getString(R.string.backup_export_success), Toast.LENGTH_SHORT).show()
                                } catch (_: Exception) {
                                    Toast.makeText(this@SettingsActivity, getString(R.string.backup_invalid_file), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }

                if (showImportPasswordDialog) {
                    BackupPasswordDialog(
                        isExport = false,
                        onDismiss = {
                            showImportPasswordDialog = false
                            pendingImportUri = null
                        },
                        onConfirm = { password ->
                            showImportPasswordDialog = false
                            val uri = pendingImportUri ?: return@BackupPasswordDialog
                            pendingImportUri = null
                            scope.launch {
                                try {
                                    val data = withContext(Dispatchers.IO) {
                                        contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                            ?: throw IllegalArgumentException("Cannot read file")
                                    }
                                    val jsonBytes = withContext(Dispatchers.Default) {
                                        BackupCrypto.decrypt(data, password)
                                    }
                                    val entriesToImport = parseImportJson(String(jsonBytes, Charsets.UTF_8))
                                    val imported = withContext(Dispatchers.IO) {
                                        app.repository?.importEntries(entriesToImport) ?: 0
                                    }
                                    Toast.makeText(
                                        this@SettingsActivity,
                                        getString(R.string.backup_import_success, imported),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } catch (e: javax.crypto.AEADBadTagException) {
                                    Toast.makeText(this@SettingsActivity, getString(R.string.backup_wrong_password), Toast.LENGTH_SHORT).show()
                                } catch (e: java.security.GeneralSecurityException) {
                                    Toast.makeText(this@SettingsActivity, getString(R.string.backup_wrong_password), Toast.LENGTH_SHORT).show()
                                } catch (_: Exception) {
                                    Toast.makeText(this@SettingsActivity, getString(R.string.backup_invalid_file), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    private fun buildExportJson(entries: List<ClipEntry>): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("exportedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()))
        val arr = JSONArray()
        for (entry in entries) {
            val obj = JSONObject()
            obj.put("content", entry.content)
            obj.put("timestamp", entry.timestamp)
            obj.put("pinned", entry.pinned)
            arr.put(obj)
        }
        root.put("entries", arr)
        return root.toString()
    }

    private fun parseImportJson(json: String): List<ClipEntry> {
        val root = JSONObject(json)
        val arr = root.getJSONArray("entries")
        val result = mutableListOf<ClipEntry>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            result.add(
                ClipEntry(
                    content = obj.getString("content"),
                    timestamp = obj.getLong("timestamp"),
                    pinned = obj.optBoolean("pinned", false)
                )
            )
        }
        return result
    }

    private fun generateSecurePassword(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%&*"
        val random = SecureRandom()
        return (1..32).map { chars[random.nextInt(chars.length)] }.joinToString("")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    appLockEnabled: Boolean,
    biometricEnabled: Boolean,
    passwordGenerated: Boolean,
    amoledMode: Boolean,
    autoCleanupDays: Int,
    versionName: String,
    onBack: () -> Unit,
    onToggleAppLock: (Boolean) -> Unit,
    onChangePassword: () -> Unit,
    onToggleBiometric: (Boolean) -> Unit,
    onToggleAmoled: (Boolean) -> Unit,
    onAutoCleanupClick: () -> Unit,
    onOpenAbout: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    Scaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
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
                .verticalScroll(rememberScrollState())
        ) {
            // --- Security Section ---
            SectionHeader(stringResource(R.string.section_security))

            // DB encryption info (always on, not toggleable)
            SettingsInfoItem(
                icon = Icons.Default.Security,
                title = stringResource(R.string.db_encryption_title),
                subtitle = stringResource(R.string.db_encryption_subtitle)
            )

            // App lock toggle
            SettingsToggleItem(
                icon = Icons.Default.Lock,
                title = stringResource(R.string.app_lock_title),
                subtitle = when {
                    appLockEnabled && passwordGenerated -> stringResource(R.string.app_lock_fingerprint)
                    appLockEnabled -> stringResource(R.string.app_lock_password)
                    else -> stringResource(R.string.app_lock_disabled)
                },
                checked = appLockEnabled,
                onCheckedChange = onToggleAppLock
            )

            if (appLockEnabled) {
                // "Passwort ändern" only for manual passwords
                if (!passwordGenerated) {
                    SettingsClickItem(
                        icon = Icons.Default.Key,
                        title = stringResource(R.string.change_password_title),
                        subtitle = stringResource(R.string.change_password_subtitle),
                        onClick = onChangePassword
                    )
                }

                // Biometric toggle
                if (passwordGenerated) {
                    SettingsInfoItem(
                        icon = Icons.Default.Fingerprint,
                        title = stringResource(R.string.biometric_lock_title),
                        subtitle = stringResource(R.string.biometric_always_active)
                    )
                } else {
                    SettingsToggleItem(
                        icon = Icons.Default.Fingerprint,
                        title = stringResource(R.string.biometric_lock_title),
                        subtitle = if (biometricEnabled) stringResource(R.string.biometric_unlock_subtitle) else stringResource(R.string.app_lock_disabled),
                        checked = biometricEnabled,
                        onCheckedChange = onToggleBiometric
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            // --- Display Section ---
            SectionHeader(stringResource(R.string.section_display))

            SettingsToggleItem(
                icon = Icons.Default.Brightness2,
                title = stringResource(R.string.amoled_mode_title),
                subtitle = if (amoledMode) stringResource(R.string.amoled_mode_enabled) else stringResource(R.string.amoled_mode_disabled),
                checked = amoledMode,
                onCheckedChange = onToggleAmoled
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            // --- Backup Section ---
            SectionHeader(stringResource(R.string.section_backup))

            SettingsClickItem(
                icon = Icons.Default.CloudUpload,
                title = stringResource(R.string.backup_export),
                subtitle = stringResource(R.string.backup_export_subtitle),
                onClick = onExport
            )

            SettingsClickItem(
                icon = Icons.Default.CloudDownload,
                title = stringResource(R.string.backup_import),
                subtitle = stringResource(R.string.backup_import_subtitle),
                onClick = onImport
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            // --- Maintenance Section ---
            SectionHeader(stringResource(R.string.section_maintenance))

            SettingsClickItem(
                icon = Icons.Default.CleaningServices,
                title = stringResource(R.string.auto_cleanup_title),
                subtitle = if (autoCleanupDays == 0)
                    stringResource(R.string.auto_cleanup_subtitle_disabled)
                else
                    stringResource(R.string.auto_cleanup_subtitle_active, autoCleanupDays),
                onClick = onAutoCleanupClick
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            // --- Info Section ---
            SectionHeader(stringResource(R.string.section_info))

            SettingsClickItem(
                icon = Icons.Default.Info,
                title = stringResource(R.string.about_clipvault),
                subtitle = stringResource(R.string.version_format, versionName),
                onClick = onOpenAbout
            )
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SettingsClickItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
    }
}

@Composable
fun SettingsInfoItem(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// --- Backup Password Dialog ---

@Composable
fun BackupPasswordDialog(
    isExport: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val mismatchMsg = stringResource(R.string.backup_password_mismatch)
    val emptyPasswordMsg = stringResource(R.string.error_enter_password)
    val okLabel = stringResource(R.string.ok)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_password_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = { Text(stringResource(R.string.backup_password_hint)) },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                if (isExport) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it; error = null },
                        label = { Text(stringResource(R.string.backup_password_confirm)) },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    password.isEmpty() -> error = emptyPasswordMsg
                    isExport && password != confirmPassword -> error = mismatchMsg
                    else -> onConfirm(password)
                }
            }) {
                Text(okLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

// --- App Lock Setup Dialog ---

@Composable
fun AppLockSetupDialog(
    onDismiss: () -> Unit,
    onConfirmGenerated: () -> Unit,
    onConfirmManual: (String) -> Unit
) {
    var showManualFields by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.enable_applock_title)) },
        text = {
            Column {
                if (!showManualFields) {
                    Text(
                        stringResource(R.string.choose_protection),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Option 1: Generated + Fingerprint
                    Button(
                        onClick = onConfirmGenerated,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.fingerprint_option))
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        stringResource(R.string.fingerprint_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Option 2: Manual password
                    OutlinedButton(
                        onClick = { showManualFields = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.custom_password_option))
                    }
                } else {
                    Text(
                        stringResource(R.string.set_password_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; error = null },
                        label = { Text(stringResource(R.string.password_label)) },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it; error = null },
                        label = { Text(stringResource(R.string.confirm_password_label)) },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    if (error != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            if (showManualFields) {
                val errorMinLength = stringResource(R.string.error_min_length)
                val errorMismatch = stringResource(R.string.error_passwords_mismatch)
                TextButton(onClick = {
                    when {
                        password.length < 4 -> error = errorMinLength
                        password != confirmPassword -> error = errorMismatch
                        else -> onConfirmManual(password)
                    }
                }) {
                    Text(stringResource(R.string.activate_button))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (showManualFields) {
                    showManualFields = false
                    password = ""
                    confirmPassword = ""
                    error = null
                } else {
                    onDismiss()
                }
            }) {
                Text(if (showManualFields) stringResource(R.string.back) else stringResource(R.string.cancel))
            }
        }
    )
}

// --- Change Password Dialog ---

@Composable
fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.change_password_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = oldPassword,
                    onValueChange = { oldPassword = it; error = null },
                    label = { Text(stringResource(R.string.current_password_label)) },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it; error = null },
                    label = { Text(stringResource(R.string.new_password_label)) },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; error = null },
                    label = { Text(stringResource(R.string.confirm_new_password_label)) },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            val errorEnterCurrent = stringResource(R.string.error_enter_current_password)
            val errorMinLength = stringResource(R.string.error_min_length)
            val errorMismatch = stringResource(R.string.error_passwords_mismatch)
            TextButton(onClick = {
                when {
                    oldPassword.isBlank() -> error = errorEnterCurrent
                    newPassword.length < 4 -> error = errorMinLength
                    newPassword != confirmPassword -> error = errorMismatch
                    else -> onConfirm(oldPassword, newPassword)
                }
            }) {
                Text(stringResource(R.string.change_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

// --- Disable App Lock with Password Dialog ---

@Composable
fun DisableWithPasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.disable_applock_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.disable_applock_enter_password),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.password_label)) },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (password.isNotEmpty()) onConfirm(password) },
                enabled = password.isNotEmpty()
            ) {
                Text(stringResource(R.string.disable_button), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

// --- Auto-Cleanup Dialog ---

@Composable
fun AutoCleanupDialog(
    currentDays: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val options = listOf(0, 7, 30, 90, 180, 365)
    var selected by remember { mutableStateOf(currentDays) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.auto_cleanup_dialog_title)) },
        text = {
            Column {
                options.forEach { days ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = days }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected == days,
                            onClick = { selected = days }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (days == 0) stringResource(R.string.auto_cleanup_disabled)
                            else stringResource(R.string.auto_cleanup_days, days),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.auto_cleanup_favorites_safe),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
