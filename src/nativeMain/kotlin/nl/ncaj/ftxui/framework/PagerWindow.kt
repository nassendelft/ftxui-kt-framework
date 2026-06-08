package nl.ncaj.ftxui.framework

import kotlin.concurrent.Volatile
import nl.ncaj.ftxui.*

data class PagerState(
    val lines: List<String>,
    val showLineNumbers: Boolean = false,
)

open class PagerWindow(
    override val extraHeader: WindowSection? = null,
    override val extraFooter: WindowSection? = null,
) : Window<PagerState> {

    @Volatile private var state: PagerState = PagerState(emptyList())
    @Volatile private var scrollOffset: Int = 0
    @Volatile private var searching: Boolean = false
    @Volatile private var searchQuery: String = ""
    @Volatile private var matchLines: List<Int> = emptyList()
    @Volatile private var matchIndex: Int = 0

    open override fun getVisibleHeight(): Int = Terminal.size().dimy

    fun updateState(newState: PagerState) {
        state = newState
        recomputeMatches()
        clampScroll()
    }

    override fun render(state: PagerState): Component {
        this.state = state
        return renderer { buildElement() }
    }

    override fun onInput(event: FtxUIEvent): Boolean {
        if (searching) return handleSearchInput(event)
        return handleNormalInput(event)
    }

    private fun handleNormalInput(event: FtxUIEvent): Boolean = when {
        event.isKey(Key.ArrowUp)   || isChar(event, "k")   -> { scroll(-1); true }
        event.isKey(Key.ArrowDown) || isChar(event, "j")   -> { scroll(+1); true }
        event.isKey(Key.PageUp)    || event.isKey(Key.CtrlU) -> { scroll(-pageSize()); true }
        event.isKey(Key.PageDown)  || event.isKey(Key.CtrlD) -> { scroll(+pageSize()); true }
        event.isKey(Key.Home)      || isChar(event, "g")   -> { scrollOffset = 0; true }
        event.isKey(Key.End)       || isChar(event, "G")   -> { scrollOffset = maxScroll(); true }
        isChar(event, "/") -> {
            searching = true
            searchQuery = ""
            matchLines = emptyList()
            matchIndex = 0
            true
        }
        isChar(event, "n") && matchLines.isNotEmpty() -> { stepMatch(+1); true }
        isChar(event, "N") && matchLines.isNotEmpty() -> { stepMatch(-1); true }
        else -> false
    }

    private fun handleSearchInput(event: FtxUIEvent): Boolean {
        when {
            event.isKey(Key.Escape) -> searching = false
            event.isKey(Key.Return) -> searching = false
            event.isKey(Key.Backspace) -> {
                if (searchQuery.isNotEmpty()) {
                    searchQuery = searchQuery.dropLast(1)
                    recomputeMatches()
                } else {
                    searching = false
                }
            }
            event is FtxUIEvent.Character -> {
                searchQuery += event.character
                recomputeMatches()
            }
        }
        return true
    }

    private fun buildElement(): Element {
        val lines = state.lines
        val hasSearch = searching || searchQuery.isNotEmpty()
        val contentH = if (hasSearch) maxOf(1, contentHeight() - 2) else contentHeight()
        clampScroll()

        val lineNumWidth = if (state.showLineNumbers) lines.size.toString().length else 0
        val slice = lines.drop(scrollOffset).take(contentH)

        val rows = slice.mapIndexed { i, line ->
            val absLine = scrollOffset + i
            val lineEl = if (searchQuery.isNotEmpty() && absLine in matchLines)
                buildHighlightedLine(line, searchQuery)
            else
                text(line)

            if (state.showLineNumbers) {
                hbox(text("${(absLine + 1).toString().padStart(lineNumWidth)} ").dim(), lineEl)
            } else {
                lineEl
            }
        }

        val contentEl = hbox(
            vbox(*rows.toTypedArray()).flex(),
            vScrollBar(scrollOffset, lines.size, contentH),
        )

        if (!hasSearch) return wrapWithDecorations(contentEl)

        val matchStatus = when {
            searchQuery.isEmpty()    -> ""
            matchLines.isEmpty()     -> "  [no matches]"
            else                     -> "  ${matchIndex + 1}/${matchLines.size}"
        }
        val queryText = if (searching) "/ $searchQuery█" else "/ $searchQuery"
        val searchEl = hbox(
            if (searching) text(queryText) else text(queryText).dim(),
            text(matchStatus).dim(),
        )
        return wrapWithDecorations(vbox(contentEl, separator(), searchEl))
    }

    private fun buildHighlightedLine(line: String, query: String): Element {
        val idx = line.lowercase().indexOf(query.lowercase())
        if (idx < 0) return text(line)
        val before = line.substring(0, idx)
        val match  = line.substring(idx, idx + query.length)
        val after  = line.substring(idx + query.length)
        return hbox(text(before), text(match).color(Theme.current.accent).bold(), text(after))
    }

    private fun recomputeMatches() {
        if (searchQuery.isEmpty()) {
            matchLines = emptyList()
            matchIndex = 0
            return
        }
        val q = searchQuery.lowercase()
        matchLines = state.lines.indices.filter { i -> state.lines[i].lowercase().contains(q) }
        matchIndex = 0
        if (matchLines.isNotEmpty()) scrollToMatch(0)
    }

    private fun stepMatch(dir: Int) {
        if (matchLines.isEmpty()) return
        matchIndex = (matchIndex + dir + matchLines.size) % matchLines.size
        scrollToMatch(matchIndex)
    }

    private fun scrollToMatch(idx: Int) {
        val line = matchLines.getOrNull(idx) ?: return
        val h = contentHeight()
        if (line < scrollOffset || line >= scrollOffset + h) {
            scrollOffset = (line - h / 2).coerceIn(0, maxScroll())
        }
    }

    private fun scroll(delta: Int) {
        scrollOffset = (scrollOffset + delta).coerceIn(0, maxScroll())
    }

    private fun clampScroll() {
        scrollOffset = scrollOffset.coerceIn(0, maxScroll())
    }

    private fun pageSize(): Int = maxOf(1, contentHeight() - 2)
    private fun maxScroll(): Int = maxOf(0, state.lines.size - contentHeight())
}
