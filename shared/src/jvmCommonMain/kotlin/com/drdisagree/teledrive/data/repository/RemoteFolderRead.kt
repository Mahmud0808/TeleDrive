package com.drdisagree.teledrive.data.repository

import com.drdisagree.teledrive.data.remote.telegram.RemoteFolderState

internal sealed interface RemoteFolderRead {
    data class Ok(val state: RemoteFolderState) : RemoteFolderRead
    data object Unreadable : RemoteFolderRead
    data object Unavailable : RemoteFolderRead
}
