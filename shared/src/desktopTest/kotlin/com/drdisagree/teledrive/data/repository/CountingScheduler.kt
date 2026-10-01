package com.drdisagree.teledrive.data.repository

import com.drdisagree.teledrive.core.publish.PublishScheduler

internal class CountingScheduler : PublishScheduler {
    var kicks = 0
    override fun kick() {
        kicks++
    }
}
