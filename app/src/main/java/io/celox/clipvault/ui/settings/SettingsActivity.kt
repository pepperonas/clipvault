package io.celox.clipvault.ui.settings

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import io.celox.clipvault.ClipVaultApp
import io.celox.clipvault.ui.about.AboutActivity
import io.celox.clipvault.ui.license.LicenseActivity
import io.celox.clipvault.ui.theme.ClipVaultTheme

class SettingsActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ClipVaultTheme {
                val app = application as ClipVaultApp
                var encryptionEnabled by remember { mutableStateOf(app.isEncryptionEnabled) }
                var biometricEnabled by remember { mutableStateOf(app.keyStoreManager.isBiometricEnabled()) }
                var showEncryptionDialog by remember { mutableStateOf(false) }
                var showDisableEncryptionDialog by remember { mutableStateOf(false) }
                var showChangePasswordDialog by remember { mutableStateOf(false) }

                val versionName = try {
                    packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
                } catch (_: Exception) { "?" }

                SettingsScreen(
                    encryptionEnabled = encryptionEnabled,
                    biometricEnabled = biometricEnabled,
                    isLicenseActivated = app.licenseManager.isActivated(),
                    licenseEmail = app.licenseManager.getActivatedEmail(),
                    versionName = versionName,
                    onBack = { finish() },
                    onToggleEncryption = { enabled ->
                        if (enabled) {
                            showEncryptionDialog = true
                        } else {
                            showDisableEncryptionDialog = true
                        }
                    },
                    onChangePassword = { showChangePasswordDialog = true },
                    onToggleBiometric = { enabled ->
                        app.keyStoreManager.setBiometricEnabled(enabled)
                        biometricEnabled = enabled
                    },
                    onOpenLicense = {
                        startActivity(Intent(this, LicenseActivity::class.java))
                    },
                    onOpenAbout = {
                        startActivity(Intent(this, AboutActivity::class.java))
                    }
                )

                if (showEncryptionDialog) {
                    PasswordSetupDialog(
                        onDismiss = { showEncryptionDialog = false },
                        onConfirm = { password ->
                            showEncryptionDialog = false
                            if (app.enableEncryption(password)) {
                                encryptionEnabled = true
                                Toast.makeText(this, "Verschluesselung aktiviert", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this, "Fehler beim Aktivieren", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                if (showDisableEncryptionDialog) {
                    AlertDialog(
                        onDismissRequest = { showDisableEncryptionDialog = false },
                        title = { Text("Verschluesselung deaktivieren?") },
                        text = { Text("Die Datenbank wird entschluesselt. Deine Clips bleiben erhalten, sind aber nicht mehr verschluesselt.") },
                        confirmButton = {
                            TextButton(onClick = {
                                showDisableEncryptionDialog = false
                                if (app.disableEncryption()) {
                                    encryptionEnabled = false
                                    biometricEnabled = app.keyStoreManager.isBiometricEnabled()
                                    Toast.makeText(this, "Verschluesselung deaktiviert", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(this, "Fehler beim Deaktivieren", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Text("Deaktivieren", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDisableEncryptionDialog = false }) {
                                Text("Abbrechen")
                            }
                        }
                    )
                }

                if (showChangePasswordDialog) {
                    ChangePasswordDialog(
                        onDismiss = { showChangePasswordDialog = false },
                        onConfirm = { oldPw, newPw ->
                            showChangePasswordDialog = false
                            if (app.changePassword(oldPw, newPw)) {
                                Toast.makeText(this, "Passwort geaendert", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this, "Falsches Passwort", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh in case license was activated in LicenseActivity
        // Simple approach: recreate to re-read state
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    encryptionEnabled: Boolean,
    biometricEnabled: Boolean,
    isLicenseActivated: Boolean,
    licenseEmail: String?,
    versionName: String,
    onBack: () -> Unit,
    onToggleEncryption: (Boolean) -> Unit,
    onChangePassword: () -> Unit,
    onToggleBiometric: (Boolean) -> Unit,
    onOpenLicense: () -> Unit,
    onOpenAbout: () -> Unit
) {
    Scaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurueck")
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
            SectionHeader("Sicherheit")

            SettingsToggleItem(
                icon = Icons.Default.Lock,
                title = "Verschluesselung",
                subtitle = if (encryptionEnabled) "Datenbank ist verschluesselt" else "Datenbank ist nicht verschluesselt",
                checked = encryptionEnabled,
                onCheckedChange = onToggleEncryption
            )

            if (encryptionEnabled) {
                SettingsClickItem(
                    icon = Icons.Default.Key,
                    title = "Passwort aendern",
                    subtitle = "Verschluesselungs-Passwort aendern",
                    onClick = onChangePassword
                )

                SettingsToggleItem(
                    icon = Icons.Default.Fingerprint,
                    title = "Biometrische Sperre",
                    subtitle = if (biometricEnabled) "Fingerabdruck/Gesicht zum Entsperren" else "Deaktiviert",
                    checked = biometricEnabled,
                    onCheckedChange = onToggleBiometric
                )
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            // --- License Section ---
            SectionHeader("Lizenz")

            SettingsClickItem(
                icon = Icons.Default.Shield,
                title = if (isLicenseActivated) "Lizenz aktiv" else "Lizenz aktivieren",
                subtitle = if (isLicenseActivated) licenseEmail ?: "" else "Kostenlose Version (max. 10 Clips)",
                onClick = onOpenLicense
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            // --- Info Section ---
            SectionHeader("Info")

            SettingsClickItem(
                icon = Icons.Default.Info,
                title = "Ueber ClipVault",
                subtitle = "Version $versionName",
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
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
fun PasswordSetupDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Passwort festlegen") },
        text = {
            Column {
                Text(
                    "Lege ein Passwort fest, um die Datenbank zu verschluesseln.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = { Text("Passwort") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
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
                    label = { Text("Passwort bestaetigen") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    password.length < 4 -> error = "Mindestens 4 Zeichen"
                    password != confirmPassword -> error = "Passwoerter stimmen nicht ueberein"
                    else -> onConfirm(password)
                }
            }) {
                Text("Aktivieren")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}

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
        title = { Text("Passwort aendern") },
        text = {
            Column {
                OutlinedTextField(
                    value = oldPassword,
                    onValueChange = { oldPassword = it; error = null },
                    label = { Text("Aktuelles Passwort") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
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
                    value = newPassword,
                    onValueChange = { newPassword = it; error = null },
                    label = { Text("Neues Passwort") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; error = null },
                    label = { Text("Neues Passwort bestaetigen") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    oldPassword.isBlank() -> error = "Aktuelles Passwort eingeben"
                    newPassword.length < 4 -> error = "Mindestens 4 Zeichen"
                    newPassword != confirmPassword -> error = "Passwoerter stimmen nicht ueberein"
                    else -> onConfirm(oldPassword, newPassword)
                }
            }) {
                Text("Aendern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}
