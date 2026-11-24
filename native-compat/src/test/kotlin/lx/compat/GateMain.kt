// SPDX-License-Identifier: Apache-2.0
package lx.compat

import java.io.File
import java.net.InetAddress
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.GZIPOutputStream
import java.io.ByteArrayOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

private var failed = 0
private val log = StringBuilder()

private fun check(name: String, ok: Boolean, detail: String = "") {
    val line = if (ok) "PASS $name" else "FAIL $name ${detail.take(300)}"
    println(line)
    log.append(line).append('\n')
    if (!ok) failed++
}

private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
    return digest.joinToString("") { "%02x".format(it) }
}

private class FakeConn(override val peerIp: String, response: ByteArray, private val slice: Int) : ByteConn {
    val written = ArrayList<Byte>()
    private val payload = response
    private var pos = 0
    override fun writeFully(bytes: ByteArray) { for (b in bytes) written.add(b) }
    override fun read(buf: ByteArray): Int {
        if (pos >= payload.size) return -1
        val n = minOf(slice, buf.size, payload.size - pos)
        payload.copyInto(buf, 0, pos, pos + n)
        pos += n
        return n
    }
    override fun close() {}
    fun text(): String = ByteArray(written.size) { written[it] }.toString(Charsets.ISO_8859_1)
}

private class ScriptedWorld {
    val dns = HashMap<String, List<String>>()
    val pages = HashMap<String, ByteArray>()
    val opened = ArrayList<String>()
    val conns = ArrayList<FakeConn>()
    val connector = Connector { host, ip, _ ->
        check("connector refuses private pins", Addresses.isPublic(ip), ip)
        opened.add("$host@$ip")
        val page = pages[host] ?: error("no page for $host")
        FakeConn(ip, page, 3).also { conns.add(it) }
    }
    fun https(maxBody: Int = 1_048_576) = PinnedHttps(NetworkPolicy(resolve = { dns[it] ?: error("dns $it") }), connector, maxBodyBytes = maxBody)
}

private fun http(status: String, headers: String, body: String = ""): ByteArray {
    val head = if (headers.isEmpty()) "" else headers + "\r\n"
    return "HTTP/1.1 $status\r\n${head}\r\n$body".toByteArray(Charsets.ISO_8859_1)
}

private fun gzip(bytes: ByteArray): ByteArray {
    val out = ByteArrayOutputStream()
    GZIPOutputStream(out).use { it.write(bytes) }
    return out.toByteArray()
}

fun main(args: Array<String>) {
    if (args.size != 3) {
        println("usage: GateMain <repo-root> <runner> <preload>")
        kotlin.system.exitProcess(2)
    }
    val root = File(args[0])
    val runner = File(args[1])
    val preload = File(args[2])
    jsonTests()
    cryptoTests()
    ledgerTests()
    addressTests()
    requestShapeTests()
    transportTests()
    businessTests()
    sourceTests(root, preload)
    check("parent watchdog kills a silent child", watchdogKillsSleep(), "")
    if (!runner.isFile) {
        check("sandbox runner exists", false, runner.path)
    } else {
        sandboxTests(runner, preload)
    }
    liveExample()
    println("SUMMARY failed=$failed")
    log.append("SUMMARY failed=$failed\n")
    val reportDir = File("reports")
    reportDir.mkdirs()
    File(reportDir, "gate-results.txt").writeText(log.toString())
    kotlin.system.exitProcess(if (failed == 0) 0 else 1)
}

private fun jsonTests() {
    val value = Json.parse("""{"a":1,"b":"中🙂","c":[true,null]}""").obj()
    check("json keeps bmp and emoji", value["b"] == "中🙂" && value["a"] == 1.0)
    val again = Json.parse(Json.stringify(value)).obj()
    check("json stringify roundtrip", again["b"] == "中🙂" && again["c"].list() == listOf(true, null))
    check("integral doubles stringify as integers", Json.stringify(1.0) == "1")
    var dup = false
    try { Json.parse("""{"a":1,"a":2}""") } catch (_: Exception) { dup = true }
    check("duplicate JSON keys are rejected", dup)
    var deep = false
    try { Json.parse("[".repeat(60) + "]".repeat(60)) } catch (_: Exception) { deep = true }
    check("json depth is bounded", deep)
    var huge = false
    try { Json.parse("\"" + "a".repeat(Json.MAX_CHARS) + "\"") } catch (_: Exception) { huge = true }
    check("json size is bounded", huge)
    var junk = false
    try { Json.parse("true false") } catch (_: Exception) { junk = true }
    check("json trailing junk is rejected", junk)
}

