package com.spendmanager.app.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendmanager.app.ui.theme.*
import com.spendmanager.app.ui.viewmodel.LoginViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as Activity

    LaunchedEffect(uiState.loginSuccess) {
        if (uiState.loginSuccess) {
            onLoginSuccess()
        }
    }

    Scaffold(
        containerColor = White
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .background(White),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(OffWhite),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Phone,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = Charcoal
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = if (uiState.otpSent) "Enter OTP" else "Welcome",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                color = Charcoal
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (uiState.otpSent)
                    "We sent a code to ${uiState.phoneNumber}"
                else
                    "Enter your phone number to continue",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = CharcoalMuted
            )

            Spacer(modifier = Modifier.height(40.dp))

            if (!uiState.otpSent) {
                // Phone number input
                OutlinedTextField(
                    value = uiState.phoneNumber,
                    onValueChange = viewModel::onPhoneNumberChange,
                    label = { Text("Phone Number") },
                    placeholder = { Text("+91 98765 43210") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { viewModel.requestOtp(activity) }
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Charcoal,
                        unfocusedBorderColor = Gray300,
                        focusedLabelColor = Charcoal,
                        cursorColor = Charcoal
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { viewModel.requestOtp(activity) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = !uiState.isLoading && uiState.phoneNumber.length >= 10,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Charcoal,
                        contentColor = White,
                        disabledContainerColor = Gray300,
                        disabledContentColor = Gray500
                    )
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = White
                        )
                    } else {
                        Text(
                            "Continue",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            } else {
                // OTP input
                OutlinedTextField(
                    value = uiState.otp,
                    onValueChange = viewModel::onOtpChange,
                    label = { Text("OTP Code") },
                    placeholder = { Text("123456") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { viewModel.verifyOtp() }
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Charcoal,
                        unfocusedBorderColor = Gray300,
                        focusedLabelColor = Charcoal,
                        cursorColor = Charcoal
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = viewModel::verifyOtp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = !uiState.isLoading && uiState.otp.length == 6,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Charcoal,
                        contentColor = White,
                        disabledContainerColor = Gray300,
                        disabledContentColor = Gray500
                    )
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = White
                        )
                    } else {
                        Text(
                            "Verify",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(
                    onClick = viewModel::resetOtp,
                    enabled = !uiState.isLoading
                ) {
                    Text(
                        "Change Phone Number",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CharcoalMuted
                    )
                }
            }

            // Error message
            uiState.error?.let { error ->
                Spacer(modifier = Modifier.height(20.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = AccentRedLight
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(16.dp),
                        color = AccentRed,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
