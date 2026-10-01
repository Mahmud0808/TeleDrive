package com.drdisagree.teledrive.data.repository

import com.drdisagree.teledrive.core.telegram.RemoteDocument
import com.drdisagree.teledrive.data.local.entity.FileEntity
import com.drdisagree.teledrive.data.remote.telegram.RemoteFileManifest

internal data class KnownRows(
    val manifests: Map<RemoteDocument, RemoteFileManifest?>,
    val byId: Map<String, FileEntity>,
    val byUniqueId: Map<String, FileEntity>,
    val splitCounts: Map<String, Int>,
    val firstPartStored: Set<String>
)
