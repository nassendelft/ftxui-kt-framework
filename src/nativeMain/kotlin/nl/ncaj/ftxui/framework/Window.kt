package nl.ncaj.ftxui.framework

import nl.ncaj.ftxui.*

data class WindowSection(val lines: Int, val content: () -> Element)

interface Window<S> {
    val activeSubWindow: Window<*>? get() = null
    val extraHeader: WindowSection? get() = null
    val extraFooter: WindowSection? get() = null

    fun getVisibleHeight(): Int = Terminal.size().dimy

    fun contentHeight(): Int {
        val h = extraHeader?.lines ?: 0
        val f = extraFooter?.lines ?: 0
        return maxOf(1, getVisibleHeight() - h - f)
    }

    fun wrapWithDecorations(inner: Element): Element {
        val h = extraHeader
        val f = extraFooter
        if (h == null && f == null) return inner
        val parts = buildList {
            if (h != null) add(h.content())
            add(inner)
            if (f != null) add(f.content())
        }
        return vbox(*parts.toTypedArray())
    }

    fun render(state: S): Component

    fun onInput(event: FtxUIEvent): Boolean {
        return activeSubWindow?.onInput(event) ?: false
    }
}
