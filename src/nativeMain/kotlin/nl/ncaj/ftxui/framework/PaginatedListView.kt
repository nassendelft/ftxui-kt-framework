package nl.ncaj.ftxui.framework

import kotlin.concurrent.Volatile
import kotlinx.coroutines.*
import nl.ncaj.ftxui.*

open class PaginatedListView<T>(
    private val pageSize: Int = 50,
    private val loadThreshold: Int = 10,
    private val loadPage: suspend (offset: Int, limit: Int) -> List<ListEntry<T>>,
    private val renderItem: (data: T, focused: Boolean) -> Element,
    private val renderHeader: (data: T) -> Element,
    toSearchString: (T) -> String = { it.toString() },
    private val onSelect: ((ListEntry.Item<T>) -> Unit)? = null,
    private val onTotalCount: (() -> Int?)? = null,
    keybindings: ListKeybindings = ListKeybindings(),
    private val style: ListStyle = ListStyle(),
) : BaseListWindow<T>(jumpSize = pageSize, toSearchString = toSearchString, keybindings = keybindings), InputReceiver {

    @Volatile private var items: List<ListEntry<T>> = emptyList()
    @Volatile private var isLoadingMore: Boolean = false
    @Volatile private var hasMore: Boolean = true
    @Volatile private var totalCount: Int? = null

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    @Volatile private var loadJob: Job? = null

    override fun displayItems(): List<ListEntry<T>> = items

    override fun onItemActivated(item: ListEntry.Item<T>) { onSelect?.invoke(item) }

    override fun moveDown(count: Int) {
        super.moveDown(count)
        val all = displayItems()
        if (!isLoadingMore && hasMore && focusedIndex >= all.size - loadThreshold) loadNextPage()
    }

    init {
        loadNextPage()
    }

    fun render(state: Unit = Unit): Component = renderer { buildElement() }

    override fun onInput(event: FtxUIEvent): Boolean = handleListInput(event)

    // -----------------------------------------------------------------------
    // Rendering
    // -----------------------------------------------------------------------

    private fun buildElement(): Element {
        val all = displayItems()
        val swComp = buildSearchBar()
        val visH = Terminal.size().dimy - Screen.STATUS_BAR_HEIGHT
        val listH = if (swComp != null) maxOf(1, visH - 2) else visH
        lastListH = listH

        val ensuredFocus = if (focusedIndex < 0 && all.isNotEmpty()) {
            focusedIndex = all.indexOfFirst { it is ListEntry.Item }.let { if (it < 0) -1 else it }
            focusedIndex
        } else focusedIndex

        val start = scrollOffset.coerceIn(0, all.size)
        val end = (scrollOffset + listH).coerceIn(0, all.size)
        val slice = all.subList(start, end)

        val rows = slice.mapIndexed { i, entry ->
            @Suppress("UNCHECKED_CAST")
            when (entry) {
                is ListEntry.Header -> {
                    val el = renderHeader(entry.data)
                    style.headerForeground?.let { el.color(it) } ?: el
                }
                is ListEntry.Item   -> {
                    val focused = (start + i) == ensuredFocus
                    val el = renderItem(entry.data, focused)
                    if (focused) {
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

        val statusRow = buildStatusRow()
        val allRows = rows + loadingRow
        val listEl = hbox(
            vbox(*allRows.toTypedArray()).flex(),
            vScrollBar(scrollOffset, all.size + (if (isLoadingMore) 1 else 0), listH, style.scrollThumb.or(Theme.current.scrollThumb)),
        )

        val bottomEl = if (statusRow != null) vbox(listEl, separator(), statusRow) else listEl

        if (!isLoadingMore && hasMore && ensuredFocus >= all.size - loadThreshold) {
            loadNextPage()
        }

        return if (swComp != null) vbox(swComp.render(), separator(), bottomEl) else bottomEl
    }

    private fun buildStatusRow(): Element? {
        val total = totalCount ?: onTotalCount?.invoke() ?: return null
        val showing = items.count { it is ListEntry.Item }
        return hbox(text("  "), text("Showing $showing of $total").dim(), filler())
    }

    // -----------------------------------------------------------------------
    // Page loading
    // -----------------------------------------------------------------------

    private fun loadNextPage() {
        if (isLoadingMore || !hasMore) return
        isLoadingMore = true
        FtxUIApp.active()?.requestAnimationFrame()
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
            FtxUIApp.active()?.requestAnimationFrame()
        }
    }
}
