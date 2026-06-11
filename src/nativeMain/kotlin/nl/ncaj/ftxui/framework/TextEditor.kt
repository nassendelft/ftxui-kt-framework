package nl.ncaj.ftxui.framework

import nl.ncaj.ftxui.*
import kotlin.reflect.KMutableProperty0

data class TextEditorState(
    val content: String = "",
    val showLineNumbers: Boolean = true,
    val cursorLine: Int = 0,
    val cursorCol: Int = 0,
    val scrollOffset: Int = 0
)

fun AppContext.textEditor(
    content: KMutableProperty0<String>,
    showLineNumbers: Boolean = true,
    onContentChange: ((String) -> Unit)? = null,
    onStateChange: ((TextEditorState) -> Unit)? = null,
    keybindings: TextEditorKeybindings = TextEditorKeybindings(),
    style: TextEditorStyle = TextEditorStyle()
): Component {
    var lines = content.get().split("\n").toMutableList().ifEmpty { mutableListOf("") }
    var cursorLine by mutableStateOf(0)
    var cursorCol by mutableStateOf(0)
    var scrollOffset by mutableStateOf(0)
    var suppressHistoryPush = false

    val history = UndoRedoStack<List<String>>(content.get().split("\n"))

    val getText: () -> String = { lines.joinToString("\n") }

    val notifyStateChange: () -> Unit = {
        onStateChange?.invoke(TextEditorState(
            content = getText(),
            showLineNumbers = showLineNumbers,
            cursorLine = cursorLine,
            cursorCol = cursorCol,
            scrollOffset = scrollOffset
        ))
    }

    val syncToProperty: () -> Unit = {
        val txt = getText()
        if (content.get() != txt) {
            content.set(txt)
            onContentChange?.invoke(txt)
        }
    }

    val pushHistory: () -> Unit = {
        if (!suppressHistoryPush) history.push(lines.toList())
    }

    val viewport = viewport()
    val contentHeight: () -> Int = { viewport.height }
    val pageSize: () -> Int = { maxOf(1, contentHeight() - 2) }
    val currentLine: () -> String = { lines.getOrElse(cursorLine) { "" } }

    val ensureScrollCoversSelection: () -> Unit = {
        val visH = contentHeight()
        if (cursorLine < scrollOffset) scrollOffset = cursorLine
        if (cursorLine >= scrollOffset + visH) scrollOffset = cursorLine - visH + 1
        scrollOffset = scrollOffset.coerceIn(0, maxOf(0, lines.size - visH))
    }

    val moveCursorUp: (Int) -> Unit = { count ->
        cursorLine = (cursorLine - count).coerceAtLeast(0)
        cursorCol = cursorCol.coerceAtMost(currentLine().length)
        ensureScrollCoversSelection()
    }

    val moveCursorDown: (Int) -> Unit = { count ->
        cursorLine = (cursorLine + count).coerceAtMost(lines.lastIndex)
        cursorCol = cursorCol.coerceAtMost(currentLine().length)
        ensureScrollCoversSelection()
    }

    val moveCursorLeft: () -> Unit = {
        if (cursorCol > 0) {
            cursorCol--
        } else if (cursorLine > 0) {
            cursorLine--
            cursorCol = currentLine().length
            ensureScrollCoversSelection()
        }
    }

    val moveCursorRight: () -> Unit = {
        if (cursorCol < currentLine().length) {
            cursorCol++
        } else if (cursorLine < lines.lastIndex) {
            cursorLine++
            cursorCol = 0
            ensureScrollCoversSelection()
        }
    }

    val insertChar: (String) -> Unit = { char ->
        val col = cursorCol.coerceIn(0, currentLine().length)
        val line = currentLine()
        lines[cursorLine] = line.substring(0, col) + char + line.substring(col)
        cursorCol = col + char.length
        pushHistory()
        syncToProperty()
    }

    val insertNewline: () -> Unit = {
        val col = cursorCol.coerceIn(0, currentLine().length)
        val line = currentLine()
        lines[cursorLine] = line.substring(0, col)
        lines.add(cursorLine + 1, line.substring(col))
        cursorLine++
        cursorCol = 0
        ensureScrollCoversSelection()
        pushHistory()
        syncToProperty()
    }

    val backspace: () -> Unit = {
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
        syncToProperty()
    }

    val deleteForward: () -> Unit = {
        val col = cursorCol.coerceIn(0, currentLine().length)
        val line = currentLine()
        if (col < line.length) {
            lines[cursorLine] = line.substring(0, col) + line.substring(col + 1)
        } else if (cursorLine < lines.lastIndex) {
            lines[cursorLine] = line + lines[cursorLine + 1]
            lines.removeAt(cursorLine + 1)
        }
        pushHistory()
        syncToProperty()
    }

    val applyUndo: () -> Unit = {
        if (history.canUndo) {
            suppressHistoryPush = true
            val snapshot = history.undo()
            lines = snapshot.toMutableList().ifEmpty { mutableListOf("") }
            cursorLine = cursorLine.coerceAtMost(lines.lastIndex)
            cursorCol = cursorCol.coerceAtMost(currentLine().length)
            ensureScrollCoversSelection()
            suppressHistoryPush = false
            syncToProperty()
        }
    }

    val applyRedo: () -> Unit = {
        if (history.canRedo) {
            suppressHistoryPush = true
            val snapshot = history.redo()
            lines = snapshot.toMutableList().ifEmpty { mutableListOf("") }
            cursorLine = cursorLine.coerceAtMost(lines.lastIndex)
            cursorCol = cursorCol.coerceAtMost(currentLine().length)
            ensureScrollCoversSelection()
            suppressHistoryPush = false
            syncToProperty()
        }
    }

    val renderCursorLine: (String) -> Element = { line ->
        val col = cursorCol.coerceIn(0, line.length)
        val before = line.substring(0, col)
        val cursorChar = if (col < line.length) line[col].toString() else " "
        val after = if (col < line.length) line.substring(col + 1) else ""
        val cursorEl = text(cursorChar)
        val styledCursor = run {
            val fg = style.cursorForeground
            val bg = style.cursorBackground
            when {
                fg != null && bg != null -> cursorEl.color(fg).bgcolor(bg)
                fg != null -> cursorEl.color(fg)
                bg != null -> cursorEl.bgcolor(bg)
                else -> cursorEl.inverted()
            }
        }
        hbox(
            if (before.isNotEmpty()) text(before) else emptyElement(),
            styledCursor,
            if (after.isNotEmpty()) text(after) else emptyElement(),
        )
    }

    val base = focusableRenderer { focused ->
        // Sync external property if it changed from the outside
        val currentContent = content.get()
        if (currentContent != getText()) {
            lines = currentContent.split("\n").toMutableList().ifEmpty { mutableListOf("") }
            cursorLine = cursorLine.coerceAtMost(lines.lastIndex)
            cursorCol = cursorCol.coerceAtMost(currentLine().length)
        }

        val visH = contentHeight()
        val lineNumWidth = if (showLineNumbers) lines.size.toString().length else 0
        ensureScrollCoversSelection()

        val slice = lines.drop(scrollOffset).take(visH)
        val rows = slice.mapIndexed { i, line ->
            val absLine = scrollOffset + i
            val lineEl = if (absLine == cursorLine && focused) renderCursorLine(line) else text(line.ifEmpty { " " })
            if (showLineNumbers) {
                val numText = text("${(absLine + 1).toString().padStart(lineNumWidth)} ")
                val styledNum = style.lineNumbersColor?.let { numText.color(it) } ?: numText.dim()
                hbox(styledNum, lineEl.flex())
            } else {
                lineEl
            }
        }

        val filled = rows + (rows.size until visH).map {
            if (showLineNumbers) {
                val fillerText = text(" ".repeat(lineNumWidth + 1))
                val styledFiller = style.lineNumbersColor?.let { fillerText.color(it) } ?: fillerText.dim()
                hbox(styledFiller, text(" ").flex())
            } else text(" ")
        }

        viewport.measure(hbox(
            vbox(*filled.toTypedArray()).flex(),
            vScrollBar(scrollOffset, lines.size, visH, style.scrollThumb.or(Theme.current.scrollThumb)),
        ))
    }

    return base.catchEvent { event ->
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
        handled
    }
}
