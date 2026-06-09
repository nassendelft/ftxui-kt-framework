package nl.ncaj.ftxui.framework

import kotlin.concurrent.Volatile
import nl.ncaj.ftxui.*

// ---------------------------------------------------------------------------
// Entry model
// ---------------------------------------------------------------------------

sealed class ListEntry<out T> {
    class Header<out T>(val data: T) : ListEntry<T>()
    class Item<out T>(val data: T, val onEnter: (() -> Unit)? = null) : ListEntry<T>()
}

// ---------------------------------------------------------------------------
// State model
// ---------------------------------------------------------------------------

data class ListState<T>(
    val entries: List<ListEntry<T>>,
    val focusedIndex: Int = -1,
    val scrollOffset: Int = 0,
    val searchActive: Boolean = false,
    val searchQuery: String = ""
)

// ---------------------------------------------------------------------------
// SearchWindow — sub-view that owns all search interaction
// ---------------------------------------------------------------------------

internal class SearchWindow<T>(
    private val getEntries: () -> List<ListEntry<T>>,
    private val toSearchString: (T) -> String,
    private val onResultSelected: (entryIndex: Int) -> Unit,
    private val onConfirm: (entryIndex: Int) -> Unit,
    private val onDismiss: () -> Unit,
) : InputReceiver {

    @Volatile var query: String = ""
        private set
    @Volatile private var resultIndices: List<Int> = emptyList()
    @Volatile private var cursor: Int = 0

    fun render(): Component = renderer { hbox(text("/ $query█")) }

    override fun onInput(event: FtxUIEvent): Boolean {
        when {
            event.isKey(Key.Escape) -> onDismiss()
            event.isKey(Key.Return) -> onConfirm(resultIndices.getOrElse(cursor) { -1 })
            event.isKey(Key.ArrowUp) -> {
                cursor = (cursor - 1).coerceAtLeast(0)
                resultIndices.getOrNull(cursor)?.let { onResultSelected(it) }
            }
            event.isKey(Key.ArrowDown) -> {
                cursor = (cursor + 1).coerceAtMost(maxOf(0, resultIndices.lastIndex))
                resultIndices.getOrNull(cursor)?.let { onResultSelected(it) }
            }
            event.isKey(Key.Backspace) -> {
                if (query.isNotEmpty()) {
                    query = query.dropLast(1)
                    applyQuery()
                } else {
                    onDismiss()
                }
            }
            event is FtxUIEvent.Character -> {
                query += event.character
                applyQuery()
            }
        }
        return true  // consume everything in search mode
    }

    private fun applyQuery() {
        resultIndices = computeResults(query)
        cursor = 0
        resultIndices.getOrNull(0)?.let { onResultSelected(it) }
    }

    private fun computeResults(query: String): List<Int> {
        if (query.isEmpty()) return emptyList()
        val entries = getEntries()
        return entries.indices.filter { i ->
            val e = entries[i]
            e is ListEntry.Item<*> && fuzzyMatch(query, toSearchString(@Suppress("UNCHECKED_CAST") (e as ListEntry.Item<T>).data))
        }
    }
}

// ---------------------------------------------------------------------------
// BaseListWindow — shared navigation, scroll, and search for list windows
// ---------------------------------------------------------------------------

