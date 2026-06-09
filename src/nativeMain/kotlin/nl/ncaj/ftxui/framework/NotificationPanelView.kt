package nl.ncaj.ftxui.framework

import kotlin.concurrent.Volatile
import kotlinx.coroutines.*
import nl.ncaj.ftxui.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

internal class NotificationPanelView(
    private val scope: CoroutineScope,
    private val requestFrame: () -> Unit,
    private val getLog: () -> List<NotificationRecord>,
) {
    @Volatile var isOpen: Boolean = false
        private set
    @Volatile var progress: Float = 0f
        private set

    @Volatile private var scrollOffset: Int = 0
    private var animJob: Job? = null

    fun open() {
        isOpen = true
        scrollOffset = 0
        animJob?.cancel()
        animJob = scope.launch {
            val start = TimeSource.Monotonic.markNow()
            val duration = 180.milliseconds
            while (progress < 1f) {
                delay(16.milliseconds)
                progress = (start.elapsedNow() / duration).toFloat().coerceIn(0f, 1f)
                requestFrame()
            }
        }
    }

    fun close() {
        isOpen = false
        animJob?.cancel()
        animJob = scope.launch {
            val start = TimeSource.Monotonic.markNow()
            val startProgress = progress
            val duration = 130.milliseconds
            while (progress > 0f) {
                delay(16.milliseconds)
                val t = (start.elapsedNow() / duration).toFloat().coerceIn(0f, 1f)
                progress = (startProgress * (1f - t)).coerceAtLeast(0f)
                requestFrame()
            }
        }
    }

    fun handleInput(event: FtxUIEvent): Boolean {
        val log = getLog()
        val visH = maxOf(1, Terminal.size().dimy - 6)
        val maxScroll = maxOf(0, log.size - visH)
        when {
            event.isKey(Key.Escape) || event.isKey(Key.CtrlN) -> close()
            event.isKey(Key.ArrowDown) || isChar(event, "j") ->
                scrollOffset = (scrollOffset + 1).coerceAtMost(maxScroll)
            event.isKey(Key.ArrowUp) || isChar(event, "k") ->
                scrollOffset = (scrollOffset - 1).coerceAtLeast(0)
            isChar(event, "G") -> scrollOffset = maxScroll
            isChar(event, "g") -> scrollOffset = 0
        }
        requestFrame()
        return true
    }

    fun buildElement(): Element? {
        if (progress == 0f) return null
        val termW = Terminal.size().dimx
        val termH = Terminal.size().dimy
        val targetWidth = (termW * 0.38f).toInt().coerceIn(42, 90)
        val currentWidth = (targetWidth * progress).toInt()
        if (currentWidth < 3) return null
        val log = getLog()
        val visH = maxOf(1, termH - 4)
        val maxScroll = maxOf(0, log.size - visH)
        val scroll = scrollOffset.coerceIn(0, maxScroll)
        val slice = log.drop(scroll).take(visH)
        val rows: List<Element> = if (log.isEmpty()) {
            listOf(hbox(text("  "), text("No notifications yet").dim(), text("  ")))
        } else {
            slice.map { record ->
                val (icon, color) = notifIconAndColor(record.type)
                hbox(text("  ${record.timestamp} ").dim(), text("$icon  ").color(color), text(record.message), text("  "))
            }
        }
        val header = hbox(
            text("  "),
            text("Notifications").color(Theme.current.accent).bold(),
            text(" [${log.size}]").color(Theme.current.accent),
            filler(),
        )
        val content = hbox(vbox(*rows.toTypedArray()).flex(), vScrollBar(scroll, log.size, rows.size))
        val hint = hbox(text("  "), text("j/k scroll  Esc/^N close").dim(), text("  "))
        val body = vbox(header, separator(), content, separator(), hint, text(""))
        return hbox(
            filler(),
            body.borderStyled(BorderStyle.Heavy, Theme.current.accent)
                .clearUnder()
                .size(WidthOrHeight.Width, Constraint.Equal, currentWidth)
                .yflex(),
        )
    }

    private fun notifIconAndColor(type: Toast.Type): Pair<String, Color> = when (type) {
        Toast.Type.Info    -> "ℹ" to Theme.current.info
        Toast.Type.Success -> "✓" to Theme.current.success
        Toast.Type.Warning -> "⚠" to Theme.current.warning
        Toast.Type.Error   -> "✗" to Theme.current.error
    }
}
