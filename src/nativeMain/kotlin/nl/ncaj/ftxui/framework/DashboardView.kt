package nl.ncaj.ftxui.framework

import kotlin.concurrent.Volatile
import nl.ncaj.ftxui.*

data class DashboardCell(
    val title: String,
    val render: () -> Component,
    val onInput: ((FtxUIEvent) -> Boolean)? = null,
)

open class DashboardView(
    private val cells: List<DashboardCell>,
    val columns: Int = 2,
    private val onFocusedCellChanged: ((Int) -> Unit)? = null,
    private val keybindings: DashboardKeybindings = DashboardKeybindings(),
    private val style: DashboardStyle = DashboardStyle(),
) : InputReceiver {

    @Volatile private var focusedCell: Int = 0

    private var cellComponents: List<Component> = emptyList()
    private var gridContainer: Component? = null

    fun render(state: Int): Component {
        focusedCell = state.coerceIn(0, maxOf(0, cells.lastIndex))

        val cols = columns.coerceAtLeast(1)
        val rows = (cells.size + cols - 1) / cols

        val compiledCells = cells.map { it.render() }
        cellComponents = compiledCells

        val rowContainers = (0 until rows).map { row ->
            val rowComp = horizontal()
            for (col in 0 until cols) {
                val idx = row * cols + col
                val cellComp = compiledCells.getOrNull(idx) ?: renderer { emptyElement() }
                rowComp.add(cellComp)
            }
            rowComp
        }

        val container = vertical()
        for (rowComp in rowContainers) {
            container.add(rowComp)
        }
        gridContainer = container

        val focusedComp = compiledCells.getOrNull(focusedCell)
        focusedComp?.takeFocus()

        return container.decorateRender {
            buildElement(compiledCells)
        }
    }

    override fun onInput(event: FtxUIEvent): Boolean {
        val compiled = cellComponents
        if (compiled.isNotEmpty()) {
            val activeIdx = compiled.indexOfFirst { it.focused }
            if (activeIdx != -1) {
                focusedCell = activeIdx
            }
        }

        val cell = cells.getOrNull(focusedCell)
        if (cell?.onInput?.invoke(event) == true) return true
        val oldFocused = focusedCell
        val handled = when {
            event.matches(keybindings.focusNextKeys, keybindings.focusNextChars) -> { cycleFocus(+1); true }
            event.matches(keybindings.focusPrevKeys, keybindings.focusPrevChars) -> { cycleFocus(-1); true }
            else -> false
        }
        if (handled && focusedCell != oldFocused) {
            val newFocusedComp = cellComponents.getOrNull(focusedCell)
            newFocusedComp?.takeFocus()
            onFocusedCellChanged?.invoke(focusedCell)
        }
        return handled
    }

    private fun cycleFocus(dir: Int) {
        if (cells.isEmpty()) return
        focusedCell = (focusedCell + dir + cells.size) % cells.size
    }

    private fun buildElement(compiledCells: List<Component>): Element {
        if (cells.isEmpty()) return emptyElement()

        val termW = Terminal.size().dimx
        val termH = Terminal.size().dimy - Screen.STATUS_BAR_HEIGHT
        val cols = columns.coerceAtLeast(1)
        val rows = (cells.size + cols - 1) / cols

        val cellW = termW / cols
        val cellH = termH / rows

        val grid = (0 until rows).map { row ->
            val rowCells = (0 until cols).mapNotNull { col ->
                val idx = row * cols + col
                cells.getOrNull(idx)?.let { cell ->
                    val comp = compiledCells[idx]
                    val isFocused = comp.focused
                    buildCell(cell, comp, isFocused, cellW, cellH)
                } ?: filler().size(WidthOrHeight.Width, Constraint.Equal, cellW)
            }
            hbox(*rowCells.toTypedArray())
        }

        return vbox(*grid.toTypedArray())
    }

    private fun buildCell(cell: DashboardCell, comp: Component, focused: Boolean, width: Int, height: Int): Element {
        val content = comp.render()
        val focusedFg = style.focusedTitleForeground.or(Theme.current.accent)
        val titleEl = text(" ${cell.title} ").let { t ->
            if (focused) t.color(focusedFg).bold()
            else style.unfocusedTitleForeground?.let { c -> t.color(c) } ?: t.dim()
        }
        val titled = content
            .size(WidthOrHeight.Width,  Constraint.Equal, width  - 2)
            .size(WidthOrHeight.Height, Constraint.Equal, height - 2)
            .window(titleEl)
        return if (focused) titled else titled.dim()
    }
}
