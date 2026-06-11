package nl.ncaj.ftxui.framework

import kotlinx.coroutines.*
import nl.ncaj.ftxui.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

internal fun AppContext.perfOverlay(
    isVisible: () -> Boolean,
    getExtraLines: () -> List<String>
): Component {
    var avgFrameMs = 0f
    var lastFrameMark: TimeSource.Monotonic.ValueTimeMark? = null

    var displayedFps = 0
    var displayedFrameMs = 0f
    var lastUpdateMark = TimeSource.Monotonic.markNow()

    return renderer {
        val now = TimeSource.Monotonic.markNow()
        val prev = lastFrameMark
        if (prev != null) {
            val ms = prev.elapsedNow().inWholeMicroseconds / 1000f
            avgFrameMs = avgFrameMs * 0.9f + ms * 0.1f
        }
        lastFrameMark = now

        if (displayedFps == 0 || now - lastUpdateMark >= 1.seconds) {
            displayedFps = if (avgFrameMs > 0) (1000f / avgFrameMs).toInt() else 0
            displayedFrameMs = avgFrameMs
            lastUpdateMark = now
        }

        val termSize = terminalSize
        val frameInt = (displayedFrameMs * 10).toInt()
        val lines = buildList {
            add("FPS:   $displayedFps")
            add("Frame: ${frameInt / 10}.${frameInt % 10}ms")
            addAll(getExtraLines())
            add("Term:  ${termSize.width}×${termSize.height}")
        }
        val body = vbox(*lines.map { hbox(text("  $it  ")) }.toTypedArray())
        hbox(filler(), body.window(text(" perf ").dim()).clearUnder(), text(" "))
    }
}
