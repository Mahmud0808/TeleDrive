package com.drdisagree.teledrive.core.telegram

/** A change to the storage channel made by another session. */
sealed interface MessageChange {

    /** A message arrived or its content, caption included, was edited. */
    data class Updated(val messageId: Long) : MessageChange

    /** Messages deleted on the server, not merely dropped from a local cache. */
    data class Deleted(val messageIds: List<Long>) : MessageChange
}
