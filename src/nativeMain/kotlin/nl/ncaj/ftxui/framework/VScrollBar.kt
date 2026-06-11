package nl.ncaj.ftxui.framework

import nl.ncaj.ftxui.*

internal fun vScrollBar(
    scrollY: Int,
    total: Int,
    visible: Int,
    thumbColor: Color = Theme.current.scrollThumb
): Element {
    if (total <= visible) return vbox(*(0 until maxOf(1, visible)).map { text(" ") }.toTypedArray())
    val thumbH = maxOf(1, visible * visible / total)
    val thumbY = ((scrollY.toLong() * maxOf(0, visible - thumbH)) / maxOf(1, total - visible)).toInt()
    return vbox(*(0 until visible).map { i ->
        if (i in thumbY until thumbY + thumbH) text("▐").color(thumbColor) else text("▕").dim()
    }.toTypedArray())
}
