package com.drdisagree.teledrive.core.transfer

fun interface DamagedUploadNotifier {
    fun report(fileName: String)
}
