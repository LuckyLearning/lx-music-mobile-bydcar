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
    private val host = AndroidHost(NetworkPolicy(resolve = { name ->
        InetAddress.getAllByName(name).mapNotNull { it.hostAddress }
    }))
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
