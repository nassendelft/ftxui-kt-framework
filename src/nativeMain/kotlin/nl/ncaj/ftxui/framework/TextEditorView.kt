package nl.ncaj.ftxui.framework

import kotlin.concurrent.Volatile
import nl.ncaj.ftxui.*

data class TextEditorState(
    val content: String = "",
    val showLineNumbers: Boolean = true,
    val cursorLine: Int = 0,
    val cursorCol: Int = 0,
    val scrollOffset: Int = 0
)

open class TextEditorView(
    private val showLineNumbers: Boolean = true,
    val onContentChange: ((String) -> Unit)? = null,
    private val onStateChange: ((TextEditorState) -> Unit)? = null,
    private val keybindings: TextEditorKeybindings = TextEditorKeybindings(),
) : InputReceiver {

    @Volatile private var lines: MutableList<String> = mutableListOf("")
    @Volatile private var cursorLine: Int = 0
    @Volatile private var cursorCol: Int = 0
    @Volatile private var scrollOffset: Int = 0
    @Volatile private var suppressHistoryPush: Boolean = false

    private val history = UndoRedoStack<List<String>>(listOf(""))

    val canUndo: Boolean get() = history.canUndo
    val canRedo: Boolean get() = history.canRedo

    fun getText(): String = lines.joinToString("\n")

    fun setText(text: String) {
        lines = text.split("\n").toMutableList().ifEmpty { mutableListOf("") }
        cursorLine = 0
        cursorCol = 0
        scrollOffset = 0
        history.reset(lines.toList())
    }

    private fun contentHeight(): Int = Terminal.size().dimy - Screen.STATUS_BAR_HEIGHT

    fun render(state: TextEditorState): Component {
        if (lines.size == 1 && lines[0].isEmpty() && state.content.isNotEmpty()) {
            setText(state.content)
        }
        if (onStateChange != null) {
            cursorLine = state.cursorLine
            cursorCol = state.cursorCol
            scrollOffset = state.scrollOffset
        }
        return renderer { buildElement(state.showLineNumbers && showLineNumbers) }
    }

    override fun onInput(event: FtxUIEvent): Boolean {
        val oldLine = cursorLine
        val oldCol = cursorCol
        val oldScroll = scrollOffset
        val oldText = getText()

        val handled = when {
            event.matches(keybindings.undoKeys, keybindings.undoChars) -> { applyUndo(); true }
            event.matches(keybindings.redoKeys, keybindings.redoChars) -> { applyRedo(); true }
            event.isKey(Key.ArrowUp)    -> { moveCursorUp(1); true }
            event.isKey(Key.ArrowDown)  -> { moveCursorDown(1); true }
            event.isKey(Key.ArrowLeft)  -> { moveCursorLeft(); true }
            event.isKey(Key.ArrowRight) -> { moveCursorRight(); true }
            event.isKey(Key.Home)       -> { cursorCol = 0; true }
            event.isKey(Key.End)        -> { cursorCol = currentLine().length; true }
            event.isKey(Key.PageUp)     -> { moveCursorUp(pageSize()); true }
            event.isKey(Key.PageDown)   -> { moveCursorDown(pageSize()); true }
            event.isKey(Key.Return)     -> { insertNewline(); true }
            event.isKey(Key.Backspace)  -> { backspace(); true }
            event.isKey(Key.Delete)     -> { deleteForward(); true }
            event is FtxUIEvent.Character && !event.character.first().isISOControl() -> {
                insertChar(event.character)
                true
            }
            else -> false
        }

        if (handled) {
            if (cursorLine != oldLine || cursorCol != oldCol || scrollOffset != oldScroll || getText() != oldText) {
                notifyStateChange()
            }
        }
        return handled
    }

    private fun notifyStateChange() {
        val newState = TextEditorState(
            content = getText(),
            showLineNumbers = showLineNumbers,
            cursorLine = cursorLine,
            cursorCol = cursorCol,
            scrollOffset = scrollOffset
        )
        onStateChange?.invoke(newState)
    }

    // -----------------------------------------------------------------------
    // Editing operations
    // -----------------------------------------------------------------------

    private fun insertChar(char: String) {
        val col = cursorCol.coerceIn(0, currentLine().length)
        val line = currentLine()
        lines[cursorLine] = line.substring(0, col) + char + line.substring(col)
        cursorCol = col + char.length
        pushHistory()
        onContentChange?.invoke(getText())
    }

    private fun insertNewline() {
        val col = cursorCol.coerceIn(0, currentLine().length)
        val line = currentLine()
        lines[cursorLine] = line.substring(0, col)
        lines.add(cursorLine + 1, line.substring(col))
        cursorLine++
        cursorCol = 0
        ensureScrollCoversSelection()
        pushHistory()
        onContentChange?.invoke(getText())
    }

    private fun backspace() {
        val col = cursorCol.coerceIn(0, currentLine().length)
        if (col > 0) {
            val line = currentLine()
            lines[cursorLine] = line.substring(0, col - 1) + line.substring(col)
            cursorCol = col - 1
        } else if (cursorLine > 0) {
            val prevLen = lines[cursorLine - 1].length
            lines[cursorLine - 1] = lines[cursorLine - 1] + currentLine()
            lines.removeAt(cursorLine)
            cursorLine--
            cursorCol = prevLen
            ensureScrollCoversSelection()
        }
        pushHistory()
        onContentChange?.invoke(getText())
    }

    private fun deleteForward() {
        val col = cursorCol.coerceIn(0, currentLine().length)
        val line = currentLine()
        if (col < line.length) {
            lines[cursorLine] = line.substring(0, col) + line.substring(col + 1)
        } else if (cursorLine < lines.lastIndex) {
            lines[cursorLine] = line + lines[cursorLine + 1]
            lines.removeAt(cursorLine + 1)
        }
        pushHistory()
        onContentChange?.invoke(getText())
    }

    // -----------------------------------------------------------------------
    // Cursor movement
    // -----------------------------------------------------------------------

    private fun moveCursorUp(count: Int) {
        cursorLine = (cursorLine - count).coerceAtLeast(0)
        cursorCol = cursorCol.coerceAtMost(currentLine().length)
        ensureScrollCoversSelection()
    }

    private fun moveCursorDown(count: Int) {
        cursorLine = (cursorLine + count).coerceAtMost(lines.lastIndex)
        cursorCol = cursorCol.coerceAtMost(currentLine().length)
        ensureScrollCoversSelection()
    }

    private fun moveCursorLeft() {
        if (cursorCol > 0) {
            cursorCol--
        } else if (cursorLine > 0) {
            cursorLine--
            cursorCol = currentLine().length
            ensureScrollCoversSelection()
        }
    }

    private fun moveCursorRight() {
        if (cursorCol < currentLine().length) {
            cursorCol++
        } else if (cursorLine < lines.lastIndex) {
            cursorLine++
            cursorCol = 0
            ensureScrollCoversSelection()
        }
    }

    // -----------------------------------------------------------------------
    // Undo / redo
    // -----------------------------------------------------------------------

    private fun pushHistory() {
        if (!suppressHistoryPush) history.push(lines.toList())
    }

    private fun applyUndo() {
        if (!history.canUndo) return
        suppressHistoryPush = true
        val snapshot = history.undo()
        lines = snapshot.toMutableList().ifEmpty { mutableListOf("") }
        cursorLine = cursorLine.coerceAtMost(lines.lastIndex)
        cursorCol = cursorCol.coerceAtMost(currentLine().length)
        ensureScrollCoversSelection()
        suppressHistoryPush = false
        onContentChange?.invoke(getText())
    }

    private fun applyRedo() {
        if (!history.canRedo) return
        suppressHistoryPush = true
        val snapshot = history.redo()
        lines = snapshot.toMutableList().ifEmpty { mutableListOf("") }
        cursorLine = cursorLine.coerceAtMost(lines.lastIndex)
        cursorCol = cursorCol.coerceAtMost(currentLine().length)
        ensureScrollCoversSelection()
        suppressHistoryPush = false
        onContentChange?.invoke(getText())
    }

    // -----------------------------------------------------------------------
    // Rendering
    // -----------------------------------------------------------------------

    private fun buildElement(showNums: Boolean): Element {
        val visH = contentHeight()
        val lineNumWidth = if (showNums) lines.size.toString().length else 0
        ensureScrollCoversSelection()

        val slice = lines.drop(scrollOffset).take(visH)
        val rows = slice.mapIndexed { i, line ->
            val absLine = scrollOffset + i
            val lineEl = if (absLine == cursorLine) renderCursorLine(line) else text(line.ifEmpty { " " })
            if (showNums) {
                hbox(
                    text("${(absLine + 1).toString().padStart(lineNumWidth)} ").dim(),
                    lineEl.flex(),
                )
            } else {
                lineEl
            }
        }

        // Fill remaining height with empty rows
        val filled = rows + (rows.size until visH).map {
            if (showNums) hbox(text(" ".repeat(lineNumWidth + 1)).dim(), text(" ").flex())
            else text(" ")
        }

        return hbox(
            vbox(*filled.toTypedArray()).flex(),
            vScrollBar(scrollOffset, lines.size, visH),
        )
    }

    private fun renderCursorLine(line: String): Element {
        val col = cursorCol.coerceIn(0, line.length)
        val before = line.substring(0, col)
        val cursorChar = if (col < line.length) line[col].toString() else " "
        val after = if (col < line.length) line.substring(col + 1) else ""
        return hbox(
            if (before.isNotEmpty()) text(before) else emptyElement(),
            text(cursorChar).inverted(),
            if (after.isNotEmpty()) text(after) else emptyElement(),
        )
    }

    // -----------------------------------------------------------------------
    // Scroll
    // -----------------------------------------------------------------------

    private fun ensureScrollCoversSelection() {
        val visH = contentHeight()
        if (cursorLine < scrollOffset) scrollOffset = cursorLine
        if (cursorLine >= scrollOffset + visH) scrollOffset = cursorLine - visH + 1
        scrollOffset = scrollOffset.coerceIn(0, maxOf(0, lines.size - visH))
    }

    private fun pageSize(): Int = maxOf(1, contentHeight() - 2)

    private fun currentLine(): String = lines.getOrElse(cursorLine) { "" }
}