private fun cryptoTests() {
    val raw = byteArrayOf(0, 0x7f, 0x80.toByte(), 0xff.toByte())
    val b64 = Binary.encode(raw)
    check("base64 matches jdk encoder", b64 == Base64.getEncoder().encodeToString(raw))
    check("base64 roundtrip", Binary.decode(b64).contentEquals(raw))
    check("signed byte json", LegacyCrypto.invoke("b642buf", listOf(b64)) == "[0,127,-128,-1]")
    check("str2b64 is utf-8", LegacyCrypto.invoke("str2b64", listOf("中🙂")) == Binary.encode("中🙂".toByteArray(Charsets.UTF_8)))
    check("md5 of a", LegacyCrypto.invoke("md5", listOf("a")) == "0cc175b9c0f1b6a831c399e269772661")
    val spaced = MessageDigest.getInstance("MD5").digest("a b".toByteArray()).joinToString("") { "%02x".format(it) }
    check("md5 url-decodes percent", LegacyCrypto.invoke("md5", listOf("a%20b")) == spaced)
    check("md5 url-decodes plus", LegacyCrypto.invoke("md5", listOf("a+b")) == spaced)
    check("md5 bad percent is empty", LegacyCrypto.invoke("md5", listOf("%")) == "")
    val key = ByteArray(16) { (it + 1).toByte() }
    val iv = ByteArray(16) { (it + 3).toByte() }
    val data = "hello".toByteArray()
    val cbc = Cipher.getInstance("AES/CBC/PKCS5Padding")
    cbc.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
    val expect = Binary.encode(cbc.doFinal(data))
    val got = LegacyCrypto.invoke("aes", listOf(Binary.encode(data), Binary.encode(key), Binary.encode(iv), "AES/CBC/PKCS7Padding"))
    check("aes-cbc pkcs7 matches jdk pkcs5", got == expect, "$got")
    val short = ByteArray(16)
    byteArrayOf(9, 8, 7, 6).copyInto(short)
    val cbcShort = Cipher.getInstance("AES/CBC/PKCS5Padding")
    cbcShort.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(short))
    val expectShort = Binary.encode(cbcShort.doFinal(data))
    val gotShort = LegacyCrypto.invoke("aes", listOf(Binary.encode(data), Binary.encode(key), Binary.encode(byteArrayOf(9, 8, 7, 6)), "AES/CBC/PKCS7Padding"))
    check("aes iv is zero-padded to 16", gotShort == expectShort)
    val ecb = Cipher.getInstance("AES/ECB/PKCS5Padding")
    ecb.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
    val expectEcb = Binary.encode(ecb.doFinal(data))
    check("aes mode AES with empty iv is ecb", LegacyCrypto.invoke("aes", listOf(Binary.encode(data), Binary.encode(key), "", "AES")) == expectEcb)
    check("aes ecb alias with an iv returns empty", LegacyCrypto.invoke("aes", listOf(Binary.encode(data), Binary.encode(key), Binary.encode(iv), "AES")) == "")
    check("aes bad key returns empty", LegacyCrypto.invoke("aes", listOf(Binary.encode(data), Binary.encode(ByteArray(15)), "", "AES")) == "")
    val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    val pub = Binary.encode(pair.public.encoded)
    val block = ByteArray(256)
    block[255] = 7
    val encrypted = LegacyCrypto.invoke("rsa", listOf(Binary.encode(block), pub, "RSA/ECB/NoPadding")) as String
    val back = Cipher.getInstance("RSA/ECB/NoPadding").run {
        init(Cipher.DECRYPT_MODE, pair.private)
        doFinal(Binary.decode(encrypted))
    }
    check("rsa nopadding matches jca", back.contentEquals(block))
    val oaepIn = "hi".toByteArray()
    val oaep = LegacyCrypto.invoke("rsa", listOf(Binary.encode(oaepIn), pub, "RSA/ECB/OAEPWithSHA1AndMGF1Padding")) as String
    val oaepOut = Cipher.getInstance("RSA/ECB/OAEPWithSHA1AndMGF1Padding").run {
        init(Cipher.DECRYPT_MODE, pair.private)
        doFinal(Binary.decode(oaep))
    }
    check("rsa oaep matches jca", oaepOut.contentEquals(oaepIn))
    check("rsa unknown padding is empty", LegacyCrypto.invoke("rsa", listOf(Binary.encode(block), pub, "RSA/ECB/PKCS1Padding")) == "")
    var quota = false
    try { LegacyCrypto.invoke("str2b64", listOf("x".repeat(1_048_577))) } catch (_: InputQuotaError) { quota = true }
    check("utility quota is not swallowed", quota)
}

