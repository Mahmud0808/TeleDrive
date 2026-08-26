package com.drdisagree.teledrive.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.drdisagree.teledrive.core.telegram.TelegramProxyType

@Entity(tableName = "proxies")
data class ProxyEntity(
    @PrimaryKey val id: String,
    val label: String,
    val type: TelegramProxyType,
    val host: String,
    val port: Int,
    val username: String? = null,
    val password: String? = null,
    val secret: String? = null,
    val addedAt: Long
)
