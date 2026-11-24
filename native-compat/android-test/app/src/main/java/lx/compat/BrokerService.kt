package lx.compat

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** App-process broker. The isolated runtime calls back into this process for network and crypto. */
class BrokerService : Service() {
    private var ready = CountDownLatch(1)
    private var runtime: ISourceRuntime? = null
    private var resolveDns: (String) -> List<String> = { name ->
        InetAddress.getAllByName(name).mapNotNull { it.hostAddress }
    }
    private val policy = NetworkPolicy(resolve = { name -> resolveDns(name) })
    private val host = AndroidHost(policy)
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            runtime = ISourceRuntime.Stub.asInterface(service)
            ready.countDown()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            runtime = null
        }
    }
    private val stub = object : IBroker.Stub() {
        override fun runScript(script: String, requestJson: String?): String {
            if (!ready.await(10, TimeUnit.SECONDS)) return "runtime-unavailable"
            val current = runtime ?: return "runtime-unavailable"
            current.setHost(object : IScriptHost.Stub() {
                override fun handle(messageJson: String): String = host.onRpc(messageJson)
            })
            val preload = assets.open("user-api-preload.js").bufferedReader().use { it.readText() }
            val request = requestJson?.takeIf { it.isNotEmpty() }
            return host.runPreload({ current.command(it) }, preload, script, request)
        }

        override fun search(source: String, query: String): String {
            if (query.length !in 1..80) return "{\"error\":\"query\",\"upstream\":false,\"status\":0,\"source\":null,\"tracks\":[]}"
            val live = when (source) {
                "kg" -> KgCatalog.live(policy, query)
                "tx" -> TxCatalog.live(policy, query)
                "mg" -> MgCatalog.live(policy, query)
                "wy" -> WyCatalog.live(query)
                "kw" -> KwSearch.live(query)
                else -> return "{\"error\":\"source\",\"upstream\":false,\"status\":0,\"source\":null,\"tracks\":[]}"
            }
            return searchJson(source, live)
        }

        override fun playlists(source: String): String {
            if (source != "mg") return "{\"error\":\"source\",\"cards\":[]}"
            val raw = capture(policy, MgPlaylist.recommendUrl(1), MgPlaylist.headers)
            if (raw.clientError != null) return Json.stringify(mapOf("error" to raw.clientError, "cards" to emptyList<Any>()))
            val cards = runCatching { MgPlaylist.parseRecommend(Json.parse(raw.body)) }.getOrNull()
            return Json.stringify(mapOf(
                "status" to raw.status,
                "error" to if (cards == null) "parse" else null,
                "cards" to (cards?.take(20)?.map { mapOf("id" to it.id, "name" to it.name.take(80), "source" to it.source) } ?: emptyList()),
            ))
        }

        override fun playlistSongs(source: String, id: String): String {
            if (source != "mg" || id.length !in 1..24 || id.any { !it.isDigit() }) return "{\"error\":\"id\",\"tracks\":[]}"
            val raw = capture(policy, MgPlaylist.songsUrl(id, 1), MgPlaylist.headers)
            if (raw.clientError != null) return Json.stringify(mapOf("error" to raw.clientError, "tracks" to emptyList<Any>()))
            val page = runCatching { MgPlaylist.parseSongs(Json.parse(raw.body)) }.getOrNull()
            return searchJson("mg", LiveSearch(raw.status, page, page == null, null))
        }

        override fun installUserScript(script: String): String {
            if (script.length !in 1..262_144 || script.contains("child_process") || script.contains("shutdown")) {
                return "{\"present\":false,\"error\":\"script-rejected\"}"
            }
            scriptFile().writeText(script)
            return scriptInfo()
        }

        override fun scriptInfo(): String {
            val file = scriptFile()
            if (!file.isFile) return "{\"present\":false}"
            val bytes = file.readBytes()
            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
            return Json.stringify(mapOf(
                "present" to true,
                "bytes" to bytes.size,
                "sha256" to digest.joinToString("") { "%02x".format(it.toInt() and 255) },
            ))
        }

        override fun resolveStored(requestJson: String): String {
            val file = scriptFile()
            if (!file.isFile) return "{\"error\":\"missing\",\"admitted\":\"missing\"}"
            return runLoaded(file.readText(), requestJson)
        }

        override fun searchKg(query: String): String {
            if (query.length !in 1..80) return "{\"error\":\"query\",\"upstream\":false,\"status\":0}"
            val live = KgCatalog.live(policy, query)
            val tracks = live.page?.tracks?.take(15)?.map { track ->
                mapOf(
                    "name" to track.name.take(80),
                    "singer" to track.singer.take(80),
                    "songmid" to track.songmid.take(64),
                    "albumName" to track.albumName.take(80),
                    "interval" to track.interval,
                    "qualities" to track.qualities,
                    "hashes" to track.hashes,
                )
            } ?: emptyList()
            return Json.stringify(mapOf(
                "status" to live.status,
                "upstream" to live.upstream,
                "error" to live.clientError,
                "tracks" to tracks,
            ))
        }

        override fun resolveScript(script: String, requestJson: String): String {
            if (script.length !in 1..262_144) return "{\"error\":\"script\",\"admitted\":\"rejected\"}"
            if (script.contains("child_process") || script.contains("shutdown")) {
                return "{\"error\":\"script-rejected\",\"admitted\":\"rejected\"}"
            }
            return runLoaded(script, requestJson)
        }

        override fun resolveReviewed(requestJson: String): String {
            val script = assets.open("reviewed-playback.js").bufferedReader().use { it.readText() }
            return runLoaded(script, requestJson)
        }

        private fun runLoaded(script: String, requestJson: String): String {
            if (!ready.await(10, TimeUnit.SECONDS)) return "{\"error\":\"runtime-unavailable\"}"
            val current = runtime ?: return "{\"error\":\"runtime-unavailable\"}"
            current.setHost(object : IScriptHost.Stub() {
                override fun handle(messageJson: String): String = host.onRpc(messageJson)
            })
            val preload = assets.open("user-api-preload.js").bufferedReader().use { it.readText() }
            var trace = host.runPreload({ current.command(it) }, preload, script, requestJson)
            if (trace.contains("command-rejected op=preload")) {
                restartRuntime()
                if (!ready.await(10, TimeUnit.SECONDS)) return "{\"error\":\"runtime-unavailable\"}"
                val again = runtime ?: return "{\"error\":\"runtime-unavailable\"}"
                again.setHost(object : IScriptHost.Stub() {
                    override fun handle(messageJson: String): String = host.onRpc(messageJson)
                })
                trace = host.runPreload({ again.command(it) }, preload, script, requestJson)
            }
            val url = host.playbackUrl
            val admitted = if (url == null) "missing" else try {
                PlayerTransportPolicy().decide(url).name
            } catch (_: PolicyRejected) {
                "rejected"
            }
            return Json.stringify(mapOf("url" to url, "admitted" to admitted, "trace" to trace))
        }

        override fun playKg(hash: String, quality: String): String {
            if (!hash.matches(Regex("[A-Fa-f0-9]{32}"))) return "{\"error\":\"hash\",\"upstream\":false,\"status\":0,\"https\":false}"
            val live = KgPlay.live(policy, hash, quality)
            val playable = live.playable
            return Json.stringify(mapOf(
                "status" to live.status,
                "upstream" to live.upstream,
                "error" to live.clientError,
                "https" to (playable != null),
                "host" to playable?.host,
                "bitrate" to (playable?.bitrate ?: 0),
                "quality" to quality,
                "url" to playable?.url,
            ))
        }

        override fun installFixtureResolver(mode: String) {
            resolveDns = if (mode == "mixed") {
                { host ->
                    if (host == "rebind.test") listOf("1.1.1.1", "127.0.0.1") else error("fixture host")
                }
            } else {
                { name -> InetAddress.getAllByName(name).mapNotNull { it.hostAddress } }
            }
        }
    }

    private fun scriptFile() = java.io.File(filesDir, "user-script.js")

    private fun searchJson(source: String, live: LiveSearch): String {
        val tracks = live.page?.tracks?.take(15)?.map { track ->
            mapOf(
                "source" to source,
                "name" to track.name.take(80),
                "singer" to track.singer.take(80),
                "songmid" to track.songmid.take(64),
                "albumName" to track.albumName.take(80),
                "interval" to track.interval,
                "qualities" to track.qualities,
                "hashes" to track.hashes,
                "extra" to track.extra,
            )
        } ?: emptyList()
        return Json.stringify(mapOf(
            "source" to source,
            "status" to live.status,
            "upstream" to live.upstream,
            "error" to live.clientError,
            "tracks" to tracks,
        ))
    }

    private fun restartRuntime() {
        try {
            unbindService(connection)
        } catch (_: Exception) {
        }
        stopService(Intent(this, SourceRuntimeService::class.java))
        runtime = null
        ready = CountDownLatch(1)
        bindService(Intent(this, SourceRuntimeService::class.java), connection, BIND_AUTO_CREATE)
    }

    override fun onCreate() {
        super.onCreate()
        bindService(Intent(this, SourceRuntimeService::class.java), connection, BIND_AUTO_CREATE)
    }

    override fun onBind(intent: Intent): IBinder = stub

    override fun onDestroy() {
        unbindService(connection)
        super.onDestroy()
    }
}
