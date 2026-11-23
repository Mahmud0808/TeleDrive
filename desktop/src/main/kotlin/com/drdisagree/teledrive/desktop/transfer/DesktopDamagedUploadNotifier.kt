package com.drdisagree.teledrive.desktop.transfer

import com.drdisagree.teledrive.core.transfer.DamagedUploadNotifier
import com.drdisagree.teledrive.core.transfer.DamagedUploads

class DesktopDamagedUploadNotifier(
    private val damagedUploads: DamagedUploads
) : DamagedUploadNotifier {

    override fun report(fileName: String) = damagedUploads.add(fileName)
}
