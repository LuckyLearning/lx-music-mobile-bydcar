// SPDX-License-Identifier: Apache-2.0
package lx.compat

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/** Starts the playback service after boot. The service decides whether anything is allowed to play. */
class BootStartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!ClientPrefs.prefs(context).getBoolean(ClientPrefs.BOOT, false)) return
        val start = Intent(context, PlaybackService::class.java).putExtra(PlaybackService.EXTRA_FROM_BOOT, true)
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(start) else context.startService(start)
    }
}
