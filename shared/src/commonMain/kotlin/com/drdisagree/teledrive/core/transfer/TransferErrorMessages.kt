package com.drdisagree.teledrive.core.transfer

interface TransferErrorMessages {

    val keyMissing: String

    val noFileReference: String

    val fileRecordMissing: String

    val noLocalCopy: String

    val localFileGone: String

    val uploadEnded: String

    val uploadStalled: String

    val downloadEnded: String

    val downloadStalled: String

    val downloadedMissing: String

    val noRemoteCopy: String

    val decryptionFailed: String

    val saveFailed: String
}
