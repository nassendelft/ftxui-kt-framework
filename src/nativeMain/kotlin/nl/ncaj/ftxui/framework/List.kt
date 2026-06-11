package nl.ncaj.ftxui.framework

import nl.ncaj.ftxui.*
import kotlin.reflect.KProperty

sealed class ListEntry<out T> {
    class Header<out T>(val data: T) : ListEntry<T>()
    class Item<out T>(val data: T, val onEnter: (() -> Unit)? = null) : ListEntry<T>()
}

fun <T> AppContext.list(
    getEntries: () -> List<ListEntry<T>>,
    renderItem: (data: T, focused: Boolean) -> Element,
    renderHeader: (data: T) -> Element,
    toSearchString: (T) -> String = { it.toString() },
    style: ListStyle = ListStyle(),
    keybindings: ListKeybindings = ListKeybindings(),
    onFocusChange: ((Int) -> Unit)? = null,
    focusedIndexState: IntState? = null,
    stateId: () -> Any? = { null },
): Component {
    var localFocusedIndex = -1
    val focusedIndexDelegate = object {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Int {
            return focusedIndexState?.value ?: localFocusedIndex
        }
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            if (focusedIndexState != null) {
                if (focusedIndexState.value != value) {
                    focusedIndexState.value = value
                    onFocusChange?.invoke(value)
                    requestRedraw()
                }
            } else {
                if (localFocusedIndex != value) {
                    localFocusedIndex = value
                    onFocusChange?.invoke(value)
                    requestRedraw()
                }
            }
        }
    }
    var focusedIndex by focusedIndexDelegate
    var scrollOffset by mutableStateOf(0)
    var searchQuery by mutableStateOf("")
    var searchActive by mutableStateOf(false)
    var searchCursor by mutableStateOf(0)
    val viewport = viewport()

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
        val visH = viewport.height
        val entries = getEntries()
        if (focusedIndex >= 0 && entries.isNotEmpty()) {
            if (focusedIndex < scrollOffset) scrollOffset = focusedIndex
            if (focusedIndex >= scrollOffset + visH) scrollOffset = focusedIndex - visH + 1
            scrollOffset = scrollOffset.coerceIn(0, maxOf(0, entries.size - visH))
        }
    }

    var lastStateId: Any? = null

    val base = focusableRenderer { focused ->
        val entries = getEntries()
        val currentId = stateId()
        if (currentId != lastStateId) {
            lastStateId = currentId
            if (focusedIndexState == null) {
                focusedIndex = -1
                scrollOffset = 0
            }
        }
        ensureValidFocus()
        ensureScrollCoversSelection()

        if (entries.isEmpty()) return@focusableRenderer emptyElement()

        val listH = viewport.height

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

        val listRow = viewport.measure(hbox(
            vbox(*items.toTypedArray()).flex(),
            vScrollBar(scrollOffset, entries.size, listH, style.scrollThumb ?: Theme.current.scrollThumb),
        ))

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
                event.matches(keybindings.moveUpKeys, keybindings.moveUpChars) -> {
                    var i = focusedIndex - 1
                    while (i >= 0) {
                        if (entries[i] is ListEntry.Item) { focusedIndex = i; break }
                        i--
                    }
                    true
                }
                event.matches(keybindings.moveDownKeys, keybindings.moveDownChars) -> {
                    var i = focusedIndex + 1
                    while (i < entries.size) {
                        if (entries[i] is ListEntry.Item) { focusedIndex = i; break }
                        i++
                    }
                    true
                }
                event.matches(keybindings.pageUpKeys, keybindings.pageUpChars) -> {
                    val count = viewport.height
                    if (focusedIndex >= 0 && entries.isNotEmpty()) {
                        val relOffset = focusedIndex - scrollOffset
                        val targetScroll = (scrollOffset - count).coerceIn(0, maxOf(0, entries.size - count))
                        var newFocused = if (targetScroll == scrollOffset) {
                            entries.indexOfFirst { it is ListEntry.Item }.let { if (it < 0) 0 else it }
                        } else {
                            (targetScroll + relOffset).coerceIn(0, entries.lastIndex)
                        }
                        if (entries[newFocused] is ListEntry.Header) {
                            val nextItem = (newFocused..entries.lastIndex).firstOrNull { entries[it] is ListEntry.Item }
                            val prevItem = (newFocused downTo 0).firstOrNull { entries[it] is ListEntry.Item }
                            newFocused = when {
                                nextItem != null && prevItem != null -> {
                                    if (nextItem - newFocused < newFocused - prevItem) nextItem else prevItem
                                }
                                nextItem != null -> nextItem
                                prevItem != null -> prevItem
                                else -> newFocused
                            }
                        }
                        focusedIndex = newFocused
                        scrollOffset = targetScroll
                    }
                    true
                }
                event.matches(keybindings.pageDownKeys, keybindings.pageDownChars) -> {
                    val count = viewport.height
                    if (focusedIndex >= 0 && entries.isNotEmpty()) {
                        val relOffset = focusedIndex - scrollOffset
                        val targetScroll = (scrollOffset + count).coerceIn(0, maxOf(0, entries.size - count))
                        var newFocused = if (targetScroll == scrollOffset) {
                            entries.lastIndex
                        } else {
                            (targetScroll + relOffset).coerceIn(0, entries.lastIndex)
                        }
                        if (entries[newFocused] is ListEntry.Header) {
                            val nextItem = (newFocused..entries.lastIndex).firstOrNull { entries[it] is ListEntry.Item }
                            val prevItem = (newFocused downTo 0).firstOrNull { entries[it] is ListEntry.Item }
                            newFocused = when {
                                nextItem != null && prevItem != null -> {
                                    if (nextItem - newFocused < newFocused - prevItem) nextItem else prevItem
                                }
                                nextItem != null -> nextItem
                                prevItem != null -> prevItem
                                else -> newFocused
                            }
                        }
                        focusedIndex = newFocused
                        scrollOffset = targetScroll
                    }
                    true
                }
                event.matches(keybindings.homeKeys, keybindings.homeChars) -> {
                    val firstItemIdx = entries.indexOfFirst { it is ListEntry.Item }
                    if (firstItemIdx >= 0) {
                        focusedIndex = firstItemIdx
                    }
                    true
                }
                event.matches(keybindings.endKeys, keybindings.endChars) -> {
                    val lastItemIdx = entries.indexOfLast { it is ListEntry.Item }
                    if (lastItemIdx >= 0) {
                        focusedIndex = lastItemIdx
                    }
                    true
                }
                event.matches(keybindings.selectKeys, keybindings.selectChars) -> {
                    (entries.getOrNull(focusedIndex) as? ListEntry.Item<T>)?.onEnter?.invoke()
                    true
                }
                event.matches(keybindings.searchKeys, keybindings.searchChars) -> {
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
