package nl.ncaj.ftxui.framework

import kotlin.concurrent.Volatile
import kotlinx.coroutines.*
import nl.ncaj.ftxui.*

open class PaginatedListWindow<T>(
    private val pageSize: Int = 50,
    private val loadThreshold: Int = 10,
    private val loadPage: suspend (offset: Int, limit: Int) -> List<ListEntry<T>>,
    private val renderItem: (data: T, focused: Boolean) -> Element,
    private val renderHeader: (data: T) -> Element,
    toSearchString: (T) -> String = { it.toString() },
    private val onSelect: ((ListEntry.Item<T>) -> Unit)? = null,
    private val onTotalCount: (() -> Int?)? = null,
    override val extraHeader: WindowSection? = null,
    override val extraFooter: WindowSection? = null,
) : BaseListWindow<T>(jumpSize = pageSize, toSearchString = toSearchString), Window<Unit> {

    @Volatile private var items: List<ListEntry<T>> = emptyList()
    @Volatile private var isLoadingMore: Boolean = false
    @Volatile private var hasMore: Boolean = true
    @Volatile private var totalCount: Int? = null

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    @Volatile private var loadJob: Job? = null

    override val activeSubWindow: Window<*>? get() = searchWindow

    override fun displayItems(): List<ListEntry<T>> = items

    override fun onItemActivated(item: ListEntry.Item<T>) { onSelect?.invoke(item) }

    override fun getVisibleHeight(): Int = Terminal.size().dimy - Screen.STATUS_BAR_HEIGHT

    override fun moveDown(count: Int) {
        super.moveDown(count)
        val all = displayItems()
        if (!isLoadingMore && hasMore && focusedIndex >= all.size - loadThreshold) loadNextPage()
    }

    init {
        loadNextPage()
    }

    override fun render(state: Unit): Component = renderer { buildElement() }

    override fun onInput(event: FtxUIEvent): Boolean = handleListInput(event)

    // -----------------------------------------------------------------------
    // Rendering
    // -----------------------------------------------------------------------

    private fun buildElement(): Element {
        val all = displayItems()
        val swComp = buildSearchBar()
        val visH = contentHeight()
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
                is ListEntry.Header -> renderHeader(entry.data as T)
                is ListEntry.Item   -> renderItem(entry.data as T, (start + i) == ensuredFocus)
            }
        }

        val loadingRow: List<Element> = if (isLoadingMore) {
            listOf(hbox(filler(), text("loading…").dim(), filler()))
        } else emptyList()

        val statusRow = buildStatusRow()
        val allRows = rows + loadingRow
        val listEl = hbox(
            vbox(*allRows.toTypedArray()).flex(),
            vScrollBar(scrollOffset, all.size + (if (isLoadingMore) 1 else 0), listH),
        )

        val bottomEl = if (statusRow != null) vbox(listEl, separator(), statusRow) else listEl

        if (!isLoadingMore && hasMore && ensuredFocus >= all.size - loadThreshold) {
            loadNextPage()
        }

        val content = if (swComp != null) vbox(swComp.render(), separator(), bottomEl) else bottomEl
        return wrapWithDecorations(content)
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
