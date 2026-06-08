package nl.ncaj.ftxui.framework

import kotlin.concurrent.Volatile
import nl.ncaj.ftxui.*

data class DashboardCell(
    val title: String,
    val render: () -> Component,
    val onInput: ((FtxUIEvent) -> Boolean)? = null,
)

open class DashboardWindow(
    private val cells: List<DashboardCell>,
    val columns: Int = 2,
    override val extraHeader: WindowSection? = null,
    override val extraFooter: WindowSection? = null,
) : Window<Int> {

    @Volatile private var focusedCell: Int = 0

    override fun getVisibleHeight(): Int = Terminal.size().dimy - Screen.STATUS_BAR_HEIGHT

    override fun render(state: Int): Component {
        focusedCell = state.coerceIn(0, maxOf(0, cells.lastIndex))
        return renderer { buildElement() }
    }

    override fun onInput(event: FtxUIEvent): Boolean {
        val cell = cells.getOrNull(focusedCell)
        if (cell?.onInput?.invoke(event) == true) return true
        return when {
            event.isKey(Key.Tab)        -> { cycleFocus(+1); true }
            event.isKey(Key.TabReverse) -> { cycleFocus(-1); true }
            else -> false
        }
    }

    private fun cycleFocus(dir: Int) {
        if (cells.isEmpty()) return
        focusedCell = (focusedCell + dir + cells.size) % cells.size
    }

    private fun buildElement(): Element {
        if (cells.isEmpty()) return emptyElement()

        val termW = Terminal.size().dimx
        val termH = contentHeight()
        val cols = columns.coerceAtLeast(1)
        val rows = (cells.size + cols - 1) / cols

        val cellW = termW / cols
        val cellH = termH / rows

        val grid = (0 until rows).map { row ->
            val rowCells = (0 until cols).mapNotNull { col ->
                val idx = row * cols + col
                cells.getOrNull(idx)?.let { cell ->
                    val isFocused = idx == focusedCell
                    buildCell(cell, isFocused, cellW, cellH)
                } ?: filler().size(WidthOrHeight.Width, Constraint.Equal, cellW)
            }
            hbox(*rowCells.toTypedArray())
        }

        return wrapWithDecorations(vbox(*grid.toTypedArray()))
    }

    private fun buildCell(cell: DashboardCell, focused: Boolean, width: Int, height: Int): Element {
        val comp = cell.render()
        val content = comp.render()
        val titled = content
            .size(WidthOrHeight.Width,  Constraint.Equal, width  - 2)
            .size(WidthOrHeight.Height, Constraint.Equal, height - 2)
            .window(text(" ${cell.title} ").let { if (focused) it.color(Theme.current.accent).bold() else it.dim() })
        return if (focused) titled else titled.dim()
    }
}
