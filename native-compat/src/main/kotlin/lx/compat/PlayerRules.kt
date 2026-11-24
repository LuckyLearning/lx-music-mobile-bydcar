// SPDX-License-Identifier: Apache-2.0
package lx.compat

/**
 * Playback decisions that must not depend on a screen existing.
 * Numbers match the current app: lyric minimum 1 with no upper cap, 5s car idle, 56dp dock reserve.
 */
object PlayerRules {
    const val MIN_LRC_FONT_PT = 1
    const val DEFAULT_VERTICAL_LRC_FONT_PT = 21
    const val DEFAULT_HORIZONTAL_LRC_FONT_PT = 22
    const val CAR_DOCK_RESERVE_DP = 56
    const val CAR_CONTROLS_IDLE_MS = 5_000L

    fun normalizeLrcFontPt(value: Double?, fallback: Int = MIN_LRC_FONT_PT): Int {
        if (value == null || !value.isFinite()) return fallback
        return maxOf(MIN_LRC_FONT_PT, kotlin.math.round(value).toInt())
    }

    fun stepLrcFontPt(value: Double?, delta: Int, fallback: Int = MIN_LRC_FONT_PT): Int {
        return normalizeLrcFontPt((normalizeLrcFontPt(value, fallback) + delta).toDouble(), fallback)
    }

    /**
     * Car layout when that mode is on and either the window is wider than tall, or its dp size
     * matches the planned BYD height band (including the tall 30% pane). A phone-sized window
     * without that density stays vertical.
     */
    fun selectPlayerLayout(width: Int, height: Int, carLayoutEnabled: Boolean, density: Float = 0f): String {
        if (width <= 0 || height <= 0) return "vertical"
        if (carLayoutEnabled && density > 0f && WindowProfile.classify(width, height, density).startsWith("car-")) return "car"
        if (carLayoutEnabled && width > height) return "car"
        if (width.toDouble() / height > 1.2) return "horizontal"
        return "vertical"
    }

    /**
     * Bottom padding in px. A reported system inset is used as-is.
     * The 56dp reserve is added only when car mode is on and the inset is still zero, so a real inset is not counted twice.
     */
    fun bottomPaddingPx(carMode: Boolean, systemBottomInsetPx: Int, density: Float): Int {
        if (systemBottomInsetPx > 0) return systemBottomInsetPx
        if (!carMode || density <= 0f) return 0
        return (CAR_DOCK_RESERVE_DP * density).toInt()
    }

    /**
     * Pane inside car layout. The planned BYD height band wins when it matches.
     * Other wide windows use the same width cuts, which are still not a measurement from a car.
     */
    fun carPane(widthPx: Int, heightPx: Int, density: Float): String {
        val planned = WindowProfile.classify(widthPx, heightPx, density)
        if (planned.startsWith("car-")) return planned
        if (density <= 0f || widthPx <= 0) return "car-30"
        val widthDp = widthPx / density
        return when {
            widthDp >= 1100f -> "car-full"
            widthDp >= 640f -> "car-70"
            else -> "car-30"
        }
    }

    /** Boot never starts audio unless autostart, launch-autoplay, and a saved queue are all true. */
    fun bootShouldStartPlayback(bootAutostart: Boolean, launchAutoplay: Boolean, hasQueue: Boolean): Boolean {
        return bootAutostart && launchAutoplay && hasQueue
    }
}

/** Car chrome visibility. Opening settings freezes the idle timer. */
class CarChrome(private val idleMs: Long = PlayerRules.CAR_CONTROLS_IDLE_MS) {
    var controlsVisible: Boolean = true
        private set
    var settingsOpen: Boolean = false
        private set
    private var hideAt: Long = idleMs

    fun tap(now: Long) {
        controlsVisible = true
        hideAt = now + idleMs
    }

    fun setSettingsOpen(open: Boolean, now: Long) {
        settingsOpen = open
        if (open) {
            controlsVisible = true
        } else {
            hideAt = now + idleMs
        }
    }

    fun tick(now: Long) {
        if (settingsOpen) return
        if (controlsVisible && now >= hideAt) controlsVisible = false
    }
}
