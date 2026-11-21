package com.drdisagree.teledrive.core.media

import com.drdisagree.teledrive.core.telegram.TelegramFileInfo
import java.io.File

internal val TelegramFileInfo.hasLocalCopy: Boolean
    get() = isDownloadingCompleted && localPath?.let { File(it).isFile } == true
