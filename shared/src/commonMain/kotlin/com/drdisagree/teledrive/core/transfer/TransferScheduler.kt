package com.drdisagree.teledrive.core.transfer

/** Starts the transfer queue worker on the platform scheduler. */
interface TransferScheduler {

    fun kick(allowMetered: Boolean)

    fun rekick(allowMetered: Boolean)
}
