// SPDX-License-Identifier: Apache-2.0
package lx.compat

import java.net.InetAddress
import java.net.URI

/**
 * Admission for script HTTP. Cleartext HTTP is refused here even though preload's musicUrl check
 * still accepts `http:`. That split is intentional and tested: the script-visible string check is
 * unchanged, and this process will not open a cleartext socket for a script.
 *
 * Address checks parse numeric literals only. They do not call a resolver.
 */
object Addresses {
    fun parse(literal: String): InetAddress? {
        val bytes = ipv4(literal) ?: ipv6(literal) ?: return null
        return InetAddress.getByAddress(bytes)
    }

    fun isPublic(literal: String): Boolean {
        val address = parse(literal) ?: return false
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress
        ) return false
        val a = address.address.map { it.toInt() and 255 }
        if (a.size == 4) {
            val x = a[0]
            val y = a[1]
            val blocked = x == 0 || x == 10 || x == 127 || x >= 224 ||
                (x == 169 && y == 254) ||
                (x == 172 && y in 16..31) ||
                (x == 192 && (y == 168 || y == 0 || y == 2)) ||
                (x == 198 && (y == 18 || y == 19 || y == 51)) ||
                (x == 203 && y == 0) ||
                (x == 100 && y in 64..127)
            return !blocked
        }
        if (a.size != 16 || a[0] and 0xE0 != 0x20) return false
        if (a[0] == 0x20 && a[1] == 0x01 && ((a[2] == 0x0D && a[3] == 0xB8) || (a[2] == 0 && a[3] == 0))) return false
        if (a[0] == 0x20 && a[1] == 0x02) return false
        return true
    }

    private fun ipv4(literal: String): ByteArray? {
        val parts = literal.split('.')
        if (parts.size != 4) return null
        val out = ByteArray(4)
        for (i in 0..3) {
            val part = parts[i]
            if (part.isEmpty() || part.length > 3) return null
            if (part.length > 1 && part[0] == '0') return null
            if (part.any { it !in '0'..'9' }) return null
            val n = part.toInt()
            if (n > 255) return null
            out[i] = n.toByte()
        }
        return out
    }

    private fun ipv6(literal: String): ByteArray? {
        if (literal.isEmpty() || '.' in literal || '%' in literal || literal.count { it == ':' } < 2) return null
        if (literal.indexOf("::") != literal.lastIndexOf("::")) return null
        val halves = literal.split("::", limit = 2)
        val head = if (halves[0].isEmpty()) emptyList() else halves[0].split(':')
        val tail = if (halves.size == 1 || halves[1].isEmpty()) emptyList() else halves[1].split(':')
        if (head.size + tail.size > 8) return null
        if (halves.size == 1 && head.size != 8) return null
        val groups = ArrayList<Int>(8)
        if (!appendGroups(head, groups)) return null
        if (halves.size == 2) {
            val missing = 8 - head.size - tail.size
            if (missing < 1 && !(literal == "::")) return null
            repeat(missing.coerceAtLeast(0)) { groups.add(0) }
            if (!appendGroups(tail, groups)) return null
        }
        if (groups.size != 8) return null
        val out = ByteArray(16)
        for (i in groups.indices) {
            out[i * 2] = (groups[i] shr 8).toByte()
            out[i * 2 + 1] = groups[i].toByte()
        }
        return out
    }

    private fun appendGroups(parts: List<String>, out: MutableList<Int>): Boolean {
        for (part in parts) {
            if (part.isEmpty() || part.length > 4 || part.any { it !in "0123456789abcdefABCDEF" }) return false
            out.add(part.toInt(16))
        }
        return true
    }
}

data class Admission(
    val uri: URI,
    val host: String,
    val method: String,
    val headers: List<Pair<String, String>>,
    val body: ByteArray?,
    val timeoutMs: Long,
    val binary: Boolean,
)

class PolicyRejected(message: String) : Exception(message)

