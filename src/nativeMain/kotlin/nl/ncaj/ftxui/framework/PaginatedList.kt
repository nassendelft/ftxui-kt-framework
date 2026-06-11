package nl.ncaj.ftxui.framework

import kotlinx.coroutines.*
import nl.ncaj.ftxui.*

fun <T> AppContext.paginatedList(
    pageSize: Int = 50,
    loadThreshold: Int = 10,
    loadPage: suspend (offset: Int, limit: Int) -> List<ListEntry<T>>,
    renderItem: (data: T, focused: Boolean) -> Element,
    renderHeader: (data: T) -> Element,
    toSearchString: (T) -> String = { it.toString() },
    onSelect: ((ListEntry.Item<T>) -> Unit)? = null,
    onTotalCount: (() -> Int?)? = null,
    keybindings: ListKeybindings = ListKeybindings(),
    style: ListStyle = ListStyle()
): Component {
    var items by mutableStateOf(emptyList<ListEntry<T>>())
    var isLoadingMore by mutableStateOf(false)
    var hasMore by mutableStateOf(true)
    var totalCount by mutableStateOf(null as Int?)
    
    var focusedIndex by mutableStateOf(-1)
    var scrollOffset by mutableStateOf(0)
    val viewport = viewport()

    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    var loadJob: Job? = null

    val loadNextPage: () -> Unit = {
        if (!isLoadingMore && hasMore) {
            isLoadingMore = true
            requestRedraw()
            loadJob?.cancel()
            loadJob = scope.launch {
                val offset = items.count { it is ListEntry.Item }
                val page = try { loadPage(offset, pageSize) } catch (e: Exception) { emptyList() }
                isLoadingMore = false
                if (page.isEmpty()) {
                    hasMore = false
                } else {
                    items = items + page
                    if (focusedIndex < 0) {
                        focusedIndex = items.indexOfFirst { it is ListEntry.Item }.let { if (it < 0) -1 else it }
                    }
                }
                requestRedraw()
            }
        }
    }

    val ensureValidFocus: () -> Unit = {
        if (items.isNotEmpty()) {
            if (focusedIndex < 0 || focusedIndex >= items.size || items[focusedIndex] is ListEntry.Header<*>) {
                focusedIndex = items.indexOfFirst { it is ListEntry.Item }.let { if (it < 0) -1 else it }
            }
        }
    }

    val ensureScrollCoversSelection: () -> Unit = {
        val visH = viewport.height
        if (focusedIndex >= 0 && items.isNotEmpty()) {
            if (focusedIndex < scrollOffset) scrollOffset = focusedIndex
            if (focusedIndex >= scrollOffset + visH) scrollOffset = focusedIndex - visH + 1
            scrollOffset = scrollOffset.coerceIn(0, maxOf(0, items.size - visH))
        }
    }

    // Trigger initial page load
    loadNextPage()

    val base = focusableRenderer { focused ->
        ensureValidFocus()
        ensureScrollCoversSelection()

        val total = totalCount ?: onTotalCount?.invoke()
        val listH = viewport.height

        val start = scrollOffset.coerceIn(0, items.size)
        val end = (scrollOffset + listH).coerceIn(0, items.size)
        val slice = items.subList(start, end)

        val rows = slice.mapIndexed { i, entry ->
            when (entry) {
                is ListEntry.Header -> {
                    val el = renderHeader(entry.data)
                    style.headerForeground?.let { el.color(it) } ?: el
                }
                is ListEntry.Item   -> {
                    val isFocused = (start + i) == focusedIndex
                    val el = renderItem(entry.data, isFocused)
                    if (isFocused && focused) {
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

        val loadingRow: List<Element> = if (isLoadingMore) {
            listOf(hbox(filler(), text("loading…").dim(), filler()))
        } else emptyList()

        val showing = items.count { it is ListEntry.Item }
        val statusRow = if (total != null) hbox(text("  "), text("Showing $showing of $total").dim(), filler()) else null
        val allRows = rows + loadingRow
        val listEl = viewport.measure(hbox(
            vbox(*allRows.toTypedArray()).flex(),
            vScrollBar(scrollOffset, items.size + (if (isLoadingMore) 1 else 0), listH, style.scrollThumb.or(Theme.current.scrollThumb)),
        ))

        val bottomEl = if (statusRow != null) vbox(listEl, separator(), statusRow) else listEl

        if (!isLoadingMore && hasMore && focusedIndex >= items.size - loadThreshold) {
            loadNextPage()
        }

        bottomEl
    }

    return base.catchEvent { event ->
        when {
            event.matches(keybindings.moveUpKeys, keybindings.moveUpChars) -> {
                var i = focusedIndex - 1
                while (i >= 0) {
                    if (items[i] is ListEntry.Item) { focusedIndex = i; break }
                    i--
                }
                true
            }
            event.matches(keybindings.moveDownKeys, keybindings.moveDownChars) -> {
                var i = focusedIndex + 1
                while (i < items.size) {
                    if (items[i] is ListEntry.Item) { focusedIndex = i; break }
                    i++
                }
                if (!isLoadingMore && hasMore && focusedIndex >= items.size - loadThreshold) loadNextPage()
                true
            }
            event.matches(keybindings.selectKeys, keybindings.selectChars) -> {
                val item = items.getOrNull(focusedIndex) as? ListEntry.Item<T>
                if (item != null) {
                    onSelect?.invoke(item)
                    true
                } else false
            }
            else -> false
        }
    }
}
