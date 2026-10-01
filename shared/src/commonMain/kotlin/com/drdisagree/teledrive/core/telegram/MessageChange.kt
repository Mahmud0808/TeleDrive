package com.drdisagree.teledrive.core.telegram

sealed interface MessageChange {

    data class Updated(val messageId: Long) : MessageChange

    /** Messages deleted on the server, not merely dropped from a local cache. */
    data class Deleted(val messageIds: List<Long>) : MessageChange
}