private fun ledgerTests() {
    val ledger = RequestLedger(maxPending = 1, maxTotal = 2)
    val a = ledger.begin("a", 0, 1000)
    var blocked = false
    try { ledger.begin("b", 0, 1000) } catch (_: IllegalStateException) { blocked = true }
    check("pending cap does not consume the lifetime cap", blocked && ledger.total() == 1)
    check("cancel removes the ticket", ledger.cancel("a") && ledger.size() == 0)
    val b = ledger.begin("b", 0, 1000)
    check("lifetime cap remains after a rejected begin", ledger.total() == 2 && b.key == "b")
    val late = RequestLedger().begin("k", 0, 5)
    check("late completion removes the ticket", RequestLedger().let {
        val t = it.begin("k", 0, 5)
        it.complete(t, 5) == Finish.LATE && it.size() == 0 && it.begin("k", 10, 5).key == "k"
    })
    val generation = RequestLedger()
    val old = generation.begin("k", 0, 1000)
    generation.reset()
    check("reset makes an old ticket stale", generation.complete(old, 1) == Finish.UNKNOWN)
    check("unknown cancel is false", !ledger.cancel("missing"))
    check("playback https is admitted", PlaybackUrlPolicy.admit("https://example.com/a.mp3") == "https://example.com/a.mp3")
    var http = false
    try { PlaybackUrlPolicy.admit("http://example.com/a.mp3") } catch (_: PolicyRejected) { http = true }
    check("playback cleartext is rejected", http)
    check("late ticket key is retained until completion", late.key == "k")
}

private fun addressTests() {
    check("public ipv4", Addresses.isPublic("8.8.8.8"))
    check("loopback rejected", !Addresses.isPublic("127.0.0.1"))
    check("rfc1918 rejected", !Addresses.isPublic("10.1.2.3") && !Addresses.isPublic("192.168.0.1") && !Addresses.isPublic("172.16.0.1"))
    check("link local and metadata rejected", !Addresses.isPublic("169.254.169.254"))
    check("cgnat rejected", !Addresses.isPublic("100.64.0.1"))
    check("documentation ipv4 rejected", !Addresses.isPublic("192.0.2.1") && !Addresses.isPublic("198.51.100.1") && !Addresses.isPublic("203.0.113.1"))
    check("multicast rejected", !Addresses.isPublic("224.0.0.1"))
    check("ipv6 loopback rejected", !Addresses.isPublic("::1"))
    check("documentation ipv6 rejected", !Addresses.isPublic("2001:db8::1"))
    check("global ipv6 accepted", Addresses.isPublic("2606:4700:4700::1111"))
    check("mapped ipv4 text is not parsed", Addresses.parse("::ffff:8.8.8.8") == null)
    check("leading zero ipv4 is not parsed", Addresses.parse("08.8.8.8") == null)
    val policy = NetworkPolicy(resolve = { error("dns") })
    var local = false
    try { policy.admit("http://example.com/", "GET", emptyList(), null, 1000, false) } catch (_: PolicyRejected) { local = true }
    check("policy rejects cleartext", local)
    var loop = false
    try { policy.admit("https://127.0.0.1/", "GET", emptyList(), null, 1000, false) } catch (_: PolicyRejected) { loop = true }
    check("policy rejects loopback literal", loop)
    var injected = false
    try { policy.admit("https://example.com/", "GET", listOf("X-A" to "a\r\nHost: evil"), null, 1000, false) } catch (_: PolicyRejected) { injected = true }
    check("policy rejects header injection", injected)
}

private fun requestShapeTests() {
    val form = LegacyRequest.prepare(mapOf("method" to "POST", "form" to linkedMapOf("a b" to "c&d")))
    check("form body uses encodeURIComponent", form.body!!.toString(Charsets.UTF_8) == "a%20b=c%26d", form.body.toString(Charsets.UTF_8))
    check("form content type", form.headers.any { it.first.equals("Content-Type", true) && it.second == "application/x-www-form-urlencoded" })
    val json = LegacyRequest.prepare(mapOf("method" to "post", "body" to linkedMapOf("n" to 1.0)))
    check("json body is stringified", json.body!!.toString(Charsets.UTF_8) == """{"n":1}""")
    val typed = LegacyRequest.prepare(mapOf("method" to "Post", "headers" to linkedMapOf("content-type" to "text/plain"), "body" to linkedMapOf("a" to 1.0)))
    check("missing exact Content-Type still json-stringifies", typed.body!!.toString(Charsets.UTF_8) == """{"a":1}""")
    val rejected = LegacyRequest.prepare(mapOf("method" to "post", "formData" to linkedMapOf("a" to "b")))
    check("formData is rejected", rejected.rejected == "multipart formData is not supported")
    val getBody = LegacyRequest.prepare(mapOf("method" to "GET", "body" to "x", "headers" to linkedMapOf("Content-Type" to "text/plain")))
    check("GET body is rejected", getBody.rejected == "GET body")
}

