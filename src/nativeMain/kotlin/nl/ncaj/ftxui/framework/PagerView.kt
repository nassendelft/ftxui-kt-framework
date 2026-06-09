package nl.ncaj.ftxui.framework

import kotlin.concurrent.Volatile
import nl.ncaj.ftxui.*

data class PagerState(
    val lines: List<String>,
    val showLineNumbers: Boolean = false,
    val scrollOffset: Int = 0,
    val searching: Boolean = false,
    val searchQuery: String = "",
    val matchLines: List<Int> = emptyList(),
    val matchIndex: Int = 0
)

open class PagerView(
    private val onStateChange: ((PagerState) -> Unit)? = null,
    private val keybindings: PagerKeybindings = PagerKeybindings(),
    private val style: PagerStyle = PagerStyle(),
) : InputReceiver {

    @Volatile private var state: PagerState = PagerState(emptyList())
    @Volatile private var scrollOffset: Int = 0
    @Volatile private var searching: Boolean = false
    @Volatile private var searchQuery: String = ""
    @Volatile private var matchLines: List<Int> = emptyList()
    @Volatile private var matchIndex: Int = 0

    fun updateState(newState: PagerState) {
        state = newState
        if (onStateChange != null) {
            scrollOffset = newState.scrollOffset
            searching = newState.searching
            searchQuery = newState.searchQuery
            matchLines = newState.matchLines
            matchIndex = newState.matchIndex
        } else {
            recomputeMatches()
        }
        clampScroll()
    }

    fun render(state: PagerState): Component {
        this.state = state
        if (onStateChange != null) {
            this.scrollOffset = state.scrollOffset
            this.searching = state.searching
            this.searchQuery = state.searchQuery
            this.matchLines = state.matchLines
            this.matchIndex = state.matchIndex
        }
        return renderer { buildElement() }
    }

    override fun onInput(event: FtxUIEvent): Boolean {
        val oldScroll = scrollOffset
        val oldSearching = searching
        val oldQuery = searchQuery
        val oldMatchIndex = matchIndex

        val handled = if (searching) handleSearchInput(event) else handleNormalInput(event)

        if (handled) {
            if (scrollOffset != oldScroll || searching != oldSearching || searchQuery != oldQuery || matchIndex != oldMatchIndex) {
                notifyStateChange()
            }
        }
        return handled
    }

    private fun notifyStateChange() {
        val newState = PagerState(
            lines = state.lines,
            showLineNumbers = state.showLineNumbers,
            scrollOffset = scrollOffset,
            searching = searching,
            searchQuery = searchQuery,
            matchLines = matchLines,
            matchIndex = matchIndex
        )
        onStateChange?.invoke(newState)
    }

    private fun handleNormalInput(event: FtxUIEvent): Boolean = when {
        event.matches(keybindings.scrollUpKeys, keybindings.scrollUpChars)   -> { scroll(-1); true }
        event.matches(keybindings.scrollDownKeys, keybindings.scrollDownChars) -> { scroll(+1); true }
        event.matches(keybindings.pageUpKeys, keybindings.pageUpChars) -> { scroll(-pageSize()); true }
        event.matches(keybindings.pageDownKeys, keybindings.pageDownChars) -> { scroll(+pageSize()); true }
        event.matches(keybindings.homeKeys, keybindings.homeChars)   -> { scrollOffset = 0; true }
        event.matches(keybindings.endKeys, keybindings.endChars)   -> { scrollOffset = maxScroll(); true }
        event.matches(keybindings.searchKeys, keybindings.searchChars) -> {
            searching = true
            searchQuery = ""
            matchLines = emptyList()
            matchIndex = 0
            true
        }
        event.matches(keybindings.nextMatchKeys, keybindings.nextMatchChars) && matchLines.isNotEmpty() -> { stepMatch(+1); true }
        event.matches(keybindings.prevMatchKeys, keybindings.prevMatchChars) && matchLines.isNotEmpty() -> { stepMatch(-1); true }
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
        val contentH = if (hasSearch) maxOf(1, getContentHeight() - 2) else getContentHeight()
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
                val lineNumEl = text("${(absLine + 1).toString().padStart(lineNumWidth)} ")
                    .let { t -> style.lineNumberColor?.let { c -> t.color(c) } ?: t.dim() }
                hbox(lineNumEl, lineEl)
            } else {
                lineEl
            }
        }

        val contentEl = hbox(
            vbox(*rows.toTypedArray()).flex(),
            vScrollBar(scrollOffset, lines.size, contentH, style.scrollThumb.or(Theme.current.scrollThumb)),
        )

        if (!hasSearch) return contentEl

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
        return vbox(contentEl, separator(), searchEl)
    }

    private fun buildHighlightedLine(line: String, query: String): Element {
        val idx = line.lowercase().indexOf(query.lowercase())
        if (idx < 0) return text(line)
        val before = line.substring(0, idx)
        val match  = line.substring(idx, idx + query.length)
        val after  = line.substring(idx + query.length)
        val highlightColor = style.searchHighlight.or(Theme.current.accent)
        return hbox(text(before), text(match).color(highlightColor).bold(), text(after))
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
        val h = getContentHeight()
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

    private fun getContentHeight(): Int = Terminal.size().dimy
    private fun pageSize(): Int = maxOf(1, getContentHeight() - 2)
    private fun maxScroll(): Int = maxOf(0, state.lines.size - getContentHeight())
}
