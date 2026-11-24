// SPDX-License-Identifier: Apache-2.0
package lx.compat

/**
 * Parent-side preload bridge. The isolated process executes JavaScript and calls back here for
 * crypto, timers, and HTTP. The script cannot change [policy].
 * Trace lines never include header values, script text, or request keys.
 */
class AndroidHost(
    private val policy: NetworkPolicy,
    private val transport: (Admission) -> Exchange = { PinnedHttps(policy, TlsConnector).execute(it) },
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val lock = Any()
    private val notes = StringBuilder()
    private val queued = ArrayList<Pending>()
    private val delivered = HashSet<String>()
    private val ledger = RequestLedger()
    private var commandId = 1L
    private var draining = false
    var playbackUrl: String? = null
        private set

    private class Pending(val key: String, val ticket: RequestLedger.Ticket, val url: String, val options: Map<String, Any?>, var cancelled: Boolean = false)

    fun onRpc(text: String): String {
        val msg = Json.parse(text).obj()
        val id = msg["id"].num()
        val fn = msg["fn"].str()
        val args = msg["args"].list()
        return try {
            when (fn) {
                "native" -> {
                    handleNative(args.getOrNull(1).str(), Json.parse(args.getOrNull(2).str()))
                    reply(id, true, null)
                }
                "timer" -> {
                    note("timer id=${args[0].num()} ms=${args[1].num()}")
                    reply(id, true, null)
                }
                "str2b64", "b642buf", "md5", "aes", "rsa" -> {
                    val value = LegacyCrypto.invoke(fn, args)
                    note("crypto $fn len=${(value as? String)?.length ?: 0}")
                    reply(id, true, value)
                }
                else -> reply(id, false, null)
            }
        } catch (_: InputQuotaError) {
            reply(id, false, null)
        } catch (_: Exception) {
            reply(id, false, null)
        }
    }

    fun runPreload(send: (String) -> String, preload: String, script: String, request: String?): String {
        synchronized(lock) {
            notes.setLength(0)
            queued.clear()
            delivered.clear()
            playbackUrl = null
        }
        ledger.reset()
        command(send, mapOf("op" to "preload", "code" to preload))
        command(send, linkedMapOf(
            "op" to "setup", "key" to "gate-key", "scriptId" to "sample", "name" to "sample",
            "description" to "", "version" to "1", "author" to "", "homepage" to "", "rawScript" to "omitted",
        ))
        command(send, mapOf("op" to "eval", "code" to script))
        if (request != null) command(send, mapOf("op" to "call", "action" to "request", "data" to request))
        return trace()
    }

    @Synchronized fun trace(): String = notes.toString()

    private fun command(send: (String) -> String, fields: Map<String, Any?>) {
        val id = commandId++
        val payload = LinkedHashMap<String, Any?>(fields)
        payload["id"] = id
        val raw = send(Json.stringify(payload))
        val result = Json.parse(raw).obj()
        if (result["ok"] != true && result["error"] != null) note("command-rejected op=${fields["op"]} error=${result["error"]}")
        drain(send)
    }

    private fun drain(send: (String) -> String) {
        synchronized(lock) {
            if (draining) return
            draining = true
        }
        var guard = 0
        try {
        while (true) {
            if (++guard > 8) { note("http-cascade-stop"); return }
            val item = synchronized(lock) { if (queued.isEmpty()) null else queued.removeAt(0) } ?: break
            if (item.key in delivered) continue
            if (item.cancelled || item.ticket.generation < 0) {
                deliver(send, item.key, if (item.ticket.generation < 0) "request quota" else "request canceled", null)
                continue
            }
            val prepared = LegacyRequest.prepare(item.options)
            if (prepared.rejected != null) {
                ledger.complete(item.ticket, clock())
                note("http-rejected reason=${prepared.rejected}")
                deliver(send, item.key, prepared.rejected, null)
                continue
            }
            val admission = try {
                val admitted = policy.admit(item.url, prepared.method, prepared.headers, prepared.body, prepared.timeoutMs, prepared.binary)
                policy.publicTargets(admitted.host)
                note("http host=${admitted.host} method=${admitted.method}")
                admitted
            } catch (e: PolicyRejected) {
                ledger.complete(item.ticket, clock())
                note("http-rejected host=${safeHost(item.url)} reason=${e.message}")
                deliver(send, item.key, e.message ?: "policy", null)
                continue
            }
            if (ledger.complete(item.ticket, clock()) != Finish.OK) {
                deliver(send, item.key, "request canceled", null)
                continue
            }
            val exchange = try {
                transport(admission)
            } catch (e: Exception) {
                note("http-failed host=${admission.host} type=${e.javaClass.simpleName} detail=${e.message?.take(80) ?: ""}")
                deliver(send, item.key, "request failed", null)
                continue
            }
            note("http-result host=${admission.host} status=${exchange.statusCode} bytes=${exchange.body.size}")
            val response = try {
                scriptResponse(prepared, exchange)
            } catch (e: TransportException) {
                deliver(send, item.key, e.message ?: "body limit", null)
                continue
            }
            deliver(send, item.key, null, response)
        }
        } finally {
            synchronized(lock) { draining = false }
        }
    }

    private fun scriptResponse(prepared: LegacyRequest.Prepared, exchange: Exchange): Map<String, Any?> {
        val body: Any? = if (prepared.binary) LegacyRequest.binaryScriptBody(exchange.body) else {
            val text = exchange.body.toString(Charsets.UTF_8)
            try { Json.parse(text) } catch (_: Exception) { text }
        }
        return linkedMapOf("statusCode" to exchange.statusCode, "statusMessage" to exchange.statusMessage, "headers" to exchange.headers, "body" to body)
    }

    private fun deliver(send: (String) -> String, key: String, error: String?, response: Map<String, Any?>?) {
        if (!delivered.add(key)) return
        command(send, mapOf("op" to "call", "action" to "response", "data" to Json.stringify(linkedMapOf("requestKey" to key, "error" to error, "response" to response))))
    }

    private fun handleNative(action: String, data: Any?) {
        when (action) {
            "init" -> {
                val map = data as? Map<*, *>
                val sources = (map?.get("info") as? Map<*, *>)?.get("sources") as? Map<*, *>
                note("init status=${map?.get("status")} sources=${sources?.keys?.joinToString(",") ?: "none"}")
            }
            "request" -> {
                val map = data.obj()
                val key = map["requestKey"].str()
                val url = map["url"].str()
                val options = map["options"]?.obj() ?: emptyMap()
                val timeout = (options["timeout"] as? Number)?.toLong() ?: 13_000L
                val ticket = try {
                    ledger.begin(key, clock(), timeout)
                } catch (_: IllegalStateException) {
                    synchronized(lock) { queued.add(Pending(key, RequestLedger.Ticket(key, -1, 0), url, options, true)) }
                    return
                }
                synchronized(lock) { queued.add(Pending(key, ticket, url, options)) }
            }
            "cancelRequest" -> {
                val key = data.str()
                ledger.cancel(key)
                synchronized(lock) { queued.filter { it.key == key }.forEach { it.cancelled = true } }
                note("cancel")
            }
            "response" -> {
                val map = data.obj()
                val err = map["errorMessage"]?.toString()?.take(80)
                val url = (map["result"] as? Map<*, *>)?.get("data").let { dataNode ->
                    when (dataNode) {
                        is Map<*, *> -> dataNode["url"]?.toString()
                        else -> null
                    }
                }
                if (!url.isNullOrEmpty()) playbackUrl = url
                note("script-response status=${map["status"]} error=$err urlHost=${url?.let { safeHost(it) }}")
            }
            "showUpdateAlert" -> {
                val log = (data as? Map<*, *>)?.get("log")?.toString() ?: ""
                if (log.length <= 200 && log.all { it.isLetterOrDigit() || it in "=;._-" }) note("alert $log")
                else note("alert-rejected")
            }
            else -> note("event $action")
        }
    }

    private fun safeHost(url: String): String = try {
        java.net.URI(url).host ?: "none"
    } catch (_: Exception) {
        "none"
    }

    private fun note(line: String) {
        synchronized(lock) { if (notes.length < 4000) notes.append(line).append('\n') }
    }

    private fun reply(id: Long, ok: Boolean, value: Any?): String =
        Json.stringify(linkedMapOf("id" to id, "ok" to ok, "value" to value))
}
