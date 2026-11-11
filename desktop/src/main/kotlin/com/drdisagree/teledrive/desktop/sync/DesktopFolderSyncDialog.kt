package com.drdisagree.teledrive.desktop.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.swing.JFileChooser
import javax.swing.SwingUtilities

@Composable
fun DesktopFolderSyncDialog(
    service: DesktopFolderSyncService,
    scope: CoroutineScope,
    onClose: () -> Unit
) {
    val status by service.status.collectAsState()

    DialogWindow(
        onCloseRequest = onClose,
        title = "TeleDrive - Sincronização de pastas",
        state = rememberDialogState(width = 600.dp, height = 370.dp)
    ) {
        MaterialTheme {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Sincronização de pastas", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Envio em uma direção: Windows → Meu Drive / Computador. " +
                        "Arquivos apagados no computador não são apagados na nuvem."
                )
                Text(
                    if (status.source.isBlank()) "Pasta: nenhuma selecionada"
                    else "Pasta: ${status.source}"
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = {
                        SwingUtilities.invokeLater {
                            val picker = JFileChooser().apply {
                                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                                dialogTitle = "Selecione uma pasta do Windows"
                            }
                            if (picker.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                                val path = picker.selectedFile.absolutePath
                                scope.launch { service.configure(path) }
                            }
                        }
                    }) {
                        Text("Selecionar pasta")
                    }
                    Text("Ativar envio automático")
                    Switch(
                        checked = status.enabled,
                        enabled = status.source.isNotBlank(),
                        onCheckedChange = { value ->
                            scope.launch { service.setEnabled(value) }
                        }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { scope.launch { service.scanNow() } },
                        enabled = status.enabled
                    ) { Text("Verificar agora") }
                    OutlinedButton(onClick = onClose) { Text("Fechar") }
                }
                Text(status.message, color = MaterialTheme.colorScheme.primary)
                if (status.changedFiles > 0) {
                    Text(
                        "Atenção: arquivos existentes que mudaram não são substituídos " +
                            "automaticamente nesta primeira versão."
                    )
                }
            }
        }
    }
}
