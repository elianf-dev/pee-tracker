package com.peetracker.app.util

import kotlin.coroutines.cancellation.CancellationException

/**
 * Like [runCatching], but lets cancellation through.
 *
 * `runCatching` catches [Throwable], which in a coroutine includes the [CancellationException]
 * the machinery throws to unwind a cancelled job. Swallowing it turns "stop, your scope is gone"
 * into an ordinary failed [Result], and the coroutine carries on doing work — issuing more
 * network writes, advancing to the next item in a loop — after whatever owned it has been torn
 * down. Use this wherever a `runCatching` wraps a suspending call.
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
