package com.drdisagree.teledrive.core.media

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import com.drdisagree.teledrive.core.telegram.TelegramClient
import javax.inject.Inject

@UnstableApi
class TelegramDataSourceFactory @Inject constructor(
    private val telegramClient: TelegramClient
) {

    fun create(remoteFileId: String): DataSource.Factory =
        DataSource.Factory { TelegramDataSource(telegramClient, remoteFileId) }
}
