package com.drdisagree.teledrive.core.common

import kotlin.coroutines.cancellation.CancellationException

/**
 * [runCatching] treats cancellation as an ordinary failure. Inside a
 * `withTimeout` that means the timeout is swallowed by the first `runCatching`
 * around a suspending call, and the screen keeps waiting for a request chain
 * that has already been cancelled. Rethrow cancellation so the timeout can
 * take effect; every other failure is still returned as a [Result].
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
