// SPDX-License-Identifier: Apache-2.0
package lx.compat

/**
 * Planned window classes. The BYD numbers are 1920x984 at density 1.5, split by the system's
 * single 30/70 pair. They are not measurements from a car.
 */
object WindowProfile {
    @JvmStatic
    fun classify(widthPx: Int, heightPx: Int, density: Float): String {
        if (density <= 0f || widthPx <= 0 || heightPx <= 0) return "unknown"
        val widthDp = widthPx / density
        val heightDp = heightPx / density
        // A 30% car pane is taller than it is wide, so aspect alone cannot separate it from a phone.
        // The planned car height band is the 984px / 1.5 density row. It is not a measured car value.
        if (heightDp in 600f..720f) {
            if (widthDp >= 1100f) return "car-full"
            if (widthDp >= 640f) return "car-70"
            return "car-30"
        }
        if (widthDp <= heightDp && widthDp < 600f) return "phone-portrait"
        return "phone-large"
    }
}