private fun transportTests() {
    val world = ScriptedWorld()
    world.dns["example.com"] = listOf("1.1.1.1")
    world.dns["cdn.example"] = listOf("8.8.8.8")
    world.dns["lan.example"] = listOf("192.168.1.9")
    world.dns["flip.example"] = listOf("1.1.1.1", "127.0.0.1")
    world.pages["example.com"] = http("302 Found", "Location: https://cdn.example/a")
    world.pages["cdn.example"] = "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n5\r\nhello\r\n0\r\n\r\n".toByteArray(Charsets.ISO_8859_1)
    val client = world.https()
    val ok = client.execute(NetworkPolicy(resolve = { world.dns[it]!! }).admit("https://example.com/start", "GET", emptyList(), null, 1000, false))
    check("redirect is followed to the pinned second address", world.opened == listOf("example.com@1.1.1.1", "cdn.example@8.8.8.8"), world.opened.toString())
    check("chunked body is reassembled", ok.body.toString(Charsets.ISO_8859_1) == "hello")
    val blocked = ScriptedWorld()
    blocked.dns["example.com"] = listOf("1.1.1.1")
    blocked.dns["lan.example"] = listOf("192.168.1.9")
    blocked.pages["example.com"] = http("302 Found", "Location: https://lan.example/secret")
    var refused = false
    try {
        blocked.https().execute(NetworkPolicy(resolve = { blocked.dns[it]!! }).admit("https://example.com/", "GET", emptyList(), null, 1000, false))
    } catch (_: PolicyRejected) { refused = true }
    check("redirect to a private address is not opened", refused && blocked.opened == listOf("example.com@1.1.1.1"), blocked.opened.toString())
    val flip = ScriptedWorld()
    flip.dns["flip.example"] = listOf("1.1.1.1", "127.0.0.1")
    flip.pages["flip.example"] = http("200 OK", "Content-Length: 2", "ok")
    var mixed = false
    try {
        flip.https().execute(NetworkPolicy(resolve = { flip.dns[it]!! }).admit("https://flip.example/", "GET", emptyList(), null, 1000, false))
    } catch (_: PolicyRejected) { mixed = true }
    check("mixed public and loopback answers are refused", mixed && flip.opened.isEmpty())
    val gz = ScriptedWorld()
    gz.dns["gz.example"] = listOf("8.8.4.4")
    val plain = "lyric".repeat(20).toByteArray()
    val packed = gzip(plain)
    gz.pages["gz.example"] = ("HTTP/1.1 200 OK\r\nContent-Encoding: gzip\r\nContent-Length: ${packed.size}\r\n\r\n").toByteArray(Charsets.ISO_8859_1) + packed
    val inflated = gz.https().execute(NetworkPolicy(resolve = { gz.dns[it]!! }).admit("https://gz.example/", "GET", emptyList(), null, 1000, false))
    check("gzip body is inflated under the cap", inflated.body.contentEquals(plain) && inflated.headers["content-encoding"] == null)
    val bomb = ScriptedWorld()
    bomb.dns["bomb.example"] = listOf("8.8.4.4")
    val huge = gzip(ByteArray(200_000))
    bomb.pages["bomb.example"] = ("HTTP/1.1 200 OK\r\nContent-Encoding: gzip\r\nContent-Length: ${huge.size}\r\n\r\n").toByteArray(Charsets.ISO_8859_1) + huge
    var limited = false
    try { bomb.https(maxBody = 1024).execute(NetworkPolicy(resolve = { bomb.dns[it]!! }).admit("https://bomb.example/", "GET", emptyList(), null, 1000, false)) } catch (_: TransportException) { limited = true }
    check("gzip expansion stops at the body cap", limited)
}

