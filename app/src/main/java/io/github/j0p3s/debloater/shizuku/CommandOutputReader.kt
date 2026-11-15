package io.github.j0p3s.debloater.shizuku

import java.io.InputStream
import java.util.concurrent.atomic.AtomicReference

/** Pipe closure after process termination must not escape a thread and kill the service. */
internal object CommandOutputReader {
    fun capture(input: InputStream, destination: StringBuilder, failure: AtomicReference<Throwable?>) {
        try { input.bufferedReader().use { destination.append(it.readText()) } }
        catch(error: Exception) { failure.compareAndSet(null, error) }
    }
}
