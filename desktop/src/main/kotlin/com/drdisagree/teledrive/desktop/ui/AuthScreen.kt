package com.drdisagree.teledrive.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.drdisagree.teledrive.core.common.AppResult
import com.drdisagree.teledrive.core.telegram.TelegramAuthState
import com.drdisagree.teledrive.core.telegram.TelegramCredentials
import com.drdisagree.teledrive.domain.repository.TelegramAuthRepository
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun AuthScreen() {
    val authRepository = koinInject<TelegramAuthRepository>()
    val authState by authRepository.authState.collectAsState()
    val scope = rememberCoroutineScope()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.width(400.dp).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "TeleDrive",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                when (val state = authState) {
                    is TelegramAuthState.Uninitialized -> CredentialsStep { id, hash ->
                        scope.launch {
                            authRepository.configure(TelegramCredentials(id, hash))
                        }
                    }

                    is TelegramAuthState.Initializing -> LoadingStep("Connecting to Telegram")

                    is TelegramAuthState.WaitingForPhoneNumber -> PhoneStep { phone ->
                        scope.launch { authRepository.submitPhoneNumber(phone) }
                    }

                    is TelegramAuthState.WaitingForCode -> CodeStep(
                        label = "Enter the code sent to ${state.phoneNumber}",
                        onSubmit = { code -> scope.launch { authRepository.submitCode(code) } }
                    )

                    is TelegramAuthState.WaitingForEmailAddress -> InputStep(
                        label = "Email address",
                        button = "Continue",
                        onSubmit = { email ->
                            scope.launch { authRepository.submitEmailAddress(email) }
                        }
                    )

                    is TelegramAuthState.WaitingForEmailCode -> CodeStep(
                        label = "Enter the code sent to ${state.emailPattern}",
                        onSubmit = { code ->
                            scope.launch { authRepository.submitEmailCode(code) }
                        }
                    )

                    is TelegramAuthState.WaitingForPassword -> PasswordStep(
                        hint = state.passwordHint,
                        onSubmit = { password ->
                            scope.launch { authRepository.submitPassword(password) }
                        }
                    )

                    is TelegramAuthState.WaitingForQrScan -> LoadingStep("Confirm on another device")

                    is TelegramAuthState.RegistrationRequired -> Message(
                        "This number has no Telegram account. Register on a phone first."
                    )

                    is TelegramAuthState.Ready -> ReadyStep(authRepository)

                    is TelegramAuthState.LoggingOut -> LoadingStep("Signing out")

                    is TelegramAuthState.Closed -> Message("Session closed. Restart the app.")

                    is TelegramAuthState.Failed -> Message("Sign in failed: ${state.message}")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadingStep(label: String) {
    CircularWavyProgressIndicator(modifier = Modifier.size(56.dp))
    Text(label, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun Message(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
}

@Composable
private fun CredentialsStep(onSubmit: (Int, String) -> Unit) {
    var apiId by remember { mutableStateOf("") }
    var apiHash by remember { mutableStateOf("") }
    Message("Enter your Telegram API credentials from my.telegram.org")
    OutlinedTextField(
        value = apiId,
        onValueChange = { apiId = it.filter(Char::isDigit) },
        label = { Text("API ID") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = apiHash,
        onValueChange = { apiHash = it.trim() },
        label = { Text("API hash") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Button(
        onClick = { apiId.toIntOrNull()?.let { onSubmit(it, apiHash) } },
        enabled = apiId.isNotBlank() && apiHash.isNotBlank(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Continue")
    }
}

@Composable
private fun PhoneStep(onSubmit: (String) -> Unit) {
    InputStep(label = "Phone number", button = "Send code", onSubmit = onSubmit)
}

@Composable
private fun CodeStep(label: String, onSubmit: (String) -> Unit) {
    Message(label)
    InputStep(label = "Code", button = "Verify", onSubmit = onSubmit)
}

@Composable
private fun PasswordStep(hint: String?, onSubmit: (String) -> Unit) {
    Message(if (hint == null) "Two-step password" else "Two-step password (hint: $hint)")
    InputStep(label = "Password", button = "Sign in", onSubmit = onSubmit)
}

@Composable
private fun InputStep(label: String, button: String, onSubmit: (String) -> Unit) {
    var value by remember { mutableStateOf("") }
    OutlinedTextField(
        value = value,
        onValueChange = { value = it },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Button(
        onClick = { onSubmit(value.trim()) },
        enabled = value.isNotBlank(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(button)
    }
}

@Composable
private fun ReadyStep(authRepository: TelegramAuthRepository) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        name = when (val result = authRepository.getCurrentUser()) {
            is AppResult.Success -> result.value.firstName
            else -> "your account"
        }
    }
    Message("Signed in as ${name.ifEmpty { "..." }}")
    OutlinedButton(
        onClick = { scope.launch { authRepository.logout() } },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Sign out")
    }
}
