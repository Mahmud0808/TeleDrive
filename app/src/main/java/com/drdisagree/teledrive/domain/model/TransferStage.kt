package com.drdisagree.teledrive.domain.model

/**
 * Local work a split transfer does between parts. Bytes are not moving during
 * these, so a speed reading would be a stale number rather than an idle one.
 */
enum class TransferStage {
    SEALING,
    JOINING
}
