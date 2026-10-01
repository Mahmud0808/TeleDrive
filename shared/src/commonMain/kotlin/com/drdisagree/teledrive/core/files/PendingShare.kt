package com.drdisagree.teledrive.core.files

import kotlinx.coroutines.flow.StateFlow

interface PendingShare {

    val uris: StateFlow<List<String>>

    val text: StateFlow<String?>

    fun offer(incoming: List<String>)

    fun clear()

    fun offerText(incoming: String?)

    fun clearText()
}
