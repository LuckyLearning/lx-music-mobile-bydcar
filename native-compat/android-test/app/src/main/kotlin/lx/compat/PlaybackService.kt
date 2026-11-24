// SPDX-License-Identifier: Apache-2.0
package lx.compat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * The only player. Activities attach with a controller and must not construct another ExoPlayer.
 * Queue JSON is stored in this test app's own preferences.
 */
class PlaybackService : MediaSessionService() {
    private var player: ExoPlayer? = null
    private var session: MediaSession? = null
    private var coldStart = true
    private var restoring = false

    override fun onCreate() {
        super.onCreate()
        val audio = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        val created = ExoPlayer.Builder(this)
            .setAudioAttributes(audio, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        created.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (restoring) return
                if (events.containsAny(
                        Player.EVENT_TIMELINE_CHANGED,
                        Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Player.EVENT_POSITION_DISCONTINUITY,
                        Player.EVENT_PLAYBACK_STATE_CHANGED,
                    )
                ) {
                    persist(player)
                }
            }
        })
        player = created
        restore(created)
        session = MediaSession.Builder(this, created)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                ): MediaSession.ConnectionResult {
                    val accepted = super.onConnect(session, controller)
                    val commands = accepted.availableSessionCommands
                        .buildUpon()
                        .add(SessionCommand(ACTION_LAUNCH, Bundle.EMPTY))
                        .build()
                    return MediaSession.ConnectionResult.accept(commands, accepted.availablePlayerCommands)
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle,
                ): ListenableFuture<SessionResult> {
                    if (customCommand.customAction == ACTION_LAUNCH) {
                        consumeLaunchAutoplay()
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
                }
            })
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra(EXTRA_FROM_BOOT, false) == true) {
            enterBootForeground()
            coldStart = false
            val current = player
            val play = PlayerRules.bootShouldStartPlayback(
                ClientPrefs.prefs(this).getBoolean(ClientPrefs.BOOT, false),
                ClientPrefs.prefs(this).getBoolean(ClientPrefs.AUTOPLAY, false),
                (current?.mediaItemCount ?: 0) > 0,
            )
            if (play && current != null) {
                current.play()
            } else {
                leaveForeground()
                stopSelf()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val current = player
        if (current == null || !current.playWhenReady || current.mediaItemCount == 0) {
            leaveForeground()
            stopSelf()
        }
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        player = null
        super.onDestroy()
    }

    private fun consumeLaunchAutoplay() {
        if (!coldStart) return
        coldStart = false
        val current = player ?: return
        val enabled = ClientPrefs.prefs(this).getBoolean(ClientPrefs.AUTOPLAY, false)
        if (enabled && current.mediaItemCount > 0 && !current.isPlaying) current.play()
    }

    private fun restore(target: ExoPlayer) {
        val raw = ClientPrefs.prefs(this).getString(ClientPrefs.QUEUE, null) ?: return
        val parsed = try {
            Json.parse(raw).obj()
        } catch (_: RuntimeException) {
            return
        }
        val items = parsed["items"].list().mapNotNull { row ->
            val item = row.obj()
            val uri = item["uri"].strOrNull() ?: return@mapNotNull null
            if (uri.isEmpty()) return@mapNotNull null
            val title = item["title"].strOrNull() ?: ""
            val id = item["id"].strOrNull() ?: uri
            MediaItem.Builder()
                .setMediaId(id)
                .setUri(Uri.parse(uri))
                .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
                .build()
        }
        if (items.isEmpty()) return
        val index = (parsed["index"] as? Number)?.toInt()?.coerceIn(0, items.lastIndex) ?: 0
        val position = (parsed["position"] as? Number)?.toLong()?.coerceAtLeast(0L) ?: 0L
        restoring = true
        try {
            target.setMediaItems(items, index, position)
            target.prepare()
            target.playWhenReady = false
        } finally {
            restoring = false
        }
    }

    private fun persist(target: Player) {
        val items = (0 until target.mediaItemCount).map { index ->
            val item = target.getMediaItemAt(index)
            mapOf(
                "id" to item.mediaId,
                "title" to (item.mediaMetadata.title?.toString() ?: ""),
                "uri" to (item.localConfiguration?.uri?.toString() ?: ""),
            )
        }
        val payload = mapOf(
            "index" to target.currentMediaItemIndex.coerceAtLeast(0),
            "position" to target.currentPosition.coerceAtLeast(0L),
            "items" to items,
        )
        ClientPrefs.prefs(this).edit().putString(ClientPrefs.QUEUE, Json.stringify(payload)).commit()
    }

    private fun enterBootForeground() {
        if (Build.VERSION.SDK_INT < 26) return
        val channelId = "lx_playback"
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(channelId) == null) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "播放", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val notification = Notification.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("LX 原生测试")
            .setContentText("开机检查")
            .build()
        startForeground(7, notification)
    }

    private fun leaveForeground() {
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else @Suppress("DEPRECATION") stopForeground(true)
    }

    companion object {
        const val EXTRA_FROM_BOOT = "lx.fromBoot"
        const val ACTION_LAUNCH = "lx.launch"
    }
}