abstract class BaseListWindow<T>(
    private val jumpSize: Int,
    private val toSearchString: (T) -> String,
    protected val keybindings: ListKeybindings = ListKeybindings(),
) {
    @Volatile protected var focusedIndex: Int = -1
    @Volatile protected var scrollOffset: Int = 0
    @Volatile protected var lastListH: Int = 20
    @Volatile protected var searchWindow: InputReceiver? = null
    @Volatile private var searchWindowComponent: Component? = null

    protected abstract fun displayItems(): List<ListEntry<T>>
    protected abstract fun onItemActivated(item: ListEntry.Item<T>)

    protected fun buildSearchBar(): Component? = searchWindowComponent

    protected fun handleListInput(event: FtxUIEvent): Boolean {
        val sw = searchWindow
        if (sw != null) return sw.onInput(event)
        return when {
            event.matches(keybindings.moveUpKeys, keybindings.moveUpChars) -> { moveUp(1); true }
            event.matches(keybindings.moveDownKeys, keybindings.moveDownChars) -> { moveDown(1); true }
            event.matches(keybindings.pageUpKeys, keybindings.pageUpChars) -> { moveUp(jumpSize); true }
            event.matches(keybindings.pageDownKeys, keybindings.pageDownChars) -> { moveDown(jumpSize); true }
            event.matches(keybindings.homeKeys, keybindings.homeChars) -> { focusFirst(); true }
            event.matches(keybindings.endKeys, keybindings.endChars) -> { focusLast(); true }
            event.matches(keybindings.selectKeys, keybindings.selectChars) -> {
                val entry = displayItems().getOrNull(focusedIndex)
                if (entry is ListEntry.Item) {
                    @Suppress("UNCHECKED_CAST")
                    onItemActivated(entry)
                    true
                } else false
            }
            event.matches(keybindings.searchKeys, keybindings.searchChars) -> { activateSearch(); true }
            else -> false
        }
    }

    private fun activateSearch() {
        val sw = SearchWindow(
            getEntries = { displayItems() },
            toSearchString = toSearchString,
            onResultSelected = { idx ->
                if (idx >= 0) { focusedIndex = idx; ensureScrollCoversSelection() }
            },
            onConfirm = { idx ->
                if (idx >= 0) {
                    @Suppress("UNCHECKED_CAST")
                    (displayItems().getOrNull(idx) as? ListEntry.Item<T>)?.let { onItemActivated(it) }
                }
                deactivateSearch()
            },
            onDismiss = { deactivateSearch() },
        )
        searchWindow = sw
        searchWindowComponent = sw.render()
    }

    protected open fun deactivateSearch() {
        searchWindow = null
        searchWindowComponent = null
    }

    private fun moveUp(count: Int) {
        val all = displayItems()
        var remaining = count
        var i = focusedIndex - 1
        while (i >= 0 && remaining > 0) {
            if (all[i] is ListEntry.Item) { focusedIndex = i; remaining-- }
            i--
        }
        ensureScrollCoversSelection()
    }

    protected open fun moveDown(count: Int) {
        val all = displayItems()
        var remaining = count
        var i = focusedIndex + 1
        while (i < all.size && remaining > 0) {
            if (all[i] is ListEntry.Item) { focusedIndex = i; remaining-- }
            i++
        }
        ensureScrollCoversSelection()
    }

    private fun focusFirst() {
        focusedIndex = displayItems().indexOfFirst { it is ListEntry.Item }.let { if (it < 0) -1 else it }
        ensureScrollCoversSelection()
    }

    private fun focusLast() {
        focusedIndex = displayItems().indexOfLast { it is ListEntry.Item }.let { if (it < 0) -1 else it }
        ensureScrollCoversSelection()
    }

    protected fun ensureScrollCoversSelection() {
        val visH = lastListH
        val fi = focusedIndex
        if (fi < 0) return
        if (fi < scrollOffset) scrollOffset = fi
        if (fi >= scrollOffset + visH) scrollOffset = fi - visH + 1
        scrollOffset = scrollOffset.coerceIn(0, maxOf(0, displayItems().size - visH))
    }
}

// ---------------------------------------------------------------------------
// ListView
// ---------------------------------------------------------------------------

