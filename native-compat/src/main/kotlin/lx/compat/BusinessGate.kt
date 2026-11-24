// SPDX-License-Identifier: Apache-2.0
package lx.compat

/**
 * App-layer request map from src/core/init/userApi/index.ts.
 * The 20s budget is independent of the script HTTP timeout. The original musicUrl/lyric/pic
 * canceleFn is empty; this gate actually retires the ticket and ignores a later result.
 * A source update fails every pending call with "request failed", matching handleApiUpdate.
 */
class BusinessGate(private val outerTimeoutMs: Long = 20_000) {
    data class Pending(val key: String, val deadlineMs: Long)

    private val pending = LinkedHashMap<String, Pending>()

    fun start(key: String, now: Long) {
        pending[key] = Pending(key, now + outerTimeoutMs)
    }

    fun succeed(key: String, now: Long, result: Any?): String? {
        val item = pending.remove(key) ?: return null
        if (now >= item.deadlineMs) return "request timeout"
        return "ok:$result"
    }

    fun fail(key: String, now: Long, message: String): String? {
        val item = pending.remove(key) ?: return null
        if (now >= item.deadlineMs) return "request timeout"
        return message
    }

    fun advance(now: Long): List<String> {
        val expired = pending.values.filter { now >= it.deadlineMs }.map { it.key }
        expired.forEach { pending.remove(it) }
        return expired.map { "request timeout" }
    }

    fun onSourceUpdated(): List<String> {
        val keys = pending.keys.toList()
        pending.clear()
        return keys.map { "request failed" }
    }

    fun size(): Int = pending.size
}
