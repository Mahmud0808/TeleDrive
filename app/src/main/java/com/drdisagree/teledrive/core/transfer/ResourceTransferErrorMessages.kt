package com.drdisagree.teledrive.core.transfer

import android.content.Context
import com.drdisagree.teledrive.R

class ResourceTransferErrorMessages(
    private val context: Context
) : TransferErrorMessages {

    override val keyMissing: String
        get() = context.getString(R.string.transfer_error_key_missing)

    override val noFileReference: String
        get() = context.getString(R.string.transfer_error_no_file_reference)

    override val fileRecordMissing: String
        get() = context.getString(R.string.transfer_error_file_record_missing)

    override val noLocalCopy: String
        get() = context.getString(R.string.transfer_error_no_local_copy)

    override val localFileGone: String
        get() = context.getString(R.string.transfer_error_local_file_gone)

    override val uploadEnded: String
        get() = context.getString(R.string.transfer_error_upload_ended)

    override val uploadStalled: String
        get() = context.getString(R.string.transfer_error_upload_stalled)

    override val downloadEnded: String
        get() = context.getString(R.string.transfer_error_download_ended)

    override val downloadStalled: String
        get() = context.getString(R.string.transfer_error_download_stalled)

    override val downloadedMissing: String
        get() = context.getString(R.string.transfer_error_downloaded_missing)

    override val noRemoteCopy: String
        get() = context.getString(R.string.transfer_error_no_remote_copy)

    override val decryptionFailed: String
        get() = context.getString(R.string.transfer_error_decryption_failed)

    override val saveFailed: String
        get() = context.getString(R.string.transfer_error_save_failed)
}
