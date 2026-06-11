package nl.ncaj.ftxui.framework

import nl.ncaj.ftxui.*

data class DashboardCell(
    val title: String,
    val render: () -> Component,
    val onInput: ((FtxUIEvent) -> Boolean)? = null,
)

fun AppContext.dashboard(
    cells: List<DashboardCell>,
    columns: Int = 2,
    style: DashboardStyle = DashboardStyle()
): Component {
    val cols = columns.coerceAtLeast(1)
    val rows = (cells.size + cols - 1) / cols

    val compiledCells = cells.map { it.render() }

    val rowContainers = (0 until rows).map { r ->
        val rowComp = horizontal()
        for (c in 0 until cols) {
            val idx = r * cols + c
            val cellComp = compiledCells.getOrNull(idx) ?: renderer { emptyElement() }
            rowComp.add(cellComp)
        }
        rowComp
    }

    val gridContainer = vertical()
    for (rowComp in rowContainers) {
        gridContainer.add(rowComp)
    }

    val viewport = viewport()

    return gridContainer.decorateRender {
        if (cells.isEmpty()) return@decorateRender emptyElement()

        val cellW = viewport.width / cols
        val cellH = viewport.height / rows

        val grid = (0 until rows).map { r ->
            val rowCells = (0 until cols).map { c ->
                val idx = r * cols + c
                val cell = cells.getOrNull(idx)
                if (cell != null) {
                    val comp = compiledCells[idx]
                    val isFocused = comp.focused
                    val content = comp.render()
                    val focusedFg = style.focusedTitleForeground.or(Theme.current.accent)
                    val titleEl = text(" ${cell.title} ").let { t ->
                        if (isFocused) t.color(focusedFg).bold()
                        else style.unfocusedTitleForeground?.let { color -> t.color(color) } ?: t.dim()
                    }
                    val titled = content
                        .size(WidthOrHeight.Width,  Constraint.Equal, cellW  - 2)
                        .size(WidthOrHeight.Height, Constraint.Equal, cellH - 2)
                        .window(titleEl)
                    if (isFocused) titled else titled.dim()
                } else {
                    filler().size(WidthOrHeight.Width, Constraint.Equal, cellW)
                }
            }
            hbox(*rowCells.toTypedArray())
        }

        viewport.measure(vbox(*grid.toTypedArray()))
    }
}
