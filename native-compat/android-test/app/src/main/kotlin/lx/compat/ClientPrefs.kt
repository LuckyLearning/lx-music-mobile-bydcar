// SPDX-License-Identifier: Apache-2.0
package lx.compat

import android.content.Context

/** New-app preferences. This file is never the official React Native app's storage. */
object ClientPrefs {
    const val FILE = "lx_native_client"
    const val BOOT = "boot_autostart"
    const val AUTOPLAY = "launch_autoplay"
    const val CAR = "car_mode"
    const val FONT = "lrc_font_pt"
    const val QUEUE = "queue"
    const val FAVORITES = "favorites"
    const val PLAYLISTS = "playlists"

    fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
