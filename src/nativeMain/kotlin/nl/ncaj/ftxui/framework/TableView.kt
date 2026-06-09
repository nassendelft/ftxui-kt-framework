package nl.ncaj.ftxui.framework

import kotlin.concurrent.Volatile
import nl.ncaj.ftxui.*

data class TableColumn<T>(
    val header: String,
    val extract: (T) -> String,
    val renderHeader: ((header: String, width: Int) -> Element)? = null,
    val renderCell: ((item: T, width: Int, focused: Boolean) -> Element)? = null,
)

data class TableState<T>(
    val rows: List<T>,
    val focusedRow: Int = 0,
    val scrollOffset: Int = 0,
    val sortColumn: Int? = null,
    val sortAscending: Boolean = true
)

open class TableView<T>(
    private val columns: List<TableColumn<T>>,
    private val onEnter: ((T) -> Unit)? = null,
    private val onStateChange: ((TableState<T>) -> Unit)? = null,
    private val onSelectionChanged: ((focusedItem: T?) -> Unit)? = null,
    private val keybindings: TableKeybindings = TableKeybindings(),
) : InputReceiver {

    @Volatile private var state: TableState<T> = TableState(emptyList())
    @Volatile private var focusedRow: Int = 0
    @Volatile private var scrollOffset: Int = 0
    @Volatile private var sortColumn: Int? = null
    @Volatile private var sortAscending: Boolean = true

    fun updateState(newState: TableState<T>) {
        state = newState
        if (onStateChange != null) {
            focusedRow = newState.focusedRow
            scrollOffset = newState.scrollOffset
            sortColumn = newState.sortColumn
            sortAscending = newState.sortAscending
        }
        ensureValidFocus()
        ensureScrollCoversSelection()
    }

    fun render(state: TableState<T>): Component {
        this.state = state
        if (onStateChange != null) {
            this.focusedRow = state.focusedRow
            this.scrollOffset = state.scrollOffset
            this.sortColumn = state.sortColumn
            this.sortAscending = state.sortAscending
        }
        val prevRow = focusedRow
        ensureValidFocus()
        if (onStateChange != null && focusedRow != prevRow) {
            notifyStateChange()
        }
        return renderer { buildElement() }
    }

    override fun onInput(event: FtxUIEvent): Boolean {
        val oldRow = focusedRow
        val oldScroll = scrollOffset
        val oldSortCol = sortColumn
        val oldSortAsc = sortAscending

        val handled = when {
            event.matches(keybindings.moveUpKeys, keybindings.moveUpChars) -> { moveUp(); true }
            event.matches(keybindings.moveDownKeys, keybindings.moveDownChars) -> { moveDown(); true }
            event.matches(keybindings.homeKeys, keybindings.homeChars) -> { focusFirst(); true }
            event.matches(keybindings.endKeys, keybindings.endChars) -> { focusLast(); true }
            event.matches(keybindings.pageUpKeys, keybindings.pageUpChars) -> { repeat(pageSize() / 2) { moveUp() }; true }
            event.matches(keybindings.pageDownKeys, keybindings.pageDownChars) -> { repeat(pageSize() / 2) { moveDown() }; true }
            event.matches(keybindings.selectKeys, keybindings.selectChars) && onEnter != null -> {
                sortedRows().getOrNull(focusedRow)?.let { onEnter.invoke(it) }
                true
            }
            event.matches(keybindings.sortKeys, keybindings.sortChars) -> { cycleSort(); true }
            else -> false
        }

        if (handled) {
            if (focusedRow != oldRow || scrollOffset != oldScroll || sortColumn != oldSortCol || sortAscending != oldSortAsc) {
                notifyStateChange()
            }
        }
        return handled
    }

    private fun notifyStateChange() {
        val newState = TableState(
            rows = state.rows,
            focusedRow = focusedRow,
            scrollOffset = scrollOffset,
            sortColumn = sortColumn,
            sortAscending = sortAscending
        )
        onStateChange?.invoke(newState)
        onSelectionChanged?.invoke(sortedRows().getOrNull(focusedRow))
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

        return vbox(headerEl, separator(), dataEl)
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
    private fun pageSize(): Int = maxOf(1, Terminal.size().dimy - 2)
}
