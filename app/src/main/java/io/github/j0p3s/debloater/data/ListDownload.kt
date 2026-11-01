package io.github.j0p3s.debloater.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** GitHub's /blob/ pages are HTML. Always request the file bytes instead. */
internal object ListDownload {
    fun rawUrl(link: String): String {
        val uri = URI(link)
        require(uri.scheme == "https" && uri.userInfo == null) { "Only HTTPS downloads are allowed" }
        if (uri.host != "github.com") return link
        val parts = uri.path.trim('/').split('/')
        require(parts.size >= 5 && parts[2] in listOf("blob", "raw")) {
            "Expected a GitHub file link, not a repository or directory: $link"
        }
        return "https://raw.githubusercontent.com/${parts[0]}/${parts[1]}/${parts.drop(3).joinToString("/")}"
    }

    private fun apiUrl(raw: String): String? {
        val uri = URI(raw)
        if (uri.host != "raw.githubusercontent.com") return null
        val parts = uri.path.trim('/').split('/')
        if (parts.size < 4) return null
        return "https://api.github.com/repos/${parts[0]}/${parts[1]}/contents/${parts.drop(3).joinToString("/")}?ref=${parts[2]}"
    }

    fun read(
        link: String,
        userAgent: String,
        connect: (String) -> HttpURLConnection = { URL(it).openConnection() as HttpURLConnection },
    ): String {
        val raw = rawUrl(link)
        try {
            return request(raw, userAgent, connect)
        } catch (first: IOException) {
            // Some networks block raw.githubusercontent.com while allowing GitHub's API.
            val fallback = apiUrl(raw) ?: throw first
            try {
                return request(fallback, userAgent, connect)
            } catch (second: IOException) {
                throw IOException("${first.message}; fallback: ${second.message}", second)
            }
        }
    }

    private fun request(url: String, userAgent: String, connect: (String) -> HttpURLConnection): String {
        try {
            val c = connect(url)
            try {
                c.connectTimeout = 15_000
                c.readTimeout = 30_000
                c.setRequestProperty("User-Agent", userAgent)
                c.setRequestProperty("Accept", "application/vnd.github.raw+json")
                val code = c.responseCode
                if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code")
                val text = c.inputStream.use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        if (out.size().toLong() + n > 16L * 1024 * 1024) throw IOException("File too large")
                        out.write(buf, 0, n)
                    }
                    out.toString(Charsets.UTF_8.name()).removePrefix("\uFEFF")
                }
                if (text.trimStart().firstOrNull() !in listOf('{', '[')) {
                    throw IOException("Expected JSON list data, received a web page or empty response")
                }
                return text
            } finally {
                c.disconnect()
            }
        } catch (e: IOException) {
            throw IOException("$url: ${e.message ?: e.javaClass.simpleName}", e)
        }
    }
}
