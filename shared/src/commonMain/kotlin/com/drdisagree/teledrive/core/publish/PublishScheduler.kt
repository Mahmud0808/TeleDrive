package com.drdisagree.teledrive.core.publish

/** Queues a drain of the publish outbox on the platform scheduler. */
interface PublishScheduler {

    fun kick()
}
