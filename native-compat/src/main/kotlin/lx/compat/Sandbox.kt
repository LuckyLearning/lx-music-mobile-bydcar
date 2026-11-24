// SPDX-License-Identifier: Apache-2.0
package lx.compat

import java.io.File
import java.io.InputStream
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class ScriptInfo(
    val key: String,
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val author: String,
    val homepage: String,
    val rawScript: String,
    val allowShowUpdateAlert: Boolean = false,
)

data class HostEvent(val action: String, val data: Map<String, Any?>)

class SandboxException(message: String) : Exception(message)

/**
 * Parent of the QuickJS runner. The child has no sockets. HTTP, crypto and timers are answered
 * here. A wall-clock thread kills the child if the session budget expires; each wait for a child
 * frame also has its own deadline so a blocked read cannot wait forever.
 */
class ScriptSandbox(
    private val runner: File,
    private val preload: File,
    private val transport: (Admission) -> Exchange,
    private val policy: NetworkPolicy,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val childSessionMs: Int = 8_000,
    private val childCpuMs: Int = 2_000,
    private val replyTimeoutMs: Long = 6_000,
    private val parentBudgetMs: Long = 12_000,
    private val info: ScriptInfo = ScriptInfo("gate-key", "id", "name", "", "0", "", "", ""),
) : AutoCloseable {
    val events = ArrayList<HostEvent>()
    val httpAttempts = ArrayList<String>()
    private val delivered = HashSet<String>()
    private val queued = ArrayList<PendingHttp>()
    private val ledger = RequestLedger()
    private val lines = ArrayBlockingQueue<String>(128)
    private val stdoutEof = AtomicBoolean(false)
    private val stderr = StringBuilder()
    private var process: Process? = null
    private var nextCommand = 1L
    private var closed = false
    @Volatile private var killForBudget = false

    private class PendingHttp(
        val key: String,
        val ticket: RequestLedger.Ticket,
        val url: String,
        val options: Map<String, Any?>,
        var cancelled: Boolean = false,
    )

    fun start() {
        val command = ArrayList<String>()
        command.add("bwrap")
        command.addAll(listOf("--unshare-all", "--die-with-parent", "--new-session", "--cap-drop", "ALL", "--clearenv"))
        for (path in listOf("/usr", "/lib", "/lib64")) {
            if (File(path).isDirectory) command.addAll(listOf("--ro-bind", path, path))
        }
        command.addAll(listOf("--proc", "/proc", "--dev", "/dev"))
        command.addAll(listOf("--ro-bind", runner.absolutePath, "/runner"))
        command.addAll(listOf("--ro-bind", preload.absolutePath, "/preload.js"))
        command.addAll(listOf("/runner", "/preload.js", childSessionMs.toString(), childCpuMs.toString()))
        process = ProcessBuilder(command).start()
        val child = process!!
        Thread({ drain(child.inputStream, lines, stdoutEof) }, "gate-stdout").apply { isDaemon = true; start() }
        Thread({
            val buf = ByteArray(256)
            try {
                child.errorStream.use { err ->
                    while (true) {
                        val n = err.read(buf)
                        if (n < 0) break
                        synchronized(stderr) {
                            if (stderr.length < 8_192) stderr.append(String(buf, 0, n, Charsets.UTF_8).take(8_192 - stderr.length))
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }, "gate-stderr").apply { isDaemon = true; start() }
        Thread({
            val deadline = System.nanoTime() + parentBudgetMs * 1_000_000L
            while (!closed && child.isAlive) {
                if (System.nanoTime() > deadline) {
                    killForBudget = true
                    child.destroyForcibly()
                    break
                }
                Thread.sleep(50)
            }
        }, "gate-watchdog").apply { isDaemon = true; start() }
        val isolation = readLine(3_000) ?: throw SandboxException("runner did not report isolation: ${stderrText()} exit=${exitCode()}")
        val msg = Json.parse(isolation).obj()
        if (msg["kind"] != "isolation" || msg["seccomp"] != true) throw SandboxException("isolation record missing")
        roundTrip(mapOf("op" to "preload"))
    }

    fun setup(script: ScriptInfo = info) {
        ledger.reset()
        roundTrip(linkedMapOf(
            "op" to "setup",
            "key" to script.key,
            "scriptId" to script.id,
            "name" to script.name,
            "description" to script.description,
            "version" to script.version,
            "author" to script.author,
            "homepage" to script.homepage,
            "rawScript" to script.rawScript,
        ))
        drainHttp()
    }

    fun eval(code: String): Map<String, Any?> {
        val result = roundTrip(mapOf("op" to "eval", "code" to code))
        drainHttp()
        return result
    }

    fun armSession(ms: Int) {
        roundTrip(mapOf("op" to "budget", "ms" to ms))
    }

    fun call(action: String, data: String?): Map<String, Any?> {
        val fields = linkedMapOf<String, Any?>("op" to "call", "action" to action)
        if (data != null) fields["data"] = data
        val result = roundTrip(fields)
        drainHttp()
        return result
    }

    /** Host-side delay used to prove the child stops waiting on a silent parent. */
    fun stallNextUtility(ms: Long) {
        stallMs = ms
    }

    @Volatile private var stallMs = 0L

    fun stderrText(): String = synchronized(stderr) { stderr.toString() }

    fun exitCode(): Int? = process?.let { if (it.isAlive) null else it.exitValue() }

    fun alive(): Boolean = process?.isAlive == true

    private fun roundTrip(fields: Map<String, Any?>): Map<String, Any?> {
        val id = nextCommand++
        val payload = LinkedHashMap<String, Any?>(fields)
        payload["id"] = id
        writeLine(Json.stringify(payload))
        val start = System.nanoTime()
        while (true) {
            if (System.nanoTime() - start > replyTimeoutMs * 1_000_000L) {
                process?.destroyForcibly()
                throw SandboxException("parent watchdog waiting for child")
            }
            val line = readLine(200) ?: if (process?.isAlive == false) {
                throw SandboxException("child exited ${process?.exitValue()} during command")
            } else continue
            val msg = Json.parse(line).obj()
            when (msg["op"].str()) {
                "rpc" -> onRpc(msg)
                "result" -> if (msg["id"].num() == id) return msg
                else -> throw SandboxException("unexpected runner message")
            }
        }
    }

    private fun onRpc(msg: Map<String, Any?>) {
        val id = msg["id"].num()
        val op = msg["fn"].str()
        val args = msg["args"].list()
        try {
            when (op) {
                "native" -> {
                    val action = args.getOrNull(1).str()
                    val dataText = args.getOrNull(2).str()
                    val data = Json.parse(dataText)
                    handleNative(action, data)
                    reply(id, true, null)
                }
                "timer" -> {
                    events.add(HostEvent("timer", linkedMapOf("id" to args[0].num(), "timeout" to args[1].num())))
                    reply(id, true, null)
                }
                "str2b64", "b642buf", "md5", "aes", "rsa" -> {
                    val stall = stallMs
                    if (stall > 0) {
                        stallMs = 0
                        Thread.sleep(stall)
                    }
                    val value = LegacyCrypto.invoke(op, args)
                    reply(id, true, value)
                }
                else -> reply(id, false, null)
            }
        } catch (closedChild: SandboxException) {
            throw closedChild
        } catch (_: InputQuotaError) {
            reply(id, false, null)
        } catch (_: Exception) {
            reply(id, false, null)
        }
    }

    private fun handleNative(action: String, data: Any?) {
        when (action) {
            "init", "showUpdateAlert" -> events.add(HostEvent(action, if (data is Map<*, *>) data.obj() else linkedMapOf("value" to data)))
            "request" -> queueHttp(data.obj())
            "cancelRequest" -> cancelHttp(data.str())
            "response" -> events.add(HostEvent("response", data.obj()))
            else -> events.add(HostEvent(action, linkedMapOf("value" to data)))
        }
    }

    private fun queueHttp(data: Map<String, Any?>) {
        val key = data["requestKey"].str()
        val url = data["url"].str()
        val options = data["options"]?.obj() ?: emptyMap()
        val timeout = when (val value = options["timeout"]) {
            is Number -> value.toLong()
            else -> 13_000L
        }
        val ticket = try {
            ledger.begin(key, clock(), timeout)
        } catch (e: IllegalStateException) {
            queued.add(PendingHttp(key, RequestLedger.Ticket(key, -1, 0), url, options, cancelled = true))
            return
        }
        queued.add(PendingHttp(key, ticket, url, options))
    }

    private fun cancelHttp(key: String) {
        val removed = ledger.cancel(key)
        queued.filter { it.key == key }.forEach { it.cancelled = true }
        events.add(HostEvent("cancelRequest", linkedMapOf("key" to key, "removed" to removed)))
    }

    private fun drainHttp() {
        var guard = 0
        while (queued.isNotEmpty()) {
            if (++guard > 16) throw SandboxException("http cascade")
            val item = queued.removeAt(0)
            if (item.key in delivered) continue
            if (item.cancelled || item.ticket.generation < 0) {
                deliver(item.key, error = if (item.ticket.generation < 0) "request quota" else "request canceled", response = null)
                continue
            }
            val prepared = LegacyRequest.prepare(item.options)
            if (prepared.rejected != null) {
                ledger.complete(item.ticket, clock())
                deliver(item.key, error = prepared.rejected, response = null)
                continue
            }
            val outcome = try {
                val admission = policy.admit(item.url, prepared.method, prepared.headers, prepared.body, prepared.timeoutMs, prepared.binary)
                val targets = policy.publicTargets(admission.host)
                httpAttempts.add(item.url + " " + targets.joinToString(","))
                targets to admission
            } catch (e: PolicyRejected) {
                ledger.complete(item.ticket, clock())
                deliver(item.key, error = e.message ?: "policy", response = null)
                continue
            }
            val finish = ledger.complete(item.ticket, clock())
            if (finish != Finish.OK) {
                val message = if (finish == Finish.LATE) "request timeout" else "request canceled"
                if (finish != Finish.UNKNOWN) deliver(item.key, error = message, response = null)
                continue
            }
            val exchange = try {
                transport(outcome.second)
            } catch (e: Exception) {
                deliver(item.key, error = "request failed", response = null)
                continue
            }
            deliver(item.key, error = null, response = scriptResponse(prepared, exchange))
        }
    }

    private fun scriptResponse(prepared: LegacyRequest.Prepared, exchange: Exchange): Map<String, Any?> {
        val body: Any? = if (prepared.binary) {
            linkedMapOf("__lx_body" to "base64", "data" to Binary.encode(exchange.body))
        } else {
            val text = exchange.body.toString(Charsets.UTF_8)
            try {
                Json.parse(text)
            } catch (_: Exception) {
                text
            }
        }
        return linkedMapOf(
            "statusCode" to exchange.statusCode,
            "statusMessage" to exchange.statusMessage,
            "headers" to exchange.headers,
            "body" to body,
        )
    }

    private fun deliver(key: String, error: String?, response: Map<String, Any?>?) {
        if (!delivered.add(key)) return
        val payload = linkedMapOf<String, Any?>("requestKey" to key, "error" to error, "response" to response)
        call("response", Json.stringify(payload))
    }

    private fun reply(id: Long, ok: Boolean, value: Any?) {
        writeLine(Json.stringify(linkedMapOf("id" to id, "ok" to ok, "value" to value)))
    }

    private fun writeLine(text: String) {
        val child = process ?: throw SandboxException("not started")
        val bytes = (text + "\n").toByteArray(Charsets.UTF_8)
        try {
            synchronized(child.outputStream) {
                child.outputStream.write(bytes)
                child.outputStream.flush()
            }
        } catch (e: Exception) {
            throw SandboxException("child exited ${exitCode()} during write")
        }
    }

    private fun readLine(waitMs: Long): String? {
        val deadline = System.nanoTime() + waitMs * 1_000_000L
        while (true) {
            val left = deadline - System.nanoTime()
            if (left <= 0) return null
            val line = lines.poll(left.coerceAtMost(200_000_000L), TimeUnit.NANOSECONDS)
            if (line != null) return line
            if (stdoutEof.get() && lines.isEmpty()) return null
        }
    }

    override fun close() {
        closed = true
        val child = process ?: return
        if (child.isAlive) {
            try {
                writeLine(Json.stringify(mapOf("op" to "close", "id" to nextCommand)))
            } catch (_: Exception) {
            }
            if (!child.waitFor(1, TimeUnit.SECONDS)) child.destroyForcibly()
        }
    }

    private fun drain(input: InputStream, queue: ArrayBlockingQueue<String>, eof: AtomicBoolean) {
        try {
            input.bufferedReader(Charsets.UTF_8).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.length > 8_388_608) break
                    queue.put(line)
                }
            }
        } catch (_: Exception) {
        } finally {
            eof.set(true)
        }
    }
}

/** Kills a non-responsive child. The separate process is not the sandbox; this deadline is. */
object ParentWatchdog {
    fun killIfSilent(command: List<String>, budgetMs: Long): Int {
        val process = ProcessBuilder(command).start()
        val started = System.nanoTime()
        Thread {
            while (process.isAlive) {
                if (System.nanoTime() - started > budgetMs * 1_000_000L) {
                    process.destroyForcibly()
                    break
                }
                Thread.sleep(20)
            }
        }.apply { isDaemon = true; start() }
        process.waitFor(budgetMs + 2_000, TimeUnit.MILLISECONDS)
        if (process.isAlive) {
            process.destroyForcibly()
            process.waitFor(2, TimeUnit.SECONDS)
        }
        return process.exitValue()
    }
}
