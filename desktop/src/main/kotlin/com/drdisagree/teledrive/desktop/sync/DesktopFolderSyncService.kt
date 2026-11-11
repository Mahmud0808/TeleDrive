package com.drdisagree.teledrive.desktop.sync

import com.drdisagree.teledrive.core.common.AppResult
import com.drdisagree.teledrive.core.files.AppStoragePaths
import com.drdisagree.teledrive.domain.repository.FileRepository
import com.drdisagree.teledrive.domain.repository.SettingsRepository
import com.drdisagree.teledrive.domain.repository.TransferRepository
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Base64
import java.util.Properties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class FolderSyncStatus(
    val source: String = "",
    val enabled: Boolean = false,
    val queued: Int = 0,
    val changedFiles: Int = 0,
    val message: String = "Selecione uma pasta para começar."
)

/**
 * One-way desktop folder uploader. Only newly discovered files are enqueued.
 *
 * The inventory is persisted separately from the Telegram data. Changed files are deliberately
 * reported rather than re-enqueued: FileRepository.importLocalFile() returns the existing record
 * for a local path and reusing it would NOT update an already-uploaded Telegram message.
 * A later revision must implement atomic replacement/version handling before enabling edits.
 */
class DesktopFolderSyncService(
    private val paths: AppStoragePaths,
    private val files: FileRepository,
    private val transfers: TransferRepository,
    private val settings: SettingsRepository
) {
    private val root = File(paths.filesDir, "folder-sync").apply { mkdirs() }
    private val configFile = File(root, "config.properties")
    private val inventoryFile = File(root, "inventory.properties")
    private val mutex = Mutex()
    private val inventory = Properties().apply {
        if (inventoryFile.exists()) inventoryFile.inputStream().use(::load)
    }
    private val config = Properties().apply {
        if (configFile.exists()) configFile.inputStream().use(::load)
    }
    private val _status = MutableStateFlow(
        FolderSyncStatus(
            source = config.getProperty("source", ""),
            enabled = config.getProperty("enabled") == "true",
            message = "Aguardando verificação de arquivos."
        )
    )
    val status: StateFlow<FolderSyncStatus> = _status.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            while (isActive) {
                runCatching { scanOnce() }.onFailure { e ->
                    _status.value = _status.value.copy(
                        message = "Falha na verificação: ${e.message.orEmpty()}"
                    )
                }
                delay(10_000)
            }
        }
    }

    /** Changing the source clears only the local inventory; it never deletes a remote file. */
    suspend fun configure(source: String): Boolean = mutex.withLock {
        val chosen = File(source).absoluteFile
        if (!chosen.isDirectory || !chosen.canRead()) {
            _status.value = _status.value.copy(message = "Escolha uma pasta existente e legível.")
            return@withLock false
        }
        val channelId = settings.preferences.first().storageChatId
        if (channelId == null) {
            _status.value = _status.value.copy(message = "Selecione um canal no TeleDrive primeiro.")
            return@withLock false
        }
        config.setProperty("source", chosen.canonicalPath)
        config.setProperty("channel", channelId.toString())
        config.setProperty("enabled", "false")
        inventory.clear()
        persist(inventory, inventoryFile)
        persist(config, configFile)
        _status.value = FolderSyncStatus(
            source = chosen.canonicalPath,
            message = "Pasta definida. Ative a sincronização para enviar os arquivos."
        )
        true
    }

    suspend fun setEnabled(enabled: Boolean) = mutex.withLock {
        if (_status.value.source.isBlank()) return@withLock
        if (enabled && settings.preferences.first().deleteAfterUpload) {
            _status.value = _status.value.copy(
                enabled = false,
                message = "Desative 'Excluir após upload' nas configurações antes de sincronizar."
            )
            return@withLock
        }
        config.setProperty("enabled", enabled.toString())
        persist(config, configFile)
        _status.value = _status.value.copy(
            enabled = enabled,
            message = if (enabled) "Sincronização ativada." else "Sincronização pausada."
        )
    }

    suspend fun scanNow() = scanOnce()

    private suspend fun scanOnce() = mutex.withLock {
        val current = _status.value
        if (!current.enabled || current.source.isBlank()) return@withLock
        val source = File(current.source)
        if (!source.isDirectory) {
            _status.value = current.copy(message = "Pasta de origem não encontrada.")
            return@withLock
        }
        val preferences = settings.preferences.first()
        if (preferences.deleteAfterUpload) {
            config.setProperty("enabled", "false")
            persist(config, configFile)
            _status.value = current.copy(
                enabled = false,
                message = "Sincronização pausada: desative 'Excluir após upload' para preservar arquivos locais."
            )
            return@withLock
        }
        val channel = preferences.storageChatId
        if (channel == null || channel.toString() != config.getProperty("channel")) {
            _status.value = current.copy(
                message = "Canal diferente do configurado. Pause e selecione a pasta novamente."
            )
            return@withLock
        }

        var queued = 0
        var changed = 0
        var failures = 0
        val now = System.currentTimeMillis()
        // Walk only within the source tree. Symbolic-link directories are never followed.
        for (candidate in FolderSyncScanner.candidates(source, now)) {
            val file = candidate.file
            val relative = candidate.relativePath
            val key = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(relative.toByteArray(Charsets.UTF_8))
            val signature = candidate.signature
            val previous = inventory.getProperty(key)
            if (previous == signature) continue
            if (previous != null && previous != "pending:$signature") {
                changed++
                continue
            }
            val parent = relative.substringBeforeLast('/', "")
            // Keep each source under its own dedicated folder in My Drive.
            val remotePath = listOf("Computador", source.name, parent)
                .filter { it.isNotBlank() }.joinToString("/")
            try {
                val folderId = files.resolveImportFolder(remotePath, null)
                if (folderId == null) {
                    failures++
                    continue
                }
                when (val imported = files.importLocalFile(file.absolutePath, folderId)) {
                    is AppResult.Success -> {
                        val uploadedAlready = imported.value.hasRemoteCopy
                        val accepted = uploadedAlready ||
                            transfers.enqueueUpload(imported.value.id) is AppResult.Success
                        if (accepted) {
                            inventory.setProperty(
                                key, if (uploadedAlready) signature else "pending:$signature"
                            )
                            persist(inventory, inventoryFile)
                            if (!uploadedAlready && previous == null) queued++
                        } else failures++
                    }
                    is AppResult.Failure -> failures++
                }
            } catch (_: Exception) {
                failures++
            }
            if (queued >= 50) break // Bound each pass; the next scan continues.
        }
        _status.value = current.copy(
            queued = current.queued + queued,
            changedFiles = changed,
            message = when {
                failures > 0 -> "${failures} arquivo(s) não enfileirado(s); nova tentativa em breve."
                changed > 0 -> "${changed} arquivo(s) alterado(s): substituição ainda não habilitada."
                queued > 0 -> "${queued} arquivo(s) adicionado(s) à fila de envio."
                else -> "Pasta verificada. Nenhum arquivo novo."
            }
        )
    }

    private fun persist(properties: Properties, destination: File) {
        val temporary = File(destination.parentFile, destination.name + ".tmp")
        temporary.outputStream().use { properties.store(it, "TeleDrive desktop folder sync") }
        try {
            Files.move(
                temporary.toPath(), destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(
                temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING
            )
        }
    }
}
