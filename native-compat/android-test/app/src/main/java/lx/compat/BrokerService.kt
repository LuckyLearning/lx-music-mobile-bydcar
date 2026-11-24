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
    private val ready = CountDownLatch(1)
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

        override fun searchKg(query: String): String {
            if (query.length !in 1..80) return "{\"error\":\"query\",\"upstream\":false,\"status\":0}"
            val live = KgCatalog.live(policy, query)
            val tracks = live.page?.tracks?.take(15)?.map { track ->
                mapOf(
                    "name" to track.name.take(80),
                    "singer" to track.singer.take(80),
                    "songmid" to track.songmid.take(64),
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

        override fun resolveReviewed(requestJson: String): String {
            if (!ready.await(10, TimeUnit.SECONDS)) return "{\"error\":\"runtime-unavailable\"}"
            val current = runtime ?: return "{\"error\":\"runtime-unavailable\"}"
            current.setHost(object : IScriptHost.Stub() {
                override fun handle(messageJson: String): String = host.onRpc(messageJson)
            })
            val preload = assets.open("user-api-preload.js").bufferedReader().use { it.readText() }
            val script = assets.open("reviewed-playback.js").bufferedReader().use { it.readText() }
            val trace = host.runPreload({ current.command(it) }, preload, script, requestJson)
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
