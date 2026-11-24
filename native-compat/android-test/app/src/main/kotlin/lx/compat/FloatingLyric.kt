// SPDX-License-Identifier: Apache-2.0
package lx.compat

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

/** A small overlay for the current lyric line. Showing it still needs the system to allow the window. */
class FloatingLyric(private val context: Context) {
    private var view: TextView? = null

    fun show(text: String) {
        val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val current = view
        if (current == null) {
            val created = TextView(context).apply {
                setTextColor(0xFFFFFFFF.toInt())
                setBackgroundColor(0xCC101014.toInt())
                textSize = 18f
                setPadding(24, 12, 24, 12)
            }
            val type = if (Build.VERSION.SDK_INT >= 26) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT,
            )
            params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            params.y = 48
            manager.addView(created, params)
            view = created
        }
        view?.text = text
    }

    fun text(): String = view?.text?.toString() ?: ""

    fun hide() {
        val current = view ?: return
        val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        manager.removeView(current)
        view = null
    }

    fun visible(): Boolean = view != null
}
