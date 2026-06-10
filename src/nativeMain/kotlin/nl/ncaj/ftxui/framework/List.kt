package nl.ncaj.ftxui.framework

import nl.ncaj.ftxui.*

sealed class ListEntry<out T> {
    class Header<out T>(val data: T) : ListEntry<T>()
    class Item<out T>(val data: T, val onEnter: (() -> Unit)? = null) : ListEntry<T>()
}

fun <T> ScreenContext.list(
    getEntries: () -> List<ListEntry<T>>,
    renderItem: (data: T, focused: Boolean) -> Element,
    renderHeader: (data: T) -> Element,
    toSearchString: (T) -> String = { it.toString() },
    style: ListStyle = ListStyle(),
    onFocusChange: ((Int) -> Unit)? = null,
): Component {
    var focusedIndex by MutableState(-1) { value ->
        onFocusChange?.invoke(value)
        requestRedraw()
    }
    var scrollOffset by mutableStateOf(0)
    var searchQuery by mutableStateOf("")
    var searchActive by mutableStateOf(false)
    var searchCursor by mutableStateOf(0)
    var lastListH by mutableStateOf(20)

    val getFilteredIndices: () -> List<Int> = {
        val q = searchQuery.lowercase()
        val entries = getEntries()
        if (q.isEmpty()) emptyList()
        else entries.indices.filter { i ->
            val e = entries[i]
            @Suppress("UNCHECKED_CAST")
            e is ListEntry.Item<*> && toSearchString(e.data as T).lowercase().contains(q)
        }
    }

    val ensureValidFocus: () -> Unit = {
        val entries = getEntries()
        if (entries.isNotEmpty()) {
            if (focusedIndex < 0 || focusedIndex >= entries.size || entries[focusedIndex] is ListEntry.Header<*>) {
                focusedIndex = entries.indexOfFirst { it is ListEntry.Item }.let { if (it < 0) -1 else it }
            }
        }
    }

    val ensureScrollCoversSelection: () -> Unit = {
        val visH = lastListH
        val entries = getEntries()
        if (focusedIndex >= 0 && entries.isNotEmpty()) {
            if (focusedIndex < scrollOffset) scrollOffset = focusedIndex
            if (focusedIndex >= scrollOffset + visH) scrollOffset = focusedIndex - visH + 1
            scrollOffset = scrollOffset.coerceIn(0, maxOf(0, entries.size - visH))
        }
    }

    val base = focusableRenderer { focused ->
        val entries = getEntries()
        ensureValidFocus()
        ensureScrollCoversSelection()

        if (entries.isEmpty()) return@focusableRenderer emptyElement()

        val totalH = Terminal.size().dimy
        val listH = if (searchActive) maxOf(1, totalH - 2) else totalH
        lastListH = listH

        val start = scrollOffset.coerceIn(0, entries.size)
        val end = (scrollOffset + listH).coerceIn(0, entries.size)
        val items = entries.subList(start, end).mapIndexed { i, entry ->
            val absoluteIndex = start + i
            when (entry) {
                is ListEntry.Header -> {
                    val el = renderHeader(entry.data)
                    style.headerForeground?.let { el.color(it) } ?: el
                }
                is ListEntry.Item -> {
                    val isFocused = absoluteIndex == focusedIndex
                    val el = renderItem(entry.data, isFocused)
                    if (focused && isFocused) {
                        val fg = style.focusedItemForeground
                        val bg = style.focusedItemBackground
                        when {
                            fg != null && bg != null -> el.color(fg).bgcolor(bg)
                            fg != null -> el.color(fg)
                            bg != null -> el.bgcolor(bg)
                            else -> el
                        }
                    } else el
                }
            }
        }

        val listRow = hbox(
            vbox(*items.toTypedArray()).flex(),
            vScrollBar(scrollOffset, entries.size, listH, style.scrollThumb ?: Theme.current.scrollThumb),
        )

        if (searchActive) {
            vbox(
                hbox(text("/ $searchQuery█").color(Theme.current.accent)),
                separator(),
                listRow
            )
        } else {
            listRow
        }
    }

    return base.catchEvent { event ->
        val entries = getEntries()
        val filtered = getFilteredIndices()

        if (searchActive) {
            when {
                event.isKey(Key.Escape) -> {
                    searchActive = false
                    searchQuery = ""
                    true
                }
                event.isKey(Key.Return) -> {
                    searchActive = false
                    val activeIdx = filtered.getOrNull(searchCursor) ?: -1
                    if (activeIdx >= 0) {
                        focusedIndex = activeIdx
                        (entries.getOrNull(focusedIndex) as? ListEntry.Item<T>)?.onEnter?.invoke()
                    }
                    true
                }
                event.isKey(Key.ArrowUp) -> {
                    if (filtered.isNotEmpty()) {
                        searchCursor = (searchCursor - 1).coerceAtLeast(0)
                        focusedIndex = filtered[searchCursor]
                    }
                    true
                }
                event.isKey(Key.ArrowDown) -> {
                    if (filtered.isNotEmpty()) {
                        searchCursor = (searchCursor + 1).coerceAtMost(filtered.lastIndex)
                        focusedIndex = filtered[searchCursor]
                    }
                    true
                }
                event.isKey(Key.Backspace) -> {
                    if (searchQuery.isNotEmpty()) {
                        searchQuery = searchQuery.dropLast(1)
                        val newFiltered = getFilteredIndices()
                        searchCursor = 0
                        newFiltered.firstOrNull()?.let { focusedIndex = it }
                    } else {
                        searchActive = false
                    }
                    true
                }
                event is FtxUIEvent.Character -> {
                    searchQuery += event.character
                    val newFiltered = getFilteredIndices()
                    searchCursor = 0
                    newFiltered.firstOrNull()?.let { focusedIndex = it }
                    true
                }
                else -> false
            }
        } else {
            when {
                event.isKey(Key.ArrowUp) || event.isKey("k") -> {
                    var i = focusedIndex - 1
                    while (i >= 0) {
                        if (entries[i] is ListEntry.Item) { focusedIndex = i; break }
                        i--
                    }
                    true
                }
                event.isKey(Key.ArrowDown) || event.isKey("j") -> {
                    var i = focusedIndex + 1
                    while (i < entries.size) {
                        if (entries[i] is ListEntry.Item) { focusedIndex = i; break }
                        i++
                    }
                    true
                }
                event.isKey(Key.Return) -> {
                    (entries.getOrNull(focusedIndex) as? ListEntry.Item<T>)?.onEnter?.invoke()
                    true
                }
                event.isKey("/") -> {
                    searchActive = true
                    searchQuery = ""
                    searchCursor = 0
                    true
                }
                else -> false
            }
        }
    }
}
