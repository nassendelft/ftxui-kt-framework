package nl.ncaj.ftxui.framework

import nl.ncaj.ftxui.*
import kotlin.time.Duration.Companion.seconds

data class Toast(
    val message: String,
    val type: Type = Type.Info,
) {
    enum class Type { Info, Success, Warning, Error }

    companion object {
        val SHORT = 2.seconds
        val LONG = 5.seconds
    }
}

data class NotificationRecord(
    val message: String,
    val type: Toast.Type,
    val timestamp: String,
)

internal fun buildToastPill(toast: Toast, progress: Float): Element {
    val (icon, color) = when (toast.type) {
        Toast.Type.Info -> "ℹ" to Theme.current.info
        Toast.Type.Success -> "✓" to Theme.current.success
        Toast.Type.Warning -> "⚠" to Theme.current.warning
        Toast.Type.Error -> "✗" to Theme.current.error
    }
    val innerWidth = 5 + toast.message.length + 2
    val boxW = innerWidth + 2
    val totalPerim = 2 * boxW + 2
    val dimColor = Color.GrayDark
    val transitionWidth = 5f
    val stripe = totalPerim * 0.30f
    val frontier = progress * (totalPerim + stripe)

    fun borderEl(pos: Int, thickChar: String, medChar: String, thinChar: String): Element {
        val distA = pos - frontier
        val distB = pos - (frontier - stripe)
        val t = ((-distA / transitionWidth) + 0.5f).coerceIn(0f, 1f)
        val blended = Color.interpolate(t, color, dimColor)
        val char = when {
            distA > 0f -> thickChar; distB > 0f -> medChar; else -> thinChar
        }
        return text(char).color(blended)
    }

    val topElements = buildList {
        add(borderEl(0, "╔", "┏", "╭"))
        for (col in 1 until boxW - 1) add(borderEl(col, "═", "━", "─"))
        add(borderEl(boxW - 1, "╗", "┓", "╮"))
    }
    val leftEl = borderEl(2 * boxW + 1, "║", "┃", "│")
    val rightEl = borderEl(boxW, "║", "┃", "│")
    val contentEl = hbox(text("  $icon  ").color(color), text(toast.message), text("  "))
    val bottomElements = buildList {
        add(borderEl(2 * boxW, "╚", "┗", "╰"))
        for (col in 1 until boxW - 1) add(borderEl(2 * boxW - col, "═", "━", "─"))
        add(borderEl(boxW + 1, "╝", "┛", "╯"))
    }
    return vbox(
        hbox(*topElements.toTypedArray()),
        hbox(leftEl, contentEl, rightEl),
        hbox(*bottomElements.toTypedArray()),
    ).clearUnder()
}
