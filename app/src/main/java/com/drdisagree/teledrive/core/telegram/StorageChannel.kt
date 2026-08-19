package com.drdisagree.teledrive.core.telegram

data class StorageChannel(
    val chatId: Long,
    val title: String,
    val documentCount: Int,
    /** Local path of the channel picture, when it has one and it is cached. */
    val photoPath: String? = null
)