open class ListView<T>(
    private val renderItem: (data: T, focused: Boolean) -> Element,
    private val renderHeader: (data: T) -> Element,
    toSearchString: (T) -> String = { it.toString() },
    pageSize: Int = 10,
    private val onStateChange: ((ListState<T>) -> Unit)? = null,
    private val onSelectionChanged: ((focusedItem: T?) -> Unit)? = null,
    keybindings: ListKeybindings = ListKeybindings(),
) : BaseListWindow<T>(jumpSize = pageSize, toSearchString = toSearchString, keybindings = keybindings), InputReceiver {

    @Volatile private var state: ListState<T> = ListState(emptyList())

    override fun displayItems(): List<ListEntry<T>> = state.entries

    override fun onItemActivated(item: ListEntry.Item<T>) = item.onEnter?.invoke() ?: Unit

    // Call this whenever the Screen's state observer receives a new emission.
    fun updateState(newState: ListState<T>) {
        state = newState
        if (onStateChange != null) {
            focusedIndex = newState.focusedIndex
            scrollOffset = newState.scrollOffset
        }
        ensureValidFocus()
        ensureScrollCoversSelection()
    }

    fun render(state: ListState<T>): Component {
        this.state = state
        if (onStateChange != null) {
            this.focusedIndex = state.focusedIndex
            this.scrollOffset = state.scrollOffset
        }
        val prevFocus = focusedIndex
        ensureValidFocus()
        if (onStateChange != null && focusedIndex != prevFocus) {
            notifyStateChange()
        }
        return renderer { buildElement() }
    }

    override fun onInput(event: FtxUIEvent): Boolean {
        val oldFocus = focusedIndex
        val oldScroll = scrollOffset
        val oldSearchActive = searchWindow != null
        val oldSearchQuery = (searchWindow as? SearchWindow<*>)?.query ?: ""

        val handled = handleListInput(event)

        if (handled) {
            val newSearchActive = searchWindow != null
            val newSearchQuery = (searchWindow as? SearchWindow<*>)?.query ?: ""
            if (focusedIndex != oldFocus || scrollOffset != oldScroll || newSearchActive != oldSearchActive || newSearchQuery != oldSearchQuery) {
                notifyStateChange()
            }
        }
        return handled
    }

    private fun notifyStateChange() {
        val newState = ListState(
            entries = state.entries,
            focusedIndex = focusedIndex,
            scrollOffset = scrollOffset,
            searchActive = searchWindow != null,
            searchQuery = (searchWindow as? SearchWindow<*>)?.query ?: ""
        )
        onStateChange?.invoke(newState)

        val item = state.entries.getOrNull(focusedIndex)
        if (item is ListEntry.Item) {
            onSelectionChanged?.invoke(item.data)
        } else {
            onSelectionChanged?.invoke(null)
        }
    }

    private fun buildElement(): Element {
        val entries = state.entries
        if (entries.isEmpty()) return emptyElement()

        val swComp = buildSearchBar()
        val totalH = Terminal.size().dimy
        val listH = if (swComp != null) maxOf(1, totalH - 2) else totalH
        lastListH = listH

        val start = scrollOffset.coerceIn(0, entries.size)
        val end = (scrollOffset + listH).coerceIn(0, entries.size)
        val items = entries.subList(start, end).mapIndexed { i, entry ->
            val absoluteIndex = start + i
            when (entry) {
                is ListEntry.Header -> renderHeader(entry.data)
                is ListEntry.Item -> renderItem(entry.data, absoluteIndex == focusedIndex)
            }
        }

        val listRow = hbox(
            vbox(*items.toTypedArray()).flex(),
            vScrollBar(scrollOffset, entries.size, listH),
        )

        return if (swComp != null) {
            vbox(swComp.render(), separator(), listRow)
        } else {
            listRow
        }
    }

    private fun ensureValidFocus() {
        val entries = state.entries
        if (focusedIndex < 0 || focusedIndex >= entries.size || entries[focusedIndex] is ListEntry.Header<*>) {
            focusedIndex = entries.indexOfFirst { it is ListEntry.Item }.let { if (it < 0) -1 else it }
        }
    }
}

// ---------------------------------------------------------------------------
// Fuzzy match — subsequence, case-insensitive
// ---------------------------------------------------------------------------

private fun fuzzyMatch(query: String, target: String): Boolean {
    val q = query.lowercase()
    val t = target.lowercase()
    var qi = 0
    for (c in t) { if (qi < q.length && c == q[qi]) qi++ }
    return qi == q.length
}
