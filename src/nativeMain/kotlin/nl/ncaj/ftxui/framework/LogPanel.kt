package nl.ncaj.ftxui.framework

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.ncaj.ftxui.*
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

internal class LogPanel(
    private val scope: CoroutineScope,
    private val requestFrame: () -> Unit,
) {
    @Volatile var isOpen: Boolean = false
        private set
    @Volatile var progress: Float = 0f
        private set

    @Volatile private var scrollOffset: Int = Int.MAX_VALUE
    @Volatile private var minLevel: Logger.Level? = null
    private var animJob: Job? = null

    fun open() {
        isOpen = true
        scrollOffset = Int.MAX_VALUE
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
        val entries = filteredEntries()
        val termH = Terminal.size().dimy
        val targetH = (termH * 0.45f).toInt().coerceAtLeast(8)
        val visH = maxOf(1, targetH - 4)
        val maxScroll = maxOf(0, entries.size - visH)
        scrollOffset = scrollOffset.coerceIn(0, maxScroll)
        when {
            event.isKey(Key.Escape) || event.isKey(Key.CtrlL) -> close()
            event.isKey(Key.ArrowDown) || isChar(event, "j") ->
                scrollOffset = (scrollOffset + 1).coerceAtMost(maxScroll)
            event.isKey(Key.ArrowUp) || isChar(event, "k") ->
                scrollOffset = (scrollOffset - 1).coerceAtLeast(0)
            isChar(event, "G") -> scrollOffset = maxScroll
            isChar(event, "g") -> scrollOffset = 0
            event.isKey(Key.CtrlD) -> scrollOffset = (scrollOffset + visH / 2).coerceAtMost(maxScroll)
            event.isKey(Key.CtrlU) -> scrollOffset = (scrollOffset - visH / 2).coerceAtLeast(0)
            isChar(event, "f") -> {
                minLevel = when (minLevel) {
                    null               -> Logger.Level.Info
                    Logger.Level.Info  -> Logger.Level.Warn
                    Logger.Level.Warn  -> Logger.Level.Error
                    Logger.Level.Error -> null
                    else               -> null
                }
                scrollOffset = Int.MAX_VALUE
            }
        }
        requestFrame()
        return true
    }

    fun buildElement(): Element? {
        if (progress == 0f) return null
        val entries = filteredEntries()
        val allCount = Logger.entries().size
        val termH = Terminal.size().dimy
        val targetH = (termH * 0.45f).toInt().coerceAtLeast(8)
        val currentH = (targetH * progress).toInt()
        if (currentH < 2) return null
        val visH = maxOf(1, currentH - 4)
        val maxScroll = maxOf(0, entries.size - visH)
        val scroll = scrollOffset.coerceIn(0, maxScroll)
        val slice = entries.drop(scroll).take(visH)
        val rows: List<Element> = if (entries.isEmpty()) {
            listOf(hbox(text("  "), text("No log entries").dim(), text("  ")))
        } else {
            slice.map { entry ->
                val (icon, color) = when (entry.level) {
                    Logger.Level.Debug -> "·" to Color.GrayLight
                    Logger.Level.Info  -> "ℹ" to Theme.current.info
                    Logger.Level.Warn  -> "⚠" to Theme.current.warning
                    Logger.Level.Error -> "✗" to Theme.current.error
                }
                hbox(text("  ${entry.time} ").dim(), text("$icon ").color(color), text(entry.message), text("  "))
            }
        }
        val filterLabel = when (minLevel) {
            null               -> "all"
            Logger.Level.Info  -> "≥info"
            Logger.Level.Warn  -> "≥warn"
            Logger.Level.Error -> "error"
            else               -> ""
        }
        val titleCount = if (minLevel == null) "${entries.size}" else "${entries.size}/${allCount}"
        val header = hbox(
            text("  "),
            text("Logs").color(Theme.current.accent).bold(),
            text(" [$titleCount]${if (filterLabel.isNotEmpty()) "  $filterLabel" else ""}").color(Theme.current.accent),
            filler(),
        )
        val content = hbox(vbox(*rows.toTypedArray()).flex(), vScrollBar(scroll, entries.size, rows.size))
        val hint = hbox(text("  "), text("j/k scroll  g/G top/bottom  f filter  Esc/^L close").dim(), text("  "))
        val body = vbox(header, separator(), content, separator(), hint, text(""))
        return vbox(
            filler(),
            body.borderStyled(BorderStyle.Heavy, Theme.current.accent)
                .clearUnder()
                .size(WidthOrHeight.Height, Constraint.Equal, currentH)
                .xflex(),
        )
    }

    private fun filteredEntries(): List<Logger.Entry> {
        val min = minLevel ?: return Logger.entries()
        return Logger.entries().filter { it.level >= min }
    }
}
