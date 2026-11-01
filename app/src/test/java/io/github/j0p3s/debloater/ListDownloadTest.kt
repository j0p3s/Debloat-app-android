package io.github.j0p3s.debloater

import io.github.j0p3s.debloater.data.ListDownload
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class ListDownloadTest {
    private class Response(url: String, val code: Int = 200, val body: String = "[]") : HttpURLConnection(URL(url)) {
        var closed = false
        override fun connect() {}
        override fun disconnect() { closed = true }
        override fun usingProxy() = false
        override fun getResponseCode() = code
        override fun getInputStream() = ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
    }

    @Test fun convertsAllAdlFilePagesToRawDownloads() {
        for (name in listOf("aosp", "carrier", "google", "misc", "oem")) {
            assertEquals("https://raw.githubusercontent.com/MuntashirAkon/android-debloat-list/master/$name.json",
                ListDownload.rawUrl("https://github.com/MuntashirAkon/android-debloat-list/blob/master/$name.json?raw=true#L1"))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsRepositoryPagesInsteadOfTreatingHtmlAsJson() {
        ListDownload.rawUrl("https://github.com/Universal-Debloater-Alliance/universal-android-preinstalled-lists")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsHttpLinks() { ListDownload.rawUrl("http://example.com/list.json") }

    @Test fun fallsBackToRawContentsApiWhenRawHostFails() {
        val responses = mutableListOf<Response>()
        val text = ListDownload.read("https://raw.githubusercontent.com/MuntashirAkon/android-debloat-list/master/aosp.json", "test") { url ->
            Response(url, if (responses.isEmpty()) 503 else 200).also { responses += it }
        }
        assertEquals("[]", text)
        assertEquals("https://api.github.com/repos/MuntashirAkon/android-debloat-list/contents/aosp.json?ref=master", responses[1].url.toString())
        assertEquals("application/vnd.github.raw+json", responses[1].getRequestProperty("Accept"))
        assertTrue(responses.all { it.closed })
    }

    @Test fun rawSuccessDoesNotConsumeApiQuotaAndAcceptsBom() {
        var calls = 0
        assertEquals("[]", ListDownload.read("https://raw.githubusercontent.com/o/r/main/list.json", "test") { url ->
            calls++
            Response(url, body = "\uFEFF[]")
        })
        assertEquals(1, calls)
    }

    @Test fun reportsBothFailingUrlsAndRejectsHtmlWithStatus200() {
        try {
            ListDownload.read("https://raw.githubusercontent.com/o/r/main/list.json", "test") { url ->
                Response(url, body = "<!DOCTYPE html><title>Access denied</title>")
            }
            fail("HTML must not replace list data")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("raw.githubusercontent.com"))
            assertTrue(e.message!!.contains("api.github.com"))
            assertTrue(e.message!!.contains("Expected JSON"))
        }
    }
}
