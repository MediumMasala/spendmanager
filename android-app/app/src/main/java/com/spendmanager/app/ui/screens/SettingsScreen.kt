package com.spendmanager.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendmanager.app.service.TransactionNotificationListener
import com.spendmanager.app.ui.theme.*
import com.spendmanager.app.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = White,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.headlineMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = Charcoal
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = White,
                    titleContentColor = Charcoal
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(White)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Privacy Section
            SettingsSection(title = "Privacy") {
                SettingsToggleItem(
                    icon = Icons.Outlined.Cloud,
                    title = "Cloud AI Parsing",
                    subtitle = if (uiState.consent.cloudAiEnabled)
                        "Transactions are parsed by AI"
                    else
                        "Local storage only",
                    checked = uiState.consent.cloudAiEnabled,
                    onCheckedChange = { viewModel.setCloudAiEnabled(it) }
                )

                if (uiState.consent.cloudAiEnabled) {
                    SettingsToggleItem(
                        icon = Icons.Outlined.DataObject,
                        title = "Upload Raw Messages",
                        subtitle = "Send unredacted text for better accuracy",
                        checked = uiState.consent.uploadRawEnabled,
                        onCheckedChange = { viewModel.setUploadRawEnabled(it) }
                    )

                    SettingsToggleItem(
                        icon = Icons.Outlined.Chat,
                        title = "WhatsApp Summaries",
                        subtitle = "Receive weekly spending summaries",
                        checked = uiState.consent.whatsappEnabled,
                        onCheckedChange = { viewModel.setWhatsappEnabled(it) }
                    )
                }
            }

            // Notifications Section
            SettingsSection(title = "Notifications") {
                SettingsClickableItem(
                    icon = Icons.Outlined.Notifications,
                    title = "Notification Access",
                    subtitle = if (TransactionNotificationListener.isEnabled(context))
                        "Enabled"
                    else
                        "Tap to enable",
                    showBadge = !TransactionNotificationListener.isEnabled(context),
                    onClick = {
                        context.startActivity(TransactionNotificationListener.getSettingsIntent())
                    }
                )

                SettingsClickableItem(
                    icon = Icons.Outlined.Apps,
                    title = "Notification Sources",
                    subtitle = "Select apps to monitor",
                    onClick = { /* TODO: Navigate to app selection */ }
                )
            }

            // Data Section
            SettingsSection(title = "Data") {
                SettingsClickableItem(
                    icon = Icons.Outlined.Download,
                    title = "Export Data",
                    subtitle = "Download your transaction data",
                    onClick = { viewModel.exportData() }
                )

                SettingsClickableItem(
                    icon = Icons.Outlined.DeleteOutline,
                    title = "Delete All Data",
                    subtitle = "Remove all local and cloud data",
                    isDestructive = true,
                    onClick = { showDeleteDialog = true }
                )
            }

            // Account Section
            SettingsSection(title = "Account") {
                SettingsClickableItem(
                    icon = Icons.Outlined.Logout,
                    title = "Logout",
                    subtitle = "Sign out of your account",
                    onClick = { showLogoutDialog = true }
                )
            }

            // App Info
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = OffWhite
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Spend",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Charcoal
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Version 1.0.0",
                        style = MaterialTheme.typography.bodySmall,
                        color = CharcoalMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = White,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    "Delete All Data?",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Charcoal
                )
            },
            text = {
                Text(
                    "This will permanently delete all your transaction data from this device and our servers. This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CharcoalMuted
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAllData()
                        showDeleteDialog = false
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentRed,
                        contentColor = White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false }
                ) {
                    Text(
                        "Cancel",
                        color = CharcoalMuted
                    )
                }
            }
        )
    }

    // Logout confirmation dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = White,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    "Logout?",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Charcoal
                )
            },
            text = {
                Text(
                    "Your local data will be preserved.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CharcoalMuted
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Charcoal,
                        contentColor = White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Logout")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLogoutDialog = false }
                ) {
                    Text(
                        "Cancel",
                        color = CharcoalMuted
                    )
                }
            }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = CharcoalMuted,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = White,
            border = BorderStroke(1.dp, Gray200)
        ) {
            Column(
                modifier = Modifier.padding(4.dp),
                content = content
            )
        }
    }
}

@Composable
private fun SettingsToggleItem(
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
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(OffWhite),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Charcoal,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = Charcoal
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = CharcoalMuted
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = White,
                checkedTrackColor = Charcoal,
                uncheckedThumbColor = White,
                uncheckedTrackColor = Gray300,
                uncheckedBorderColor = Gray300
            )
        )
    }
}

@Composable
private fun SettingsClickableItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isDestructive: Boolean = false,
    showBadge: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (isDestructive) AccentRedLight else OffWhite),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isDestructive) AccentRed else Charcoal,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isDestructive) AccentRed else Charcoal
                )
                if (showBadge) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(AccentRed)
                    )
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = CharcoalMuted
            )
        }

        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = Gray400,
            modifier = Modifier.size(20.dp)
        )
    }
}
