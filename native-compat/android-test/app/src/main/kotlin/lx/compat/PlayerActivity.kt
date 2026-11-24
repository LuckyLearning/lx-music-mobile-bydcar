// SPDX-License-Identifier: Apache-2.0
package lx.compat

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor

/**
 * Phone and car shell for the test client. The service owns playback; this screen only sends commands.
 * Online source search is not offered here. Queue filtering is local.
 */
class PlayerActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val chrome = CarChrome()
    private var controller: MediaController? = null
    private var systemBottomInset = 0
    private var dragging = false
    private var fontRepeat: Runnable? = null

    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var body: LinearLayout
    private lateinit var lyric: TextView
    private lateinit var cover: TextView
    private lateinit var seek: SeekBar
    private lateinit var position: TextView
    private lateinit var transport: LinearLayout
    private lateinit var playButton: Button
    private lateinit var modeButton: Button
    private lateinit var favoriteButton: Button
    private lateinit var errorText: TextView
    private lateinit var filter: EditText
    private lateinit var queue: TextView
    private lateinit var settings: LinearLayout
    private lateinit var chromeViews: List<View>

    private val tick = object : Runnable {
        override fun run() {
            val car = carLayout()
            if (car) chrome.tick(SystemClock.uptimeMillis())
            applyChrome(car)
            renderProgress()
            handler.postDelayed(this, 250)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN or
                android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
        )
        buildUi()
        root.isFocusableInTouchMode = true
        root.requestFocus()
        root.setOnApplyWindowInsetsListener { _, insets ->
            val bottom = insets.systemWindowInsetBottom
            if (bottom != systemBottomInset) systemBottomInset = bottom
            insets
        }
        root.requestApplyInsets()
        root.viewTreeObserver.addOnGlobalLayoutListener { applyWindow() }
        connect()
    }

    override fun onResume() {
        super.onResume()
        chrome.tap(SystemClock.uptimeMillis())
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }

    override fun onDestroy() {
        controller?.release()
        controller = null
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        if (requestCode == REQUEST_AUDIO) addAudio(data)
        if (requestCode == REQUEST_LYRIC) readLyric(data.data)
    }

    private fun connect() {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        future.addListener({
            try {
                controller = future.get()
                controller?.addListener(object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) {
                        render()
                    }
                })
                controller?.sendCustomCommand(SessionCommand(PlaybackService.ACTION_LAUNCH, Bundle.EMPTY), Bundle.EMPTY)
                render()
            } catch (_: ExecutionException) {
                status.text = "播放服务没有连上"
            } catch (_: InterruptedException) {
                status.text = "播放服务没有连上"
            }
        }, Executor { command -> handler.post(command) })
    }

    private fun buildUi() {
        val pad = dp(12)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF101014.toInt())
            setPadding(pad, pad, pad, pad)
            setOnClickListener { reveal() }
        }
        status = text("正在连接播放服务", 14f)
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        lyric = text("还没有歌词。可以打开一个文本歌词，字号最小是 1，没有上限。", 21f).apply {
            setOnClickListener { reveal() }
        }
        cover = text("封面", 18f).apply {
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF1C1C22.toInt())
            minimumHeight = dp(120)
        }
        val lyricScroll = ScrollView(this).apply { addView(lyric) }
        body.addView(lyricScroll, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 7f))
        body.addView(cover, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 3f))
        seek = SeekBar(this).apply {
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) position.text = format(progress.toLong())
                }
                override fun onStartTrackingTouch(bar: SeekBar?) { dragging = true }
                override fun onStopTrackingTouch(bar: SeekBar?) {
                    dragging = false
                    controller?.seekTo(bar?.progress?.toLong() ?: 0L)
                }
            })
        }
        position = text("00:00 / 00:00", 13f)
        transport = row(
            button("上一首") { controller?.seekToPreviousMediaItem() },
            button("播放") { togglePlay() }.also { playButton = it },
            button("下一首") { controller?.seekToNextMediaItem() },
            button("顺序") { cycleMode() }.also { modeButton = it },
            button("收藏") { toggleFavorite() }.also { favoriteButton = it },
            button("打开音频") { openAudio() },
            button("测试音") { addTone() },
            button("歌词") { openLyric() },
            button("重试") { controller?.let { it.prepare(); it.play() } },
            button("设置") { toggleSettings() },
            button("诊断") { startActivity(Intent(this, DiagnosticActivity::class.java)) },
        )
        errorText = text("", 13f)
        val transportScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(transport)
        }
        filter = EditText(this).apply {
            hint = "筛选当前队列"
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF9A9AA3.toInt())
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) { renderQueue() }
            })
        }
        queue = text("队列是空的", 14f)
        val queueScroll = ScrollView(this).apply { addView(queue) }
        settings = settingsPanel()
        settings.visibility = View.GONE
        chromeViews = listOf(seek, position, transportScroll, filter)
        root.addView(status)
        root.addView(body, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(errorText)
        chromeViews.forEach { root.addView(it, match()) }
        root.addView(queueScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(settings, match())
        setContentView(root)
        applyFont()
    }

    private fun settingsPanel(): LinearLayout {
        val prefs = ClientPrefs.prefs(this)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF1C1C22.toInt())
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        panel.addView(text("设置打开时，车机底部控制条不会自动收起。", 13f))
        panel.addView(toggle("车机布局", prefs.getBoolean(ClientPrefs.CAR, false)) { on ->
            prefs.edit().putBoolean(ClientPrefs.CAR, on).apply()
            chrome.tap(SystemClock.uptimeMillis())
            applyWindow()
        })
        panel.addView(toggle("开机自启", prefs.getBoolean(ClientPrefs.BOOT, false)) { on ->
            prefs.edit().putBoolean(ClientPrefs.BOOT, on).apply()
        })
        panel.addView(toggle("启动后自动播放", prefs.getBoolean(ClientPrefs.AUTOPLAY, false)) { on ->
            prefs.edit().putBoolean(ClientPrefs.AUTOPLAY, on).apply()
        })
        panel.addView(text("开机自启不会在自动播放关闭时出声，也没有队列时不会挑一首。", 13f))
        val smaller = button("歌词 -") {}
        val larger = button("歌词 +") {}
        bindStep(smaller, -1)
        bindStep(larger, 1)
        panel.addView(row(smaller, larger))
        panel.addView(text("在线搜索和各平台歌单还没有完成音源对照，这里只筛选本机队列。", 13f))
        return panel
    }

    private fun toggle(label: String, initial: Boolean, onChange: (Boolean) -> Unit): Switch {
        return Switch(this).apply {
            text = label
            isChecked = initial
            setTextColor(0xFFFFFFFF.toInt())
            setOnCheckedChangeListener { _, checked -> onChange(checked) }
        }
    }

    private fun bindStep(button: Button, delta: Int) {
        button.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    stepFont(delta)
                    val repeat = object : Runnable {
                        override fun run() {
                            stepFont(delta)
                            handler.postDelayed(this, 80)
                        }
                    }
                    fontRepeat = repeat
                    handler.postDelayed(repeat, 400)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    fontRepeat?.let { handler.removeCallbacks(it) }
                    true
                }
                else -> false
            }
        }
    }

    private fun stepFont(delta: Int) {
        val prefs = ClientPrefs.prefs(this)
        val next = PlayerRules.stepLrcFontPt(prefs.getInt(ClientPrefs.FONT, PlayerRules.DEFAULT_VERTICAL_LRC_FONT_PT).toDouble(), delta)
        prefs.edit().putInt(ClientPrefs.FONT, next).apply()
        applyFont()
        applyWindow()
    }

    private fun applyFont() {
        val pt = ClientPrefs.prefs(this).getInt(ClientPrefs.FONT, PlayerRules.DEFAULT_VERTICAL_LRC_FONT_PT)
        val size = if (carLayout()) (36 * pt / PlayerRules.DEFAULT_HORIZONTAL_LRC_FONT_PT) else pt
        val desiredPx = size * resources.displayMetrics.scaledDensity
        if (kotlin.math.abs(lyric.textSize - desiredPx) > 0.5f) lyric.textSize = size.toFloat()
    }

    private fun applyWindow() {
        val width = root.width
        val height = root.height
        if (width == 0 || height == 0) return
        val density = resources.displayMetrics.density
        val mode = PlayerRules.selectPlayerLayout(width, height, ClientPrefs.prefs(this).getBoolean(ClientPrefs.CAR, false))
        val pane = if (mode == "car") PlayerRules.carPane(width, height, density) else mode
        val narrow = pane == "car-30"
        cover.visibility = if (narrow) View.GONE else View.VISIBLE
        body.orientation = if (mode == "car" && !narrow) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        val lyrics = body.getChildAt(0).layoutParams as LinearLayout.LayoutParams
        val art = cover.layoutParams as LinearLayout.LayoutParams
        if (body.orientation == LinearLayout.HORIZONTAL) {
            lyrics.width = 0
            lyrics.height = LinearLayout.LayoutParams.MATCH_PARENT
            lyrics.weight = 7f
            art.width = 0
            art.height = LinearLayout.LayoutParams.MATCH_PARENT
            art.weight = 3f
        } else {
            lyrics.width = LinearLayout.LayoutParams.MATCH_PARENT
            lyrics.height = 0
            lyrics.weight = 1f
            art.width = LinearLayout.LayoutParams.MATCH_PARENT
            art.height = dp(if (narrow) 0 else 140)
            art.weight = 0f
        }
        val bottom = dp(12) + PlayerRules.bottomPaddingPx(mode == "car", systemBottomInset, density)
        if (root.paddingBottom != bottom) root.setPadding(dp(12), dp(12), dp(12), bottom)
        val label = when (pane) {
            "car-full" -> "车机全屏"
            "car-70" -> "车机 70%"
            "car-30" -> "车机 30%"
            "horizontal" -> "手机横屏"
            else -> "手机竖屏"
        }
        val pt = ClientPrefs.prefs(this).getInt(ClientPrefs.FONT, PlayerRules.DEFAULT_VERTICAL_LRC_FONT_PT)
        val nextStatus = label + "  ·  " + playState() + "  · 歌词 " + pt
        if (status.text.toString() != nextStatus) status.text = nextStatus
        applyFont()
        applyChrome(mode == "car")
    }

    private fun applyChrome(car: Boolean) {
        val show = !car || chrome.controlsVisible || chrome.settingsOpen
        val visibility = if (show) View.VISIBLE else View.GONE
        chromeViews.forEach { it.visibility = visibility }
        if (!show && settings.visibility == View.VISIBLE) settings.visibility = View.GONE
    }

    private fun reveal() {
        if (!carLayout()) return
        chrome.tap(SystemClock.uptimeMillis())
        applyChrome(true)
    }

    private fun carLayout(): Boolean {
        return PlayerRules.selectPlayerLayout(
            root.width,
            root.height,
            ClientPrefs.prefs(this).getBoolean(ClientPrefs.CAR, false),
        ) == "car"
    }

    private fun toggleSettings() {
        val open = settings.visibility != View.VISIBLE
        settings.visibility = if (open) View.VISIBLE else View.GONE
        chrome.setSettingsOpen(open, SystemClock.uptimeMillis())
        applyChrome(carLayout())
    }

    private fun render() {
        val current = controller ?: return
        playButton.text = if (current.isPlaying) "暂停" else "播放"
        modeButton.text = when {
            current.shuffleModeEnabled -> "随机"
            current.repeatMode == Player.REPEAT_MODE_ONE -> "单曲"
            current.repeatMode == Player.REPEAT_MODE_ALL -> "列表"
            else -> "顺序"
        }
        val id = current.currentMediaItem?.mediaId
        favoriteButton.text = if (id != null && favorites().contains(id)) "已收藏" else "收藏"
        val title = current.mediaMetadata.title ?: current.currentMediaItem?.mediaMetadata?.title
        cover.text = title?.toString()?.ifEmpty { null } ?: "封面"
        val error = current.playerError
        errorText.text = if (error == null) "" else "播放失败：" + (error.message ?: error.errorCodeName)
        renderQueue()
        renderProgress()
        applyWindow()
    }

    private fun renderQueue() {
        val current = controller ?: return
        val query = filter.text?.toString()?.trim()?.lowercase() ?: ""
        val lines = (0 until current.mediaItemCount).mapNotNull { index ->
            val item = current.getMediaItemAt(index)
            val title = item.mediaMetadata.title?.toString()?.ifEmpty { null } ?: item.mediaId
            if (query.isNotEmpty() && !title.lowercase().contains(query)) return@mapNotNull null
            val mark = if (index == current.currentMediaItemIndex) "▶ " else ""
            mark + (index + 1) + ". " + title
        }
        queue.text = if (lines.isEmpty()) "队列是空的" else lines.joinToString("\n")
    }

    private fun renderProgress() {
        val current = controller ?: return
        if (dragging) return
        val duration = current.duration.coerceAtLeast(0L)
        val bounded = if (duration > Int.MAX_VALUE) Int.MAX_VALUE else duration.toInt()
        seek.max = if (bounded <= 0) 1 else bounded
        val pos = current.currentPosition.coerceAtLeast(0L)
        seek.progress = if (pos > Int.MAX_VALUE) Int.MAX_VALUE else pos.toInt()
        position.text = format(pos) + " / " + format(if (duration < 0) 0 else duration)
    }

    private fun togglePlay() {
        val current = controller ?: return
        if (current.isPlaying) current.pause() else current.play()
    }

    private fun cycleMode() {
        val current = controller ?: return
        when {
            current.shuffleModeEnabled -> {
                current.shuffleModeEnabled = false
                current.repeatMode = Player.REPEAT_MODE_OFF
            }
            current.repeatMode == Player.REPEAT_MODE_OFF -> current.repeatMode = Player.REPEAT_MODE_ALL
            current.repeatMode == Player.REPEAT_MODE_ALL -> current.repeatMode = Player.REPEAT_MODE_ONE
            else -> {
                current.repeatMode = Player.REPEAT_MODE_OFF
                current.shuffleModeEnabled = true
            }
        }
    }

    private fun toggleFavorite() {
        val id = controller?.currentMediaItem?.mediaId ?: return
        val values = favorites()
        if (!values.add(id)) values.remove(id)
        ClientPrefs.prefs(this).edit().putString(ClientPrefs.FAVORITES, Json.stringify(values.toList())).apply()
        render()
    }

    private fun favorites(): MutableSet<String> {
        val raw = ClientPrefs.prefs(this).getString(ClientPrefs.FAVORITES, null) ?: return linkedSetOf()
        return try {
            Json.parse(raw).list().mapNotNull { it as? String }.toMutableSet()
        } catch (_: RuntimeException) {
            linkedSetOf()
        }
    }

    private fun openAudio() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }, REQUEST_AUDIO)
    }

    private fun openLyric() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, REQUEST_LYRIC)
    }

    private fun addAudio(data: Intent) {
        val current = controller ?: return
        val uris = mutableListOf<Uri>()
        val clip = data.clipData
        if (clip != null) {
            for (index in 0 until clip.itemCount) uris.add(clip.getItemAt(index).uri)
        } else if (data.data != null) {
            uris.add(data.data!!)
        }
        uris.forEach { uri ->
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
                // A one-shot grant still plays until the process drops it.
            }
            current.addMediaItem(item(uri.toString(), displayName(uri), uri))
        }
        if (current.playbackState == Player.STATE_IDLE) current.prepare()
    }

    private fun addTone() {
        val current = controller ?: return
        val uri = Uri.parse("asset:///tone.wav")
        current.addMediaItem(item("tone", "测试音", uri))
        current.seekTo(current.mediaItemCount - 1, 0)
        current.prepare()
        current.play()
    }

    private fun item(id: String, title: String, uri: Uri): MediaItem {
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(uri)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
            .build()
    }

    private fun readLyric(uri: Uri?) {
        if (uri == null) return
        val text = try {
            contentResolver.openInputStream(uri)?.use { input ->
                val bytes = ByteArray(65_536)
                val count = input.read(bytes)
                if (count <= 0) "" else String(bytes, 0, count, Charsets.UTF_8)
            } ?: ""
        } catch (_: Exception) {
            "歌词没有读出来"
        }
        lyric.text = if (text.length > 51_200) text.substring(0, 51_200) else text
    }

    private fun displayName(uri: Uri): String {
        val cursor = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null) ?: return "音频"
        cursor.use {
            if (!it.moveToFirst()) return "音频"
            return it.getString(0) ?: "音频"
        }
    }

    private fun playState(): String {
        val current = controller ?: return "未连接"
        return when (current.playbackState) {
            Player.STATE_BUFFERING -> "缓冲"
            Player.STATE_READY -> if (current.isPlaying) "播放中" else "已暂停"
            Player.STATE_ENDED -> "已结束"
            else -> "空闲"
        }
    }

    private fun text(value: String, size: Float): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, dp(4), 0, dp(4))
        }
    }

    private fun button(label: String, action: () -> Unit): Button {
        return Button(this).apply {
            text = label
            setOnClickListener {
                reveal()
                action()
            }
        }
    }

    private fun row(vararg views: View): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            views.forEach { addView(it) }
        }
    }

    private fun match() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun format(ms: Long): String {
        val total = (ms / 1000).toInt()
        return "%02d:%02d".format(total / 60, total % 60)
    }

    companion object {
        private const val REQUEST_AUDIO = 41
        private const val REQUEST_LYRIC = 42
    }
}
