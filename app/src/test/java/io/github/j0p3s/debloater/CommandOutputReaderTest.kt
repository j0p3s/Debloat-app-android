package io.github.j0p3s.debloater
import io.github.j0p3s.debloater.shizuku.CommandOutputReader
import org.junit.Assert.*
import org.junit.Test
import java.io.InputStream
import java.io.InterruptedIOException
import java.util.concurrent.atomic.AtomicReference

class CommandOutputReaderTest {
    @Test fun interruptedPipeDoesNotEscapeWorkerThread() {
        val error = AtomicReference<Throwable?>(null)
        val uncaught = AtomicReference<Throwable?>(null)
        val input = object : InputStream() { override fun read(): Int = throw InterruptedIOException("pipe closed") }
        val worker = Thread { CommandOutputReader.capture(input, StringBuilder(), error) }
        worker.uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, failure -> uncaught.set(failure) }
        worker.start(); worker.join(2000)
        assertFalse(worker.isAlive)
        assertNull(uncaught.get())
        assertTrue(error.get() is InterruptedIOException)
    }
    @Test fun successfulOutputIsPreserved() {
        val error = AtomicReference<Throwable?>(null)
        val output = StringBuilder()
        CommandOutputReader.capture("Success\n".byteInputStream(), output, error)
        assertEquals("Success\n", output.toString())
        assertNull(error.get())
    }
}
