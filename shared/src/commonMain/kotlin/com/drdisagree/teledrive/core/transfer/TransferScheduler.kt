package com.drdisagree.teledrive.core.transfer

interface TransferScheduler {

    fun kick(allowMetered: Boolean)

    fun rekick(allowMetered: Boolean)
}
