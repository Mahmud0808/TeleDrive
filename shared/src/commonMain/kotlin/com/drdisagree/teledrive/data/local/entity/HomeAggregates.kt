package com.drdisagree.teledrive.data.local.entity

data class HomeAggregates(
    val total: Int,
    val remoteBytes: Long,
    val backedUp: Int,
    val queued: Int,
    val failed: Int,
    val localOnly: Int
)