private fun businessTests() {
    val gate = BusinessGate()
    gate.start("a", 0)
    check("business success before 20s", gate.succeed("a", 19_999, "url") == "ok:url")
    gate.start("b", 0)
    check("business timeout message", gate.advance(20_000) == listOf("request timeout") && gate.size() == 0)
    val again = BusinessGate()
    again.start("c", 0)
    check("result after deadline reports timeout once", again.succeed("c", 20_000, "late") == "request timeout" && again.size() == 0)
    val updated = BusinessGate()
    updated.start("d", 0)
    updated.start("e", 0)
    check("source update fails pending requests", updated.onSourceUpdated() == listOf("request failed", "request failed") && updated.size() == 0)
}

private fun sourceTests(root: File, preload: File) {
    val asset = File(root, "android/app/src/main/assets/script/user-api-preload.js")
    val sdk = File(root, "src/utils/musicSdk/index.js").readText()
    val bridge = File(root, "src/core/init/userApi/index.ts").readText()
    val request = File(root, "src/core/init/userApi/request.js").readText()
    val text = asset.readText()
    check("preload fixture matches the app asset", sha256(asset) == sha256(preload) && sha256(asset) == "e0e892ea9472e190aef6f16996ebcd6bec077f7d8659ceb87a30830d8a53fe41")
    check("user-api sources are the mobile set", text.contains("const allSources = ['kw', 'kg', 'tx', 'wy', 'mg', 'local']"))
    check("online script actions are musicUrl", text.contains("kw: ['musicUrl']") && text.contains("local: ['musicUrl', 'lyric', 'pic']"))
    check("lx identity stays mobile 2.0.0", text.contains("version: '2.0.0'") && text.contains("env: 'mobile'"))
    check("built-in sdk owns search", sdk.contains("export const searchMusic") && sdk.contains("id: 'kw'") && sdk.contains("import xm"))
    check("xm is not an active built-in source id", !sdk.contains("id: 'xm'"))
    check("outer bridge timeout is 20s and cancel hook is empty", bridge.contains("20_000") && bridge.contains("canceleFn()"))
    check("formData branch reads form", request.contains("options.formData") && request.contains("Object.entries(options.form)"))
    check("default http timeout is 13s", request.contains("timeout = 13_000"))
}

private fun watchdogKillsSleep(): Boolean {
    val started = System.nanoTime()
    val code = ParentWatchdog.killIfSilent(listOf("/bin/sleep", "30"), 400)
    val elapsed = (System.nanoTime() - started) / 1_000_000
    log.append("INFO watchdog exit=$code elapsedMs=$elapsed\n")
    println("INFO watchdog exit=$code elapsedMs=$elapsed")
    return code != 0 && elapsed < 3_000
}

