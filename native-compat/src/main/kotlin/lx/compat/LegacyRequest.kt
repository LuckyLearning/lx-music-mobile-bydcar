// SPDX-License-Identifier: Apache-2.0
package lx.compat

/**
 * Request shaping copied from src/core/init/userApi/request.js, with two explicit divergences:
 * formData is rejected instead of following the original loop (that loop reads `form` after it may
 * already have been removed), and a GET with a body is rejected instead of being forwarded.
 */
object LegacyRequest {
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36"

    data class Prepared(
        val method: String,
        val headers: List<Pair<String, String>>,
        val body: ByteArray?,
        val binary: Boolean,
        val timeoutMs: Long,
        val rejected: String? = null,
    )

    fun prepare(options: Map<String, Any?>): Prepared {
        if (options["formData"] != null) {
            return Prepared("GET", emptyList(), null, false, 13_000, "multipart formData is not supported")
        }
        val methodRaw = (options["method"] as? String) ?: "get"
        val headers = LinkedHashMap<String, Any?>()
        headers["Accept"] = "application/json"
        val supplied = options["headers"]?.obj() ?: emptyMap()
        for ((key, value) in supplied) headers[key] = value
        var body: Any? = options["body"]
        val post = methodRaw.lowercase() == "post"
        if (post && !headers.containsKey("Content-Type")) {
            val form = options["form"]?.obj()
            if (form != null) {
                headers["Content-Type"] = "application/x-www-form-urlencoded"
                body = form.entries.joinToString("&") { (key, value) ->
                    encodeURIComponent(key) + "=" + encodeURIComponent(jsString(value))
                }
            } else {
                headers["Content-Type"] = "application/json"
            }
        }
        if (headers["Content-Type"] == "application/json" && body != null) {
            body = Json.stringify(body)
        }
        val timeout = when (val value = options["timeout"]) {
            null -> 13_000L
            is Number -> value.toLong()
            else -> return Prepared(methodRaw, emptyList(), null, false, 13_000, "timeout")
        }
        if (timeout !in 1..60_000) return Prepared(methodRaw, emptyList(), null, false, timeout, "timeout")
        val wire = LinkedHashMap<String, String>()
        putHeader(wire, "User-Agent", USER_AGENT)
        for ((key, value) in headers) {
            val text = when (value) {
                is String -> value
                is Number, is Boolean -> jsString(value)
                else -> return Prepared(methodRaw, emptyList(), null, false, timeout, "header value")
            }
            putHeader(wire, key, text)
        }
        val bytes = when (body) {
            null -> null
            is String -> body.toByteArray(Charsets.UTF_8)
            else -> return Prepared(methodRaw, emptyList(), null, false, timeout, "non-json object body is not supported")
        }
        if (!post && bytes != null) {
            return Prepared(methodRaw, emptyList(), null, false, timeout, "GET body")
        }
        return Prepared(
            method = methodRaw,
            headers = wire.toList(),
            body = bytes,
            binary = options["binary"] == true,
            timeoutMs = timeout,
        )
    }

    private fun putHeader(target: LinkedHashMap<String, String>, name: String, value: String) {
        val existing = target.keys.firstOrNull { it.equals(name, ignoreCase = true) }
        if (existing != null && existing != name) target.remove(existing)
        target[name] = value
    }

    fun encodeURIComponent(value: String): String {
        val bytes = value.toByteArray(Charsets.UTF_8)
        val out = StringBuilder(bytes.size * 3)
        for (byte in bytes) {
            val n = byte.toInt() and 255
            val ch = n.toChar()
            if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch in "-_.!~*'()") out.append(ch)
            else out.append("%%%02X".format(n))
        }
        return out.toString()
    }

    /** JSON shape left after the old RN bridge stringified a Buffer and QuickJS parsed it. */
    fun binaryScriptBody(bytes: ByteArray): Map<String, Any?> {
        if (bytes.size > 262_144) throw TransportException("body limit")
        return linkedMapOf("type" to "Buffer", "data" to bytes.map { it.toInt() and 255 })
    }

    fun jsString(value: Any?): String = when (value) {
        null -> "null"
        is String -> value
        is Boolean -> value.toString()
        is Double -> if (value.isFinite() && value % 1.0 == 0.0 && value <= Long.MAX_VALUE.toDouble() && value >= Long.MIN_VALUE.toDouble()) {
            value.toLong().toString()
        } else value.toString()
        is Number -> value.toString()
        else -> "[object Object]"
    }
}
