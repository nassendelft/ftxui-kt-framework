package nl.ncaj.ftxui.framework

import kotlin.concurrent.Volatile
import nl.ncaj.ftxui.*

data class TableColumn<T>(
    val header: String,
    val extract: (T) -> String,
    val renderHeader: ((header: String, width: Int) -> Element)? = null,
    val renderCell: ((item: T, width: Int, focused: Boolean) -> Element)? = null,
)

data class TableState<T>(val rows: List<T>)

open class TableWindow<T>(
    private val columns: List<TableColumn<T>>,
    private val onEnter: ((T) -> Unit)? = null,
    override val extraHeader: WindowSection? = null,
    override val extraFooter: WindowSection? = null,
) : Window<TableState<T>> {

    @Volatile private var state: TableState<T> = TableState(emptyList())
    @Volatile private var focusedRow: Int = 0
    @Volatile private var scrollOffset: Int = 0
    @Volatile private var sortColumn: Int? = null
    @Volatile private var sortAscending: Boolean = true

    open override fun getVisibleHeight(): Int = Terminal.size().dimy

    fun updateState(newState: TableState<T>) {
        state = newState
        ensureValidFocus()
    }

    override fun render(state: TableState<T>): Component {
        this.state = state
        return renderer { buildElement() }
    }

    override fun onInput(event: FtxUIEvent): Boolean {
        return when {
            event.isKey(Key.ArrowUp)   || isChar(event, "k") -> { moveUp(); true }
            event.isKey(Key.ArrowDown) || isChar(event, "j") -> { moveDown(); true }
            isChar(event, "g") || event.isKey(Key.Home)      -> { focusFirst(); true }
            isChar(event, "G") || event.isKey(Key.End)       -> { focusLast(); true }
            event.isKey(Key.CtrlD) -> { repeat(pageSize() / 2) { moveDown() }; true }
            event.isKey(Key.CtrlU) -> { repeat(pageSize() / 2) { moveUp() }; true }
            event.isKey(Key.Return) && onEnter != null -> {
                sortedRows().getOrNull(focusedRow)?.let { onEnter.invoke(it) }
                true
            }
            isChar(event, "s") -> { cycleSort(); true }
            else -> false
        }
    }

    private fun buildElement(): Element {
        val rows = sortedRows()
        val visH = pageSize()
        clampScroll(rows.size, visH)

        val colWidths = columns.mapIndexed { i, col ->
            maxOf(col.header.length, rows.maxOfOrNull { col.extract(it).length } ?: 0)
        }

        val headerCells = columns.mapIndexed { i, col ->
            val indicator = when {
                sortColumn == i && sortAscending  -> " ▲"
                sortColumn == i && !sortAscending -> " ▼"
                else -> ""
            }
            val headerText = col.header + indicator
            col.renderHeader?.invoke(headerText, colWidths[i])
                ?: text(headerText.padEnd(colWidths[i] + 3)).bold()
        }
        val headerEl = hbox(*headerCells.toTypedArray())

        val slice = rows.drop(scrollOffset).take(visH)
        val dataRows = slice.mapIndexed { i, row ->
            val focused = scrollOffset + i == focusedRow
            val cells = columns.mapIndexed { ci, col ->
                col.renderCell?.invoke(row, colWidths[ci], focused)
                    ?: run {
                        val el = text(col.extract(row).padEnd(colWidths[ci] + 3))
                        if (focused) el.inverted() else el
                    }
            }
            hbox(*cells.toTypedArray())
        }

        val emptyRows = (0 until visH - slice.size).map { text("") }
        val dataEl = hbox(
            vbox(*dataRows.toTypedArray(), *emptyRows.toTypedArray()).flex(),
            vScrollBar(scrollOffset, rows.size, visH),
        )

        return wrapWithDecorations(vbox(headerEl, separator(), dataEl))
    }

    private fun sortedRows(): List<T> {
        val sc = sortColumn ?: return state.rows
        return if (sortAscending)
            state.rows.sortedBy { columns[sc].extract(it) }
        else
            state.rows.sortedByDescending { columns[sc].extract(it) }
    }

    private fun cycleSort() {
        val cur = sortColumn
        when {
            cur == null -> { sortColumn = 0; sortAscending = true }
            sortAscending -> sortAscending = false
            cur < columns.lastIndex -> { sortColumn = cur + 1; sortAscending = true }
            else -> sortColumn = null
        }
    }

    private fun moveUp() {
        if (focusedRow > 0) { focusedRow--; ensureScrollCoversSelection() }
    }

    private fun moveDown() {
        val last = sortedRows().lastIndex
        if (focusedRow < last) { focusedRow++; ensureScrollCoversSelection() }
    }

    private fun focusFirst() { focusedRow = 0; scrollOffset = 0 }

    private fun focusLast() {
        val rows = sortedRows()
        focusedRow = rows.lastIndex.coerceAtLeast(0)
        scrollOffset = maxScroll(rows.size, pageSize())
    }

    private fun ensureValidFocus() {
        val last = sortedRows().lastIndex.coerceAtLeast(0)
        focusedRow = focusedRow.coerceIn(0, last)
        clampScroll(sortedRows().size, pageSize())
    }

    private fun ensureScrollCoversSelection() {
        val visH = pageSize()
        if (focusedRow < scrollOffset) scrollOffset = focusedRow
        if (focusedRow >= scrollOffset + visH) scrollOffset = focusedRow - visH + 1
    }

    private fun clampScroll(total: Int, visH: Int) {
        scrollOffset = scrollOffset.coerceIn(0, maxScroll(total, visH))
    }

    private fun maxScroll(total: Int, visH: Int) = maxOf(0, total - visH)
    private fun pageSize(): Int = maxOf(1, contentHeight() - 2)
}
