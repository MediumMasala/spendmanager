package com.spendmanager.app.ui.screens

import android.app.Activity
import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.spendmanager.app.BuildConfig
import com.spendmanager.app.service.TransactionNotificationListener
import com.spendmanager.app.ui.theme.*
import com.spendmanager.app.ui.viewmodel.SetupViewModel
import com.spendmanager.app.util.SmsPermissionHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val scrollState = rememberScrollState()

    // SMS permission state (only for sideload builds)
    var smsPermissionStatus by remember {
        mutableStateOf(
            if (BuildConfig.ENABLE_SMS_INGESTION) {
                SmsPermissionHelper.getPermissionStatus(context)
            } else {
                null
            }
        )
    }
    var showOnePlusDialog by remember { mutableStateOf(false) }

    // Refresh states when lifecycle resumes
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkNotificationListener()
                if (BuildConfig.ENABLE_SMS_INGESTION) {
                    smsPermissionStatus = SmsPermissionHelper.getPermissionStatus(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        containerColor = White
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(White)
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 32.dp)
        ) {
            // Header
            Text(
                text = "Quick Setup",
                style = MaterialTheme.typography.headlineLarge,
                color = Charcoal
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Configure a few things to get started",
                style = MaterialTheme.typography.bodyLarge,
                color = CharcoalMuted
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Notification Access Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = White,
                border = BorderStroke(
                    1.dp,
                    if (uiState.notificationListenerEnabled) AccentGreen else Gray200
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (uiState.notificationListenerEnabled) AccentGreenLight else OffWhite
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (uiState.notificationListenerEnabled)
                                    Icons.Outlined.CheckCircle
                                else
                                    Icons.Outlined.Notifications,
                                contentDescription = null,
                                tint = if (uiState.notificationListenerEnabled) AccentGreen else Charcoal,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Notification Access",
                                style = MaterialTheme.typography.titleMedium,
                                color = Charcoal
                            )
                            Text(
                                text = if (uiState.notificationListenerEnabled)
                                    "Enabled - We can read payment notifications"
                                else
                                    "Required to capture transactions",
                                style = MaterialTheme.typography.bodySmall,
                                color = CharcoalMuted
                            )
                        }
                    }

                    if (!uiState.notificationListenerEnabled) {
                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                context.startActivity(
                                    TransactionNotificationListener.getSettingsIntent()
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Charcoal,
                                contentColor = White
                            )
                        ) {
                            Text("Enable Access")
                        }
                    }
                }
            }

            // SMS Access Card (Sideload only)
            if (BuildConfig.ENABLE_SMS_INGESTION && smsPermissionStatus != null) {
                Spacer(modifier = Modifier.height(20.dp))

                SmsAccessCard(
                    permissionStatus = smsPermissionStatus!!,
                    activity = activity,
                    onShowOnePlusDialog = { showOnePlusDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Privacy Mode Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = White,
                border = BorderStroke(1.dp, Gray200)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Privacy Mode",
                        style = MaterialTheme.typography.titleMedium,
                        color = Charcoal
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Local Only Option
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setLocalOnlyMode(true) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (uiState.consent.localOnlyMode) OffWhite else White,
                        border = BorderStroke(
                            1.dp,
                            if (uiState.consent.localOnlyMode) Charcoal else Gray200
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = uiState.consent.localOnlyMode,
                                onClick = { viewModel.setLocalOnlyMode(true) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Charcoal
                                )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Local Only",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Charcoal
                                )
                                Text(
                                    "Data stays on device. No cloud.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CharcoalMuted
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Cloud AI Option
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setLocalOnlyMode(false) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (!uiState.consent.localOnlyMode) OffWhite else White,
                        border = BorderStroke(
                            1.dp,
                            if (!uiState.consent.localOnlyMode) Charcoal else Gray200
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = !uiState.consent.localOnlyMode,
                                onClick = { viewModel.setLocalOnlyMode(false) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Charcoal
                                )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Cloud AI",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Charcoal
                                )
                                Text(
                                    "Smart categorization & summaries",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CharcoalMuted
                                )
                            }
                        }
                    }
                }
            }

            // Cloud Options (if cloud mode selected)
            if (!uiState.consent.localOnlyMode) {
                Spacer(modifier = Modifier.height(20.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = White,
                    border = BorderStroke(1.dp, Gray200)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Cloud Options",
                            style = MaterialTheme.typography.titleMedium,
                            color = Charcoal
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Upload Raw Toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setUploadRaw(!uiState.consent.uploadRawEnabled) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = uiState.consent.uploadRawEnabled,
                                onCheckedChange = { viewModel.setUploadRaw(it) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Charcoal,
                                    checkmarkColor = White
                                )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Upload Raw Messages",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Charcoal
                                )
                                Text(
                                    "Better accuracy (off by default)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CharcoalMuted
                                )
                            }
                        }

                        // WhatsApp Toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setWhatsappEnabled(!uiState.consent.whatsappEnabled) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = uiState.consent.whatsappEnabled,
                                onCheckedChange = { viewModel.setWhatsappEnabled(it) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Charcoal,
                                    checkmarkColor = White
                                )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "WhatsApp Summaries",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Charcoal
                                )
                                Text(
                                    "Weekly spending on WhatsApp",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CharcoalMuted
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Continue Button
            Button(
                onClick = {
                    viewModel.saveAndContinue()
                    onSetupComplete()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Charcoal,
                    contentColor = White
                )
            ) {
                Text(
                    "Continue",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            if (!uiState.notificationListenerEnabled) {
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Notification access can be enabled later in Settings",
                    style = MaterialTheme.typography.bodySmall,
                    color = CharcoalMuted,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // OnePlus instructions dialog
    if (showOnePlusDialog) {
        AlertDialog(
            onDismissRequest = { showOnePlusDialog = false },
            title = {
                Text(
                    "OnePlus Auto-Start Setup",
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column {
                    SmsPermissionHelper.getOnePlusInstructions().forEach { instruction ->
                        Text(
                            text = instruction,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showOnePlusDialog = false
                        SmsPermissionHelper.getOnePlusAutoStartIntent(context)?.let { intent ->
                            context.startActivity(intent)
                        }
                    }
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showOnePlusDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun SmsAccessCard(
    permissionStatus: SmsPermissionHelper.PermissionStatus,
    activity: Activity?,
    onShowOnePlusDialog: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = White,
        border = BorderStroke(
            1.dp,
            if (permissionStatus.allOptimal) AccentGreen else Gray200
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (permissionStatus.smsGranted) AccentGreenLight else OffWhite
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (permissionStatus.smsGranted)
                            Icons.Outlined.CheckCircle
                        else
                            Icons.Outlined.Sms,
                        contentDescription = null,
                        tint = if (permissionStatus.smsGranted) AccentGreen else Charcoal,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SMS Access",
                        style = MaterialTheme.typography.titleMedium,
                        color = Charcoal
                    )
                    Text(
                        text = if (permissionStatus.smsGranted)
                            "Enabled - Reading bank transaction SMS"
                        else
                            "Required to capture bank SMS",
                        style = MaterialTheme.typography.bodySmall,
                        color = CharcoalMuted
                    )
                }
            }

            // OnePlus-specific notice
            if (permissionStatus.needsSpecialHandling && !permissionStatus.allOptimal) {
                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = WarningYellowLight
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = WarningYellow,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (SmsPermissionHelper.isOnePlusDevice())
                                "OnePlus detected: Extra steps needed for reliable SMS capture"
                            else
                                "Extra steps needed for reliable SMS capture",
                            style = MaterialTheme.typography.bodySmall,
                            color = Charcoal
                        )
                    }
                }
            }

            // SMS Permission button
            if (!permissionStatus.smsGranted) {
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        activity?.let { SmsPermissionHelper.requestSmsPermissions(it) }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Charcoal,
                        contentColor = White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Sms,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Enable SMS Access")
                }
            }

            // Battery optimization button (for OnePlus)
            if (permissionStatus.smsGranted && permissionStatus.needsSpecialHandling && !permissionStatus.batteryOptimizationDisabled) {
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        activity?.let { SmsPermissionHelper.requestDisableBatteryOptimization(it) }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Charcoal)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.BatteryChargingFull,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Charcoal
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Disable Battery Optimization", color = Charcoal)
                }
            }

            // Auto-start button (OnePlus specific)
            if (permissionStatus.smsGranted && permissionStatus.needsSpecialHandling) {
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onShowOnePlusDialog,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Gray300)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Charcoal
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Configure Auto-Start", color = Charcoal)
                }
            }
        }
    }
}