private fun sandboxTests(runner: File, preload: File) {
    fun open(
        resolver: (String) -> List<String> = { listOf("1.1.1.1") },
        transport: (Admission) -> Exchange = { Exchange(200, "OK", linkedMapOf("content-type" to "application/json"), "{\"ok\":true}".toByteArray(), listOf("1.1.1.1")) },
        childSessionMs: Int = 8_000,
        childCpuMs: Int = 2_000,
        replyTimeoutMs: Long = 6_000,
        parentBudgetMs: Long = 12_000,
    ) = ScriptSandbox(runner, preload, transport, NetworkPolicy(resolve = resolver), childSessionMs = childSessionMs, childCpuMs = childCpuMs, replyTimeoutMs = replyTimeoutMs, parentBudgetMs = parentBudgetMs)

    open().use { box ->
        box.start()
        val ident = box.eval("lx.version + '/' + lx.env")
        check("runner stays up before setup", ident["ok"] == false)
        box.setup(ScriptInfo("gate-key", "sid", "Sample", "desc", "1.2.0", "author", "https://example.com", "RAW"))
        val who = box.eval("lx.version + '/' + lx.env + '/' + lx.currentScriptInfo.name + '/' + lx.currentScriptInfo.rawScript")
        check("lx version env and script info", who["value"] == "2.0.0/mobile/Sample/RAW", "${who["value"]}")
        val globals = box.eval(
            "var report=['require','process','Java','std','os','fetch','XMLHttpRequest'].map(function(n){return n+':'+typeof globalThis[n]}).join(',');" +
                "var gate='eval'; try { eval('1'); gate='eval-ran'; } catch (e) { gate='eval-throw'; } try { Function('return 1')(); gate += ':fn-ran'; } catch (e) { gate += ':fn-throw'; } report+'|'+gate"
        )
        check("hostile globals and dynamic code are absent", globals["value"] == "require:undefined,process:undefined,Java:undefined,std:undefined,os:undefined,fetch:undefined,XMLHttpRequest:undefined|eval-throw:fn-throw", "${globals["value"]}")
        box.eval("console.log('CANARY_SECRET_lx')")
        check("console output is not copied out", !box.stderrText().contains("CANARY_SECRET_lx"))
        val wrong = box.eval("__lx_native__('nope','request', null)")
        check("native entry rejects the wrong key", wrong["value"] == "Invalid key", "${wrong["value"]}")
        val thrown = box.eval("throw new Error('SECRET_ERR_lx')")
        check("script exception is rejected without the message", thrown["ok"] == false && !box.stderrText().contains("SECRET_ERR_lx"))
    }

    open().use { box ->
        box.start(); box.setup()
        box.eval(
            "lx.on('request', function(req){ if (this !== lx || this.env !== 'mobile') throw new Error('this'); if (req.action==='musicUrl') return Promise.resolve('https://cdn.example/a.mp3'); if (req.action==='lyric') return Promise.resolve({lyric:'ok', tlyric:'t'.repeat(5119), rlyric:'r'.repeat(5120), lxlyric:'L'.repeat(8192)}); return Promise.reject(new Error('no')); }).then(function(){ return lx.send('inited', {sources:{kw:{type:'music', actions:['musicUrl','lyric','search'], qualitys:['128k','320k','flac','hires']}, kg:{type:'music', actions:['musicUrl'], qualitys:['128k','320k','flac','flac24bit']}, local:{type:'music', actions:['musicUrl','lyric','pic','search'], qualitys:['128k']}, xm:{type:'music', actions:['musicUrl'], qualitys:['128k']}, tx:{type:'video', actions:['musicUrl'], qualitys:['128k']}, wy:{type:'music', actions:['musicUrl'], qualitys:[]}}}); })"
        )
        val init = box.events.last { it.action == "init" }.data
        check("init status", init["status"] == true)
        val sources = init["info"].obj()["sources"].obj()
        check("kw intersection drops lyric search and hires", sources["kw"].obj()["actions"].list() == listOf("musicUrl") && sources["kw"].obj()["qualitys"].list() == listOf("128k", "320k", "flac"), sources["kw"].toString())
        check("kg keeps the quality intersection", sources["kg"].obj()["qualitys"].list() == listOf("128k", "320k", "flac", "flac24bit"))
        check("local actions stay and online qualities do not apply", sources["local"].obj()["actions"].list() == listOf("musicUrl", "lyric", "pic") && sources["local"].obj()["qualitys"].list().isEmpty())
        check("xm and non-music tx are absent", !sources.containsKey("xm") && !sources.containsKey("tx") && sources["wy"].obj()["qualitys"].list().isEmpty())
        box.call("request", Json.stringify(mapOf("requestKey" to "u1", "data" to mapOf("source" to "kw", "action" to "musicUrl", "info" to mapOf("type" to "128k")))))
        val music = box.events.last { it.action == "response" }.data
        check("musicUrl https is accepted by preload", music["status"] == true && music["result"].obj()["data"].obj()["url"] == "https://cdn.example/a.mp3", music.toString())
    }

    open().use { box ->
        box.start(); box.setup()
        box.eval("lx.on('request', function(){ return Promise.resolve('http://cdn.example/a.mp3'); })")
        box.call("request", Json.stringify(mapOf("requestKey" to "h1", "data" to mapOf("source" to "kw", "action" to "musicUrl", "info" to mapOf("type" to "128k")))))
        val url = box.events.last { it.action == "response" }.data["result"].obj()["data"].obj()["url"].str()
        check("preload still accepts a cleartext musicUrl string", url == "http://cdn.example/a.mp3")
        var play = false
        try { PlaybackUrlPolicy.admit(url) } catch (_: PolicyRejected) { play = true }
        check("playback policy still rejects that cleartext string", play)
    }

    open().use { box ->
        box.start(); box.setup()
        box.eval("lx.on('request', function(){ return Promise.resolve({lyric:'ok', tlyric:'t'.repeat(5119), rlyric:'r'.repeat(5120), lxlyric:'L'.repeat(8192)}); })")
        box.call("request", Json.stringify(mapOf("requestKey" to "l1", "data" to mapOf("source" to "local", "action" to "lyric", "info" to mapOf("type" to "local")))))
        val data = box.events.last { it.action == "response" }.data["result"].obj()["data"].obj()
        check("lyric boundaries match preload", data["lyric"] == "ok" && (data["tlyric"] as String).length == 5119 && data["rlyric"] == null && data["lxlyric"] == null, data.toString())
    }

    open().use { box ->
        box.start(); box.setup()
        box.call("request", Json.stringify(mapOf("requestKey" to "early", "data" to mapOf("source" to "kw", "action" to "musicUrl", "info" to mapOf("type" to "128k")))))
        val early = box.events.last { it.action == "response" }.data
        check("request before on() fails closed", early["status"] == false && early["errorMessage"] == "Request event is not defined", early.toString())
    }

    open().use { box ->
        box.start(); box.setup()
        box.eval(
            "var trace=[]; lx.on('request', function(){ trace.push('sync'); return Promise.resolve().then(function(){ trace.push('micro'); return 'https://cdn.example/ok.mp3'; }); }).then(function(){ trace.push('on'); return lx.send('inited', {sources:{kw:{type:'music', actions:['musicUrl'], qualitys:['128k']}}}); }).then(function(){ trace.push('inited'); });"
        )
        val traced = box.eval("trace.join(',')")
        check("init runs after on() microtasks", traced["value"] == "on,inited" && box.events.any { it.action == "init" }, "${traced["value"]}")
        box.call("request", Json.stringify(mapOf("requestKey" to "ord", "data" to mapOf("source" to "kw", "action" to "musicUrl", "info" to mapOf("type" to "128k")))))
        val after = box.eval("trace.join(',')")
        check("request handler microtask is ordered before the response is observed", after["value"] == "on,inited,sync,micro", "${after["value"]}")
        val responseIndex = box.events.indexOfLast { it.action == "response" }
        check("response event exists", responseIndex >= 0)
    }

    open().use { box ->
        box.start(); box.setup()
        box.eval("var ran=0; var id=setTimeout(function(){ ran=1; }, 0); clearTimeout(id); var id2=setTimeout(function(){ ran=2; }, 0);")
        check("both timers were posted", box.events.count { it.action == "timer" } == 2)
        box.call("__set_timeout__", "0")
        check("cleared timer does not run when the native timer still fires", box.eval("String(ran)")["value"] == "0")
        box.call("__set_timeout__", "1")
        check("remaining timer runs", box.eval("String(ran)")["value"] == "2")
    }

    open().use { box ->
        box.start(); box.setup()
        box.eval("var cancel=lx.request('https://example.com/a',{timeout:1000}, function(err){ globalThis.__err=err?String(err.message):'ok'; }); cancel(); var second='no'; try { cancel(); } catch (e) { second='threw'; } globalThis.__second=second;")
        val state = box.eval("globalThis.__second+':'+globalThis.__err")
        check("second cancel throws and the host reports one cancellation", state["value"] == "threw:request canceled" && box.httpAttempts.isEmpty(), "${state["value"]} attempts=${box.httpAttempts}")
    }

    open().use { box ->
        box.start(); box.setup()
        box.eval("lx.request('https://example.com/a',{method:'post', formData:{a:'b'}}, function(err){ globalThis.__form=err?String(err.message):'ok'; });")
        check("formData never reaches the network", box.httpAttempts.isEmpty() && box.eval("globalThis.__form")["value"] == "multipart formData is not supported")
    }

    var sawBody = ""
    open(transport = { admission ->
        sawBody = admission.body?.toString(Charsets.UTF_8) ?: ""
        Exchange(200, "OK", linkedMapOf("content-type" to "application/json"), "{\"ok\":true}".toByteArray(), listOf("1.1.1.1"))
    }).use { box ->
        box.start(); box.setup()
        box.eval("lx.request('https://example.com/song',{method:'post', timeout:1000, body:{q:'a b'}}, function(err, resp){ globalThis.__body = err ? String(err.message) : (resp.body && resp.body.ok === true ? 'json' : 'other'); });")
        check("script http json is parsed and the host saw the body", box.eval("globalThis.__body")["value"] == "json" && sawBody == """{"q":"a b"}""" && box.httpAttempts.single().startsWith("https://example.com/song 1.1.1.1"), "body=$sawBody attempts=${box.httpAttempts} value=${box.eval("globalThis.__body")["value"]}")
    }

    open(resolver = { if (it == "rebind.test") listOf("10.0.0.8") else listOf("1.1.1.1") }).use { box ->
        box.start(); box.setup()
        box.eval("lx.request('https://rebind.test/x',{}, function(err){ globalThis.__net=err?String(err.message):'ok'; });")
        check("private resolution does not call the transport", box.httpAttempts.isEmpty() && box.eval("globalThis.__net")["value"] == "Non-public address", "${box.eval("globalThis.__net")["value"]}")
    }

    open().use { box ->
        box.start(); box.setup()
        val bytes = box.eval("var u=lx.utils.buffer.from([0xF0,0x9F,0x99,0x82],'not'); String(lx.utils.buffer.bufToString([0xF0,0x9F,0x99,0x82],'utf8'));")
        val points = (bytes["value"] as? String)?.map { it.code.toString(16) }?.joinToString(",") ?: "missing"
        check("preload utf-8 quirk keeps the 3-byte decoder", points == "7d9,2000", points)
        val bmp = box.eval("lx.utils.buffer.bufToString([0xE4,0xB8,0xAD],'utf8')")
        check("preload utf-8 still decodes BMP", bmp["value"] == "中")
        val md5 = box.eval("lx.utils.crypto.md5('a b')")
        val expect = MessageDigest.getInstance("MD5").digest("a b".toByteArray()).joinToString("") { "%02x".format(it) }
        check("preload md5 encodes then the host decodes", md5["value"] == expect, "${md5["value"]}")
    }

    open().use { box ->
        box.start(); box.setup()
        box.eval("var first='p', second='p'; lx.send('updateAlert',{log:'hello', updateUrl:'bad'}).then(function(){ first='ok'; }, function(e){ first=String(e.message); }); lx.send('updateAlert',{log:'again'}).then(function(){ second='ok'; }, function(e){ second=String(e.message); });")
        val state = box.eval("first+'>'+second")
        val alert = box.events.last { it.action == "showUpdateAlert" }.data
        check("update alert is once and a short invalid url is kept", state["value"] == "ok>The update alert can only be called once." && alert["updateUrl"] == "bad" && box.events.count { it.action == "showUpdateAlert" } == 1, "${state["value"]} $alert")
    }

    val rawBin = byteArrayOf(0, 0x80.toByte(), 0xff.toByte())
    open(transport = {
        Exchange(200, "OK", linkedMapOf("content-type" to "application/octet-stream"), rawBin, listOf("1.1.1.1"))
    }).use { box ->
        box.start(); box.setup()
        box.eval("lx.request('https://example.com/b',{binary:true}, function(err, resp){ globalThis.__bin = err ? String(err.message) : JSON.stringify(resp.body); });")
        val expect = """{"__lx_body":"base64","data":"${Binary.encode(rawBin)}"}"""
        check("binary response reaches the script as base64, not a Buffer", box.eval("globalThis.__bin")["value"] == expect, "${box.eval("globalThis.__bin")["value"]}")
    }

    open(childCpuMs = 1500, replyTimeoutMs = 6_000, parentBudgetMs = 10_000).use { box ->
        box.start()
        var rejected = false
        try { box.eval("while(true){}") } catch (e: SandboxException) { rejected = e.message?.contains("70") == true }
        check("infinite loop exits 70", rejected && box.exitCode() == 70, "exit=${box.exitCode()} stderr=${box.stderrText()}")
    }

    open(childCpuMs = 2000, replyTimeoutMs = 6_000, parentBudgetMs = 10_000).use { box ->
        box.start()
        var rejected = false
        try { box.eval("var kept=[]; while(true) kept.push(new Uint8Array(1024*1024));") } catch (e: SandboxException) { rejected = true }
        check("memory limit exits 70", rejected && box.exitCode() == 70, "exit=${box.exitCode()}")
    }

    open(childSessionMs = 8_000, replyTimeoutMs = 8_000, parentBudgetMs = 12_000).use { box ->
        box.start(); box.setup()
        box.armSession(700)
        box.stallNextUtility(4_000)
        var stopped = false
        try { box.eval("lx.utils.crypto.md5('abc')") } catch (e: SandboxException) { stopped = true }
        check("child watchdog exits 72 while the parent is silent", stopped && box.exitCode() == 72, "exit=${box.exitCode()} stderr=${box.stderrText()}")
    }
}

private fun liveExample() {
    try {
        val policy = NetworkPolicy(resolve = { host -> InetAddress.getAllByName(host).map { it.hostAddress } })
        val client = PinnedHttps(policy, TlsConnector)
        val exchange = client.execute(policy.admit("https://example.com/", "GET", emptyList(), null, 8_000, false))
        val text = exchange.body.toString(Charsets.UTF_8)
        val public = exchange.peerIps.all { Addresses.isPublic(it) }
        check("live https://example.com returns a public address and the example page", exchange.statusCode == 200 && text.contains("Example Domain") && public, "status=${exchange.statusCode} ips=${exchange.peerIps}")
    } catch (e: Exception) {
        check("live https://example.com returns a public address and the example page", false, e.javaClass.simpleName + " " + (e.message ?: ""))
    }
}
