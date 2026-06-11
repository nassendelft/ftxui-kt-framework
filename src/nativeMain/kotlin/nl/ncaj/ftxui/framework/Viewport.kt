package nl.ncaj.ftxui.framework

import nl.ncaj.ftxui.*

/**
 * Self-measuring slot for scrollable components.
 *
 * Wrap the element that should fill the component's slot with [measure] on
 * every build, then read [height]/[width] to decide how many rows to render
 * and how far paging keys should jump. The layout writes the rectangle it
 * assigned to the wrapped element into a backing box during each render
 * (ftxui's `reflect`), so the values are exact for any nesting — no terminal
 * size arithmetic or chrome offsets.
 *
 * Layout runs after build, so a frame whose slot size changed (terminal
 * resize, surrounding chrome appearing) is built with the previous
 * measurement. [measure] therefore posts a check that runs after the frame
 * and requests one corrective redraw when the measurement changed; once the
 * size is stable no extra frames are produced. Before the first render the
 * terminal size is used as a safe overestimate (overflow is clipped).
 */
class Viewport internal constructor(private val context: AppContext) {
    private val box = Box()

    val width: Int get() = if (box.width > 0) box.width else context.terminalSize.width
    val height: Int get() = if (box.height > 0) box.height else context.terminalSize.height

    fun measure(element: Element): Element {
        val builtWidth = box.width
        val builtHeight = box.height
        context.post { if (box.width != builtWidth || box.height != builtHeight) context.requestRedraw() }
        return element.flex().reflect(box)
    }
}

fun AppContext.viewport() = Viewport(this)