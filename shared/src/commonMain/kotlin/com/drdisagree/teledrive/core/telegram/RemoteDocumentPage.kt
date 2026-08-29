package com.drdisagree.teledrive.core.telegram

data class RemoteDocumentPage(
    val documents: List<RemoteDocument>,
    /** Pass as `fromMessageId` to fetch the next (older) page; 0 when exhausted. */
    val nextFromMessageId: Long
)
