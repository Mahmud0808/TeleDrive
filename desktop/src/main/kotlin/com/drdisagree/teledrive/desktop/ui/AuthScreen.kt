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
import com.drdisagree.teledrive.core.telegram.TelegramAuthState
import com.drdisagree.teledrive.core.telegram.TelegramCredentials
import com.drdisagree.teledrive.domain.repository.TelegramAuthRepository
import com.drdisagree.teledrive.desktop.resources.Res
import com.drdisagree.teledrive.desktop.resources.app_name
import com.drdisagree.teledrive.desktop.resources.auth_api_hash
import com.drdisagree.teledrive.desktop.resources.auth_api_id
import com.drdisagree.teledrive.desktop.resources.auth_code
import com.drdisagree.teledrive.desktop.resources.auth_code_sent_to
import com.drdisagree.teledrive.desktop.resources.auth_confirm_other_device
import com.drdisagree.teledrive.desktop.resources.auth_connecting
import com.drdisagree.teledrive.desktop.resources.auth_continue
import com.drdisagree.teledrive.desktop.resources.auth_credentials_prompt
import com.drdisagree.teledrive.desktop.resources.auth_email_address
import com.drdisagree.teledrive.desktop.resources.auth_failed
import com.drdisagree.teledrive.desktop.resources.auth_password
import com.drdisagree.teledrive.desktop.resources.auth_password_hint
import com.drdisagree.teledrive.desktop.resources.auth_password_prompt
import com.drdisagree.teledrive.desktop.resources.auth_phone_number
import com.drdisagree.teledrive.desktop.resources.auth_registration_required
import com.drdisagree.teledrive.desktop.resources.auth_send_code
import com.drdisagree.teledrive.desktop.resources.auth_session_closed
import com.drdisagree.teledrive.desktop.resources.auth_sign_in
import com.drdisagree.teledrive.desktop.resources.auth_signing_out
import com.drdisagree.teledrive.desktop.resources.auth_verify
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
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
                    text = stringResource(Res.string.app_name),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                when (val state = authState) {
                    is TelegramAuthState.Uninitialized -> CredentialsStep { id, hash ->
                        scope.launch {
                            authRepository.configure(TelegramCredentials(id, hash))
                        }
                    }

                    is TelegramAuthState.Initializing -> LoadingStep(stringResource(Res.string.auth_connecting))

                    is TelegramAuthState.WaitingForPhoneNumber -> PhoneStep { phone ->
                        scope.launch { authRepository.submitPhoneNumber(phone) }
                    }

                    is TelegramAuthState.WaitingForCode -> CodeStep(
                        label = stringResource(Res.string.auth_code_sent_to, state.phoneNumber),
                        onSubmit = { code -> scope.launch { authRepository.submitCode(code) } }
                    )

                    is TelegramAuthState.WaitingForEmailAddress -> InputStep(
                        label = stringResource(Res.string.auth_email_address),
                        button = stringResource(Res.string.auth_continue),
                        onSubmit = { email ->
                            scope.launch { authRepository.submitEmailAddress(email) }
                        }
                    )

                    is TelegramAuthState.WaitingForEmailCode -> CodeStep(
                        label = stringResource(Res.string.auth_code_sent_to, state.emailPattern),
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

                    is TelegramAuthState.WaitingForQrScan -> LoadingStep(stringResource(Res.string.auth_confirm_other_device))

                    is TelegramAuthState.RegistrationRequired -> Message(
                        stringResource(Res.string.auth_registration_required)
                    )

                    is TelegramAuthState.Ready -> Unit

                    is TelegramAuthState.LoggingOut -> LoadingStep(stringResource(Res.string.auth_signing_out))

                    is TelegramAuthState.Closed -> Message(stringResource(Res.string.auth_session_closed))

                    is TelegramAuthState.Failed -> Message(stringResource(Res.string.auth_failed, state.message))
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
    Message(stringResource(Res.string.auth_credentials_prompt))
    OutlinedTextField(
        value = apiId,
        onValueChange = { apiId = it.filter(Char::isDigit) },
        label = { Text(stringResource(Res.string.auth_api_id)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = apiHash,
        onValueChange = { apiHash = it.trim() },
        label = { Text(stringResource(Res.string.auth_api_hash)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Button(
        onClick = { apiId.toIntOrNull()?.let { onSubmit(it, apiHash) } },
        enabled = apiId.isNotBlank() && apiHash.isNotBlank(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(Res.string.auth_continue))
    }
}

@Composable
private fun PhoneStep(onSubmit: (String) -> Unit) {
    InputStep(
        label = stringResource(Res.string.auth_phone_number),
        button = stringResource(Res.string.auth_send_code),
        onSubmit = onSubmit
    )
}

@Composable
private fun CodeStep(label: String, onSubmit: (String) -> Unit) {
    Message(label)
    InputStep(
        label = stringResource(Res.string.auth_code),
        button = stringResource(Res.string.auth_verify),
        onSubmit = onSubmit
    )
}

@Composable
private fun PasswordStep(hint: String?, onSubmit: (String) -> Unit) {
    Message(
        if (hint == null) {
            stringResource(Res.string.auth_password_prompt)
        } else {
            stringResource(Res.string.auth_password_hint, hint)
        }
    )
    InputStep(
        label = stringResource(Res.string.auth_password),
        button = stringResource(Res.string.auth_sign_in),
        onSubmit = onSubmit
    )
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
