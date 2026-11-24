// SPDX-License-Identifier: Apache-2.0
package lx.compat

import java.io.ByteArrayInputStream
import java.io.Closeable
import java.net.InetSocketAddress
import java.net.Socket
import java.util.zip.GZIPInputStream
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

interface ByteConn : Closeable {
    val peerIp: String
    fun writeFully(bytes: ByteArray)
    /** Positive count, or -1 at EOF. */
    fun read(buf: ByteArray): Int
}

fun interface Connector {
    fun open(host: String, ip: String, port: Int, tls: Boolean, timeoutMs: Int): ByteConn
}

data class Exchange(
    val statusCode: Int,
    val statusMessage: String,
    val headers: Map<String, String>,
    val body: ByteArray,
    val peerIps: List<String>,
)

class TransportException(message: String) : Exception(message)

/**
 * HTTPS/1.1 client. The TCP peer is one of the numeric addresses [NetworkPolicy.publicTargets]
 * already returned. Redirects run the admission and resolver again.
 */
class PinnedHttps(
    private val policy: NetworkPolicy,
    private val connector: Connector,
    private val maxBodyBytes: Int = 1_048_576,
    private val maxRedirects: Int = 4,
) {
    fun execute(admission: Admission): Exchange {
        var current = admission
        var method = admission.method
        var body = admission.body
        val seen = ArrayList<String>()
        for (hop in 0..maxRedirects) {
            val ips = policy.publicTargets(current.host)
            var last: Exception? = null
            var exchange: Exchange? = null
            for (ip in ips) {
                try {
                    exchange = one(current, method, body, ip)
                    break
                } catch (e: Exception) {
                    last = e
                }
            }
            val got = exchange ?: throw TransportException(last?.message ?: "connect")
            seen.addAll(got.peerIps)
            val location = got.headers["location"]
            val redirect = got.statusCode in setOf(301, 302, 303, 307, 308) && !location.isNullOrEmpty()
            if (!redirect) return got.copy(peerIps = seen)
            if (hop == maxRedirects) throw TransportException("redirect limit")
            val nextUrl = try {
                current.uri.resolve(location).toString()
            } catch (e: Exception) {
                throw TransportException("redirect")
            }
            if (got.statusCode == 301 || got.statusCode == 302 || got.statusCode == 303) {
                method = "GET"
                body = null
            }
            current = policy.admit(nextUrl, method, current.headers, body, current.timeoutMs, current.binary)
        }
        throw TransportException("redirect limit")
    }

    private fun one(admission: Admission, method: String, body: ByteArray?, ip: String): Exchange {
        val tls = admission.uri.scheme == "https"
        val port = if (admission.uri.port != -1) admission.uri.port else if (tls) 443 else 80
        connector.open(admission.host, ip, port, tls, admission.timeoutMs.toInt()).use { conn ->
            val bytes = render(admission, method, body)
            conn.writeFully(bytes)
            if (body != null && method != "GET") conn.writeFully(body)
            val cursor = Cursor(conn)
            val exchange = readResponse(cursor)
            return exchange.copy(peerIps = listOf(conn.peerIp))
        }
    }

    private fun render(admission: Admission, method: String, body: ByteArray?): ByteArray {
        val path = admission.uri.rawPath?.takeIf { it.isNotEmpty() } ?: "/"
        val query = admission.uri.rawQuery
        val target = if (query.isNullOrEmpty()) path else "$path?$query"
        val out = StringBuilder()
        out.append(method).append(' ').append(target).append(" HTTP/1.1\r\n")
        out.append("Host: ").append(admission.host).append("\r\n")
        val names = admission.headers.map { it.first }.toSet()
        for ((name, value) in admission.headers) out.append(name).append(": ").append(value).append("\r\n")
        if ("user-agent" !in names) out.append("User-Agent: ").append(LegacyRequest.USER_AGENT).append("\r\n")
        if ("accept" !in names) out.append("Accept: application/json\r\n")
        if ("accept-encoding" !in names) out.append("Accept-Encoding: identity\r\n")
        out.append("Connection: close\r\n")
        if (body != null && method != "GET") out.append("Content-Length: ").append(body.size).append("\r\n")
        out.append("\r\n")
        return out.toString().toByteArray(Charsets.ISO_8859_1)
    }

    private fun readResponse(cursor: Cursor): Exchange {
        val status = cursor.line(8 * 1024)
        val match = Regex("HTTP/1\\.[01] (\\d{3})(?: (.*))?").matchEntire(status) ?: throw TransportException("status")
        val code = match.groupValues[1].toInt()
        val reason = match.groupValues[2]
        val headers = linkedMapOf<String, String>()
        while (true) {
            val line = cursor.line(8 * 1024)
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            if (colon <= 0) throw TransportException("header")
            val name = line.substring(0, colon).trim().lowercase()
            val value = line.substring(colon + 1).trim()
            headers[name] = if (name in headers) headers.getValue(name) + ", " + value else value
        }
        val skipBody = code in setOf(301, 302, 303, 307, 308)
        val raw = when {
            skipBody -> ByteArray(0)
            headers["transfer-encoding"]?.contains("chunked", ignoreCase = true) == true -> readChunked(cursor)
            headers["content-length"] != null -> {
                val len = headers.getValue("content-length").toIntOrNull() ?: throw TransportException("length")
                if (len !in 0..maxBodyBytes) throw TransportException("body limit")
                cursor.take(len)
            }
            else -> cursor.rest(maxBodyBytes)
        }
        val encoding = headers["content-encoding"]?.lowercase()
        val decoded = when (encoding) {
            null, "", "identity" -> raw
            "gzip" -> if (skipBody) raw else inflate(raw)
            else -> throw TransportException("content-encoding")
        }
        if (decoded.size > maxBodyBytes) throw TransportException("body limit")
        headers.remove("transfer-encoding")
        headers.remove("connection")
        if (encoding == "gzip") headers.remove("content-encoding")
        return Exchange(code, reason, headers, decoded, emptyList())
    }

    private fun inflate(body: ByteArray): ByteArray {
        GZIPInputStream(ByteArrayInputStream(body)).use { input ->
            val out = ByteArray(maxBodyBytes + 1)
            var used = 0
            while (true) {
                val n = input.read(out, used, out.size - used)
                if (n < 0) return out.copyOf(used)
                used += n
                if (used > maxBodyBytes) throw TransportException("body limit")
            }
        }
    }

    private fun readChunked(cursor: Cursor): ByteArray {
        val parts = ArrayList<ByteArray>()
        var total = 0
        while (true) {
            val size = cursor.line(4096).substringBefore(';').trim().toIntOrNull(16) ?: throw TransportException("chunk size")
            if (size == 0) {
                while (cursor.line(4096).isNotEmpty()) Unit
                break
            }
            if (size < 0 || total.toLong() + size > maxBodyBytes) throw TransportException("body limit")
            parts.add(cursor.take(size))
            total += size
            val crlf = cursor.take(2)
            if (crlf[0] != 13.toByte() || crlf[1] != 10.toByte()) throw TransportException("chunk")
        }
        val out = ByteArray(total)
        var at = 0
        for (part in parts) {
            part.copyInto(out, at)
            at += part.size
        }
        return out
    }

    private class Cursor(val conn: ByteConn) {
        private var data = ByteArray(0)
        private var at = 0
        private fun fill() {
            if (at < data.size) return
            val tmp = ByteArray(2048)
            val got = conn.read(tmp)
            if (got < 0) throw TransportException("eof")
            data = if (got == 0) ByteArray(0) else tmp.copyOf(got)
            at = 0
            if (got == 0) throw TransportException("eof")
        }
        fun line(limit: Int): String {
            val out = ArrayList<Byte>()
            while (true) {
                fill()
                val b = data[at++]
                if (b == 10.toByte()) break
                if (b != 13.toByte()) out.add(b)
                if (out.size > limit) throw TransportException("line limit")
            }
            return ByteArray(out.size) { out[it] }.toString(Charsets.ISO_8859_1)
        }
        fun take(n: Int): ByteArray {
            val out = ByteArray(n)
            var used = 0
            while (used < n) {
                fill()
                val take = minOf(n - used, data.size - at)
                data.copyInto(out, used, at, at + take)
                at += take
                used += take
            }
            return out
        }
        fun rest(limit: Int): ByteArray {
            val parts = ArrayList<ByteArray>()
            var total = 0
            if (at < data.size) {
                parts.add(data.copyOfRange(at, data.size))
                total += data.size - at
                at = data.size
            }
            val tmp = ByteArray(2048)
            while (true) {
                val got = conn.read(tmp)
                if (got < 0) break
                if (got == 0) continue
                total += got
                if (total > limit) throw TransportException("body limit")
                parts.add(tmp.copyOf(got))
            }
            val out = ByteArray(total)
            var pos = 0
            for (part in parts) {
                part.copyInto(out, pos)
                pos += part.size
            }
            return out
        }
    }
}

object TlsConnector : Connector {
    override fun open(host: String, ip: String, port: Int, tls: Boolean, timeoutMs: Int): ByteConn {
        if (!tls || port != 443) throw TransportException("tls required")
        if (!Addresses.isPublic(ip)) throw TransportException("pin")
        val tcp = Socket()
        tcp.connect(InetSocketAddress(ip, 443), timeoutMs)
        tcp.soTimeout = timeoutMs
        val ssl = (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(tcp, host, 443, true) as SSLSocket
        val params = ssl.sslParameters
        params.serverNames = listOf(SNIHostName(host))
        params.endpointIdentificationAlgorithm = "HTTPS"
        ssl.sslParameters = params
        ssl.startHandshake()
        return object : ByteConn {
            override val peerIp = ip
            override fun writeFully(bytes: ByteArray) {
                ssl.outputStream.write(bytes)
                ssl.outputStream.flush()
            }
            override fun read(buf: ByteArray): Int = ssl.inputStream.read(buf)
            override fun close() {
                try {
                    ssl.close()
                } catch (_: Exception) {
                }
            }
        }
    }
}
