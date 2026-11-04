package io.github.j0p3s.debloater.data

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Bounds both caller wait and outstanding blocking Binder calls, even if Android ignores interruption. */
class BoundedCacheReader(private val timeoutMs: Long = 8000) {
    private val permits = Semaphore(2)
    private val workers = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "cache-stat").apply { isDaemon = true }
    }
    suspend fun <T> read(block: () -> T): T {
        // A cancelled/timed-out caller does not release the permit while Binder is still executing.
        return withTimeout(timeoutMs) {
            suspendCancellableCoroutine { continuation ->
                if (!continuation.isActive) return@suspendCancellableCoroutine
                if (!permits.tryAcquire()) {
                    continuation.resumeWithException(IllegalStateException("Android storage service is still busy; try again later"))
                    return@suspendCancellableCoroutine
                }
                workers.execute {
                    try {
                        val result = block()
                        if (continuation.isActive) continuation.resume(result)
                    } catch (error: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    } finally { permits.release() }
                }
            }
        }
    }
}
