package io.github.j0p3s.debloater

import io.github.j0p3s.debloater.data.BoundedCacheReader
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch

class BoundedCacheReaderTest {
    @Test fun timeoutsDoNotQueueUnboundedCallsToAnUnresponsiveService() = runBlocking {
        val reader = BoundedCacheReader(100)
        val release = CountDownLatch(1)
        val started = CountDownLatch(2)
        try {
            withTimeout(3000) {
                repeat(2) {
                    try { reader.read { started.countDown(); release.await(); 1 }; fail("Expected timeout") }
                    catch (_: TimeoutCancellationException) { }
                }
                assertEquals(0L, started.count)
                try { reader.read { fail("Must not queue another Binder call") }; fail("Expected busy failure") }
                catch (_: IllegalStateException) { }
            }
        } finally { release.countDown() }
    }
    @Test fun cancellationDoesNotWaitForTheBlockingCall() = runBlocking {
        val reader = BoundedCacheReader(10000)
        val release = CountDownLatch(1)
        val started = CompletableDeferred<Unit>()
        try {
            val job = launch { reader.read { started.complete(Unit); release.await() } }
            withTimeout(3000) { started.await(); job.cancelAndJoin() }
            assertTrue(job.isCancelled)
        } finally { release.countDown() }
    }
}
