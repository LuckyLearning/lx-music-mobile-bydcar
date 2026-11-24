// SPDX-License-Identifier: Apache-2.0
package lx.compat

/** Timed lyric lines. A file without timestamps is left to the caller. */
object LyricCue {
    data class Line(val timeMs: Long, val text: String)

    fun parse(text: String): List<Line> {
        val lines = ArrayList<Line>()
        for (raw in text.lineSequence()) {
            val match = Regex("^\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?](.*)$").find(raw.trim()) ?: continue
            val minutes = match.groupValues[1].toLong()
            val seconds = match.groupValues[2].toLong()
            val fraction = match.groupValues[3]
            val millis = when (fraction.length) {
                0 -> 0L
                1 -> fraction.toLong() * 100
                2 -> fraction.toLong() * 10
                else -> fraction.take(3).toLong()
            }
            lines.add(Line(minutes * 60_000L + seconds * 1_000L + millis, match.groupValues[4].trim()))
        }
        return lines.sortedBy { it.timeMs }
    }

    fun current(lines: List<Line>, positionMs: Long): Line? {
        var chosen: Line? = null
        for (line in lines) {
            if (line.timeMs <= positionMs) chosen = line else break
        }
        return chosen
    }
}
