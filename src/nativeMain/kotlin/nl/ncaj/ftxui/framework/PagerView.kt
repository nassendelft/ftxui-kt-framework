package nl.ncaj.ftxui.framework

import nl.ncaj.ftxui.*

data class PagerState(
    val lines: List<String>,
    val showLineNumbers: Boolean = false
)

fun ScreenContext.pagerView(
    getState: () -> PagerState,
    keybindings: PagerKeybindings = PagerKeybindings(),
    style: PagerStyle = PagerStyle()
): Component {
    var scrollOffset by mutableStateOf(0)
    var searching by mutableStateOf(false)
    var searchQuery by mutableStateOf("")
    var matchLines by mutableStateOf(emptyList<Int>())
    var matchIndex by mutableStateOf(0)

    val getContentHeight: () -> Int = { Terminal.size().dimy }
    val maxScroll: () -> Int = { maxOf(0, getState().lines.size - getContentHeight()) }
    val pageSize: () -> Int = { maxOf(1, getContentHeight() - 2) }

    val scrollToMatch: (Int) -> Unit = { idx ->
        val line = matchLines.getOrNull(idx)
        if (line != null) {
            val h = getContentHeight()
            if (line < scrollOffset || line >= scrollOffset + h) {
                scrollOffset = (line - h / 2).coerceIn(0, maxScroll())
            }
        }
    }

    val recomputeMatches: () -> Unit = {
        val state = getState()
        if (searchQuery.isEmpty()) {
            matchLines = emptyList()
            matchIndex = 0
        } else {
            val q = searchQuery.lowercase()
            matchLines = state.lines.indices.filter { i -> state.lines[i].lowercase().contains(q) }
            matchIndex = 0
            if (matchLines.isNotEmpty()) scrollToMatch(0)
        }
    }

    val stepMatch: (Int) -> Unit = { dir ->
        if (matchLines.isNotEmpty()) {
            matchIndex = (matchIndex + dir + matchLines.size) % matchLines.size
            scrollToMatch(matchIndex)
        }
    }

    val buildHighlightedLine: (String, String) -> Element = { line, query ->
        val idx = line.lowercase().indexOf(query.lowercase())
        if (idx < 0) text(line)
        else {
            val before = line.substring(0, idx)
            val match  = line.substring(idx, idx + query.length)
            val after  = line.substring(idx + query.length)
            val highlightColor = style.searchHighlight.or(Theme.current.accent)
            hbox(text(before), text(match).color(highlightColor).bold(), text(after))
        }
    }

    val base = focusableRenderer { focused ->
        val state = getState()
        val lines = state.lines
        val hasSearch = searching || searchQuery.isNotEmpty()
        val contentH = if (hasSearch) maxOf(1, getContentHeight() - 2) else getContentHeight()
        scrollOffset = scrollOffset.coerceIn(0, maxScroll())

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

        if (!hasSearch) return@focusableRenderer contentEl

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
        vbox(contentEl, separator(), searchEl)
    }

    return base.catchEvent { event ->
        if (searching) {
            when {
                event.isKey(Key.Escape) || event.isKey(Key.Return) -> searching = false
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
            true
        } else {
            when {
                event.matches(keybindings.scrollUpKeys, keybindings.scrollUpChars)   -> { scrollOffset = (scrollOffset - 1).coerceIn(0, maxScroll()); true }
                event.matches(keybindings.scrollDownKeys, keybindings.scrollDownChars) -> { scrollOffset = (scrollOffset + 1).coerceIn(0, maxScroll()); true }
                event.matches(keybindings.pageUpKeys, keybindings.pageUpChars) -> { scrollOffset = (scrollOffset - pageSize()).coerceIn(0, maxScroll()); true }
                event.matches(keybindings.pageDownKeys, keybindings.pageDownChars) -> { scrollOffset = (scrollOffset + pageSize()).coerceIn(0, maxScroll()); true }
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
        }
    }
}
