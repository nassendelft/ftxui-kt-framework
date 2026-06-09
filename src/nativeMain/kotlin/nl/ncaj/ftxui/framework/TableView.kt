package nl.ncaj.ftxui.framework

import nl.ncaj.ftxui.*

data class TableColumn<T>(
    val header: String,
    val extract: (T) -> String,
    val renderHeader: ((header: String, width: Int) -> Element)? = null,
    val renderCell: ((item: T, width: Int, focused: Boolean) -> Element)? = null,
)

fun <T> ScreenContext.tableView(
    getRows: () -> List<T>,
    columns: List<TableColumn<T>>,
    onEnter: ((T) -> Unit)? = null,
    onSelectionChanged: ((focusedItem: T?) -> Unit)? = null,
    keybindings: TableKeybindings = TableKeybindings(),
    style: TableStyle = TableStyle()
): Component {
    var focusedRow by mutableStateOf(0)
    var scrollOffset by mutableStateOf(0)
    var sortColumn by mutableStateOf(null as Int?)
    var sortAscending by mutableStateOf(true)

    val pageSize: () -> Int = { maxOf(1, Terminal.size().dimy - 2) }
    val maxScroll: (Int, Int) -> Int = { total, visH -> maxOf(0, total - visH) }

    val sortedRows: () -> List<T> = {
        val sc = sortColumn
        val rows = getRows()
        if (sc == null) rows
        else {
            if (sortAscending) rows.sortedBy { columns[sc].extract(it) }
            else rows.sortedByDescending { columns[sc].extract(it) }
        }
    }

    val ensureScrollCoversSelection: () -> Unit = {
        val visH = pageSize()
        if (focusedRow < scrollOffset) scrollOffset = focusedRow
        if (focusedRow >= scrollOffset + visH) scrollOffset = focusedRow - visH + 1
    }

    val cycleSort: () -> Unit = {
        val cur = sortColumn
        when {
            cur == null -> { sortColumn = 0; sortAscending = true }
            sortAscending -> sortAscending = false
            cur < columns.lastIndex -> { sortColumn = cur + 1; sortAscending = true }
            else -> sortColumn = null
        }
    }

    val base = focusableRenderer { focused ->
        val rows = sortedRows()
        val visH = pageSize()

        val last = maxOf(0, rows.lastIndex)
        focusedRow = focusedRow.coerceIn(0, last)
        scrollOffset = scrollOffset.coerceIn(0, maxScroll(rows.size, visH))
        ensureScrollCoversSelection()

        val colWidths = columns.mapIndexed { i, col ->
            maxOf(col.header.length, rows.maxOfOrNull { col.extract(it).length } ?: 0)
        }

        val headerCells = columns.mapIndexed { i, col ->
            val indicator = when {
                sortColumn == i && sortAscending  -> " ▲"
                sortColumn == i && !sortAscending -> " ▼"
                else -> ""
            }
            col.renderHeader?.invoke(col.header + indicator, colWidths[i])
                ?: run {
                    val headerEl = text(col.header.padEnd(colWidths[i] + 3 - indicator.length))
                        .bold()
                        .let { t -> style.headerForeground?.let { c -> t.color(c) } ?: t }
                    if (indicator.isEmpty()) headerEl
                    else hbox(headerEl, text(indicator).bold()
                        .let { t -> style.sortIndicatorColor?.let { c -> t.color(c) } ?: t })
                }
        }
        val headerEl = hbox(*headerCells.toTypedArray())

        val slice = rows.drop(scrollOffset).take(visH)
        val dataRows = slice.mapIndexed { i, row ->
            val isFocused = scrollOffset + i == focusedRow
            val cells = columns.mapIndexed { ci, col ->
                col.renderCell?.invoke(row, colWidths[ci], isFocused)
                    ?: run {
                        val el = text(col.extract(row).padEnd(colWidths[ci] + 3))
                        if (isFocused && focused) {
                            val fg = style.focusedRowForeground
                            val bg = style.focusedRowBackground
                            when {
                                fg != null && bg != null -> el.color(fg).bgcolor(bg)
                                fg != null -> el.color(fg)
                                bg != null -> el.bgcolor(bg)
                                else -> el.inverted()
                            }
                        } else el
                    }
            }
            hbox(*cells.toTypedArray())
        }

        val emptyRows = (0 until visH - slice.size).map { text("") }
        val dataEl = hbox(
            vbox(*dataRows.toTypedArray(), *emptyRows.toTypedArray()).flex(),
            vScrollBar(scrollOffset, rows.size, visH, style.scrollThumb.or(Theme.current.scrollThumb)),
        )

        vbox(headerEl, separator(), dataEl)
    }

    return base.catchEvent { event ->
        val rows = sortedRows()
        val visH = pageSize()
        val last = maxOf(0, rows.lastIndex)

        when {
            event.matches(keybindings.moveUpKeys, keybindings.moveUpChars) -> {
                if (focusedRow > 0) {
                    focusedRow--
                    ensureScrollCoversSelection()
                    onSelectionChanged?.invoke(rows.getOrNull(focusedRow))
                }
                true
            }
            event.matches(keybindings.moveDownKeys, keybindings.moveDownChars) -> {
                if (focusedRow < last) {
                    focusedRow++
                    ensureScrollCoversSelection()
                    onSelectionChanged?.invoke(rows.getOrNull(focusedRow))
                }
                true
            }
            event.matches(keybindings.homeKeys, keybindings.homeChars) -> {
                focusedRow = 0
                scrollOffset = 0
                onSelectionChanged?.invoke(rows.getOrNull(focusedRow))
                true
            }
            event.matches(keybindings.endKeys, keybindings.endChars) -> {
                focusedRow = last
                scrollOffset = maxScroll(rows.size, visH)
                onSelectionChanged?.invoke(rows.getOrNull(focusedRow))
                true
            }
            event.matches(keybindings.pageUpKeys, keybindings.pageUpChars) -> {
                focusedRow = (focusedRow - visH / 2).coerceIn(0, last)
                ensureScrollCoversSelection()
                onSelectionChanged?.invoke(rows.getOrNull(focusedRow))
                true
            }
            event.matches(keybindings.pageDownKeys, keybindings.pageDownChars) -> {
                focusedRow = (focusedRow + visH / 2).coerceIn(0, last)
                ensureScrollCoversSelection()
                onSelectionChanged?.invoke(rows.getOrNull(focusedRow))
                true
            }
            event.matches(keybindings.selectKeys, keybindings.selectChars) && onEnter != null -> {
                rows.getOrNull(focusedRow)?.let { onEnter.invoke(it) }
                true
            }
            event.matches(keybindings.sortKeys, keybindings.sortChars) -> {
                cycleSort()
                true
            }
            else -> false
        }
    }
}
