package com.drdisagree.teledrive.data.repository

import com.drdisagree.teledrive.core.telegram.RemoteDocument
import com.drdisagree.teledrive.data.local.entity.FileEntity
import com.drdisagree.teledrive.data.remote.telegram.RemoteFileManifest

internal data class KnownRows(
    val manifests: Map<RemoteDocument, RemoteFileManifest?>,
    val byId: Map<String, FileEntity>,
    val byMessage: Map<Pair<Long?, Long?>, FileEntity>,
    val splitCounts: Map<String, Int>,
    val firstPartStored: Set<String>
) {
    fun sameMessage(document: RemoteDocument): FileEntity? =
        byMessage[document.chatId to document.messageId]
            ?.takeIf { it.remoteUniqueId == document.uniqueFileId }
}