class NetworkPolicy(
    private val allowHosts: Set<String>? = null,
    private val resolve: (String) -> List<String>,
) {
    fun admit(url: String, method: String, headers: List<Pair<String, String>>, body: ByteArray?, timeoutMs: Long, binary: Boolean): Admission {
        if (url.length > 2048) throw PolicyRejected("URL length")
        val uri = try {
            URI(url)
        } catch (e: Exception) {
            throw PolicyRejected("URL")
        }
        if (uri.scheme != "https" || uri.rawUserInfo != null || uri.rawFragment != null) throw PolicyRejected("URL scheme")
        if (uri.port != -1 && uri.port != 443) throw PolicyRejected("URL port")
        val host = uri.host?.lowercase() ?: throw PolicyRejected("URL host")
        if (!host.matches(Regex("[a-z0-9]([a-z0-9-]{0,62}[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]{0,62}[a-z0-9])?)+"))) {
            throw PolicyRejected("URL host")
        }
        if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local") || host.endsWith(".internal")) {
            throw PolicyRejected("URL host")
        }
        if (Addresses.parse(host) != null && !Addresses.isPublic(host)) throw PolicyRejected("Non-public address")
        if (allowHosts != null && host !in allowHosts) throw PolicyRejected("Host not approved")
        val verb = method.uppercase()
        if (verb !in setOf("GET", "POST")) throw PolicyRejected("Method")
        if (verb == "GET" && body != null) throw PolicyRejected("GET body")
        if (body != null && body.size > 32_768) throw PolicyRejected("Body size")
        if (timeoutMs !in 1..60_000) throw PolicyRejected("Timeout")
        if (headers.size > 24) throw PolicyRejected("Header count")
        val clean = ArrayList<Pair<String, String>>(headers.size)
        for ((name, value) in headers) {
            val key = name.lowercase()
            if (!key.matches(Regex("[a-z0-9-]{1,64}"))) throw PolicyRejected("Header name")
            if (key in FORBIDDEN_HEADERS) throw PolicyRejected("Header $key")
            if (value.length > 4096 || '\r' in value || '\n' in value) throw PolicyRejected("Header value")
            clean.add(key to value)
        }
        return Admission(uri, host, verb, clean, body, timeoutMs, binary)
    }

    fun publicTargets(host: String): List<String> {
        val literals = if (Addresses.parse(host) != null) listOf(host) else resolve(host)
        if (literals.isEmpty() || literals.size > 16) throw PolicyRejected("DNS")
        val accepted = ArrayList<String>(literals.size)
        for (literal in literals) {
            if (Addresses.parse(literal) == null) throw PolicyRejected("DNS literal")
            if (!Addresses.isPublic(literal)) throw PolicyRejected("Non-public address")
            accepted.add(literal)
        }
        if (accepted.isEmpty()) throw PolicyRejected("Non-public address")
        return accepted
    }

    companion object {
        val FORBIDDEN_HEADERS = setOf(
            "host", "content-length", "transfer-encoding", "connection", "upgrade", "te", "trailer",
            "keep-alive", "proxy-authorization", "proxy-connection", "proxy-authenticate",
        )
    }
}

enum class Finish { OK, LATE, STALE, UNKNOWN }

/**
 * Host-owned request identity. A reply is applied only when the ticket is still the pending one,
 * the session generation still matches, and the deadline has not passed. Late tickets are removed
 * so they cannot pin the pending quota.
 */
class RequestLedger(private val maxPending: Int = 8, private val maxTotal: Int = 64) {
    data class Ticket(val key: String, val generation: Long, val deadlineMs: Long)

    private var generation = 1L
    private var total = 0
    private val pending = LinkedHashMap<String, Ticket>()

    @Synchronized fun begin(key: String, now: Long, timeout: Long): Ticket {
        require(timeout in 1..60_000 && key.length in 1..128)
        if (total >= maxTotal || pending.size >= maxPending || pending.containsKey(key)) error("Request quota")
        total++
        return Ticket(key, generation, now + timeout).also { pending[key] = it }
    }

    @Synchronized fun cancel(key: String): Boolean = pending.remove(key) != null

    @Synchronized fun complete(ticket: Ticket, now: Long): Finish {
        val active = pending[ticket.key] ?: return Finish.UNKNOWN
        if (active != ticket || ticket.generation != generation) return Finish.STALE
        pending.remove(ticket.key)
        return if (now < ticket.deadlineMs) Finish.OK else Finish.LATE
    }

    @Synchronized fun reset() {
        generation++
        pending.clear()
        total = 0
    }

    @Synchronized fun size(): Int = pending.size

    @Synchronized fun generation(): Long = generation

    @Synchronized fun total(): Int = total
}

/** musicUrl strings the preload accepts are not automatically playable. */
object PlaybackUrlPolicy {
    fun admit(url: String): String {
        if (url.length > 2048 || !url.startsWith("https://")) {
            throw PolicyRejected("Playback URL")
        }
        val uri = URI(url)
        if (uri.rawUserInfo != null || uri.host.isNullOrBlank()) throw PolicyRejected("Playback URL")
        if (uri.scheme != "https") throw PolicyRejected("Playback URL")
        return url
    }
}
