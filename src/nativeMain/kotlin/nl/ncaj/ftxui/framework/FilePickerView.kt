package nl.ncaj.ftxui.framework

import kotlin.concurrent.Volatile
import kotlinx.cinterop.*
import platform.posix.*
import nl.ncaj.ftxui.*

data class FileEntry(
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
)

data class FilePickerState(
    val initialPath: String = ".",
    val showHidden: Boolean = false,
    val currentPath: String = "",
    val selectedIndex: Int = 0,
    val scrollOffset: Int = 0,
    val filterQuery: String = "",
    val filtering: Boolean = false
)

@OptIn(ExperimentalForeignApi::class)
open class FilePickerView(
    initialPath: String = ".",
    val onFileSelected: (String) -> Unit = {},
    showHiddenInitially: Boolean = false,
    val filter: ((FileEntry) -> Boolean)? = null,
    val rowContent: ((entry: FileEntry, focused: Boolean) -> Element)? = null,
    private val onStateChange: ((FilePickerState) -> Unit)? = null,
    private val keybindings: FilePickerKeybindings = FilePickerKeybindings(),
) : InputReceiver {

    @Volatile private var currentPath: String = resolvePath(initialPath)
    @Volatile private var entries: List<FileEntry> = emptyList()
    @Volatile private var selectedIndex: Int = 0
    @Volatile private var scrollOffset: Int = 0
    @Volatile private var showHidden: Boolean = showHiddenInitially
    @Volatile private var filterQuery: String = ""
    @Volatile private var filtering: Boolean = false

    init {
        loadEntries()
    }

    private fun getVisibleHeight(): Int = Terminal.size().dimy - Screen.STATUS_BAR_HEIGHT
    private fun contentHeight(): Int = getVisibleHeight()

    fun render(state: FilePickerState): Component {
        val oldPath = currentPath
        val oldHidden = showHidden
        if (onStateChange != null) {
            currentPath = if (state.currentPath.isNotEmpty()) state.currentPath else resolvePath(state.initialPath)
            selectedIndex = state.selectedIndex
            scrollOffset = state.scrollOffset
            showHidden = state.showHidden
            filterQuery = state.filterQuery
            filtering = state.filtering
        }
        if (currentPath != oldPath || showHidden != oldHidden || entries.isEmpty()) {
            loadEntries()
        }
        return renderer { buildElement() }
    }

    override fun onInput(event: FtxUIEvent): Boolean {
        val oldPath = currentPath
        val oldIndex = selectedIndex
        val oldScroll = scrollOffset
        val oldHidden = showHidden
        val oldFilterQuery = filterQuery
        val oldFiltering = filtering

        val handled = if (filtering) handleFilterInput(event) else handleNormalInput(event)

        if (handled) {
            if (currentPath != oldPath || selectedIndex != oldIndex || scrollOffset != oldScroll || showHidden != oldHidden || filterQuery != oldFilterQuery || filtering != oldFiltering) {
                notifyStateChange()
            }
        }
        return handled
    }

    private fun notifyStateChange() {
        val newState = FilePickerState(
            initialPath = currentPath,
            showHidden = showHidden,
            currentPath = currentPath,
            selectedIndex = selectedIndex,
            scrollOffset = scrollOffset,
            filterQuery = filterQuery,
            filtering = filtering
        )
        onStateChange?.invoke(newState)
    }

    private fun handleNormalInput(event: FtxUIEvent): Boolean = when {
        event.matches(keybindings.moveUpKeys, keybindings.moveUpChars)  -> { moveUp(1); true }
        event.matches(keybindings.moveDownKeys, keybindings.moveDownChars) -> { moveDown(1); true }
        event.matches(keybindings.pageUpKeys, keybindings.pageUpChars) -> { moveUp(pageSize()); true }
        event.matches(keybindings.pageDownKeys, keybindings.pageDownChars) -> { moveDown(pageSize()); true }
        event.matches(keybindings.homeKeys, keybindings.homeChars)  -> { selectedIndex = 0; ensureScrollCoversSelection(); true }
        event.matches(keybindings.endKeys, keybindings.endChars)  -> { selectedIndex = visibleEntries().lastIndex; ensureScrollCoversSelection(); true }
        event.matches(keybindings.selectKeys, keybindings.selectChars) -> { onEnter(); true }
        event.matches(keybindings.goUpKeys, keybindings.goUpChars) -> { goUp(); true }
        event.matches(keybindings.searchKeys, keybindings.searchChars) -> { filtering = true; filterQuery = ""; true }
        event.matches(keybindings.toggleHiddenKeys, keybindings.toggleHiddenChars) -> { showHidden = !showHidden; loadEntries(); true }
        else -> false
    }

    private fun handleFilterInput(event: FtxUIEvent): Boolean {
        when {
            event.isKey(Key.Escape) -> { filtering = false; filterQuery = "" }
            event.isKey(Key.Return) -> { filtering = false }
            event.isKey(Key.Backspace) -> {
                if (filterQuery.isNotEmpty()) filterQuery = filterQuery.dropLast(1)
                else filtering = false
            }
            event is FtxUIEvent.Character -> filterQuery += event.character
        }
        selectedIndex = 0
        scrollOffset = 0
        return true
    }

    private fun onEnter() {
        val entry = visibleEntries().getOrNull(selectedIndex) ?: return
        if (entry.isDirectory) {
            currentPath = "$currentPath/${entry.name}".normalizePath()
            loadEntries()
            selectedIndex = 0
            scrollOffset = 0
        } else {
            onFileSelected("$currentPath/${entry.name}")
        }
    }

    private fun goUp() {
        val parent = currentPath.substringBeforeLast('/', "")
        if (parent.isNotEmpty()) {
            val prevDir = currentPath.substringAfterLast('/')
            currentPath = parent
            loadEntries()
            selectedIndex = visibleEntries().indexOfFirst { it.name == prevDir }.coerceAtLeast(0)
            ensureScrollCoversSelection()
        }
    }

    private fun loadEntries() {
        val raw = listDirectory(currentPath) ?: emptyList()
        entries = raw
            .filter { showHidden || !it.name.startsWith(".") }
            .let { list -> filter?.let { f -> list.filter(f) } ?: list }
    }

    private fun visibleEntries(): List<FileEntry> {
        if (filterQuery.isEmpty()) return entries
        val q = filterQuery.lowercase()
        return entries.filter { it.name.lowercase().contains(q) }
    }

    // -----------------------------------------------------------------------
    // Rendering
    // -----------------------------------------------------------------------

    private fun buildElement(): Element {
        val visible = visibleEntries()
        val visH = contentHeight()
        val headerLines = 2  // path header + separator
        val footerLines = if (filtering) 2 else 0
        val listH = maxOf(1, visH - headerLines - footerLines)

        ensureScrollCoversSelection()
        val start = scrollOffset.coerceIn(0, visible.size)
        val end = (scrollOffset + listH).coerceIn(0, visible.size)
        val slice = visible.subList(start, end)

        val rows: List<Element> = if (visible.isEmpty()) {
            listOf(hbox(text("  "), text("(empty)").dim(), text("  ")))
        } else {
            slice.mapIndexed { i, entry ->
                val absIdx = start + i
                val focused = absIdx == selectedIndex
                buildEntryRow(entry, focused)
            }
        }

        val pathEl = hbox(
            text("  ").dim(),
            text(currentPath).color(Theme.current.accent),
            if (showHidden) text("  [.hidden]").dim() else emptyElement(),
            text("  "),
        )

        val listRow = hbox(
            vbox(*rows.toTypedArray()).flex(),
            vScrollBar(scrollOffset, visible.size, listH),
        )

        val main = vbox(pathEl, separator(), listRow)

        val content = if (filtering) {
            val queryEl = hbox(text("/ $filterQuery█"))
            vbox(main, separator(), queryEl)
        } else {
            main
        }
        return content
    }

    private fun buildEntryRow(entry: FileEntry, focused: Boolean): Element {
        if (rowContent != null) return rowContent(entry, focused)
        val icon = if (entry.isDirectory) "[d]" else "   "
        val size = if (entry.isDirectory) "" else formatSize(entry.sizeBytes).padStart(8)
        val row = hbox(
            text("  $icon ").color(if (entry.isDirectory) Theme.current.accent else Color.Default),
            text(entry.name).flex(),
            text("  $size  ").dim(),
        )
        return if (focused) row.inverted() else row
    }

    // -----------------------------------------------------------------------
    // Navigation helpers
    // -----------------------------------------------------------------------

    private fun moveUp(count: Int) {
        selectedIndex = (selectedIndex - count).coerceAtLeast(0)
        ensureScrollCoversSelection()
    }

    private fun moveDown(count: Int) {
        selectedIndex = (selectedIndex + count).coerceAtMost(maxOf(0, visibleEntries().lastIndex))
        ensureScrollCoversSelection()
    }

    private fun ensureScrollCoversSelection() {
        val listH = contentHeight() - 2
        if (selectedIndex < scrollOffset) scrollOffset = selectedIndex
        if (selectedIndex >= scrollOffset + listH) scrollOffset = selectedIndex - listH + 1
        scrollOffset = scrollOffset.coerceIn(0, maxOf(0, visibleEntries().size - listH))
    }

    private fun pageSize(): Int = maxOf(1, getVisibleHeight() - 4)
}

// -----------------------------------------------------------------------
// POSIX helpers
// -----------------------------------------------------------------------

// `mode_t` and `stat.st_mode` resolve to integer types of different bit widths on macOS vs Linux,
// so the stat lookup is implemented per-target and exposed here through a width-safe type.
internal class PathInfo(val isDirectory: Boolean, val sizeBytes: Long)
internal expect fun statPath(path: String): PathInfo?

@OptIn(ExperimentalForeignApi::class)
private fun listDirectory(path: String): List<FileEntry>? {
    val dir = opendir(path) ?: return null
    val entries = mutableListOf<FileEntry>()
    try {
        while (true) {
            val entry = readdir(dir) ?: break
            val name = entry.pointed.d_name.toKString()
            if (name == "." || name == "..") continue
            val info = statPath("$path/$name")
            entries.add(FileEntry(name, info?.isDirectory ?: false, info?.sizeBytes ?: 0L))
        }
    } finally {
        closedir(dir)
    }
    return entries.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
}

@OptIn(ExperimentalForeignApi::class)
private fun resolvePath(path: String): String {
    if (path == ".") {
        return memScoped {
            val buf = allocArray<ByteVar>(4096)
            getcwd(buf, 4096.toULong())?.toKString() ?: path
        }
    }
    return path
}

private fun String.normalizePath(): String {
    val parts = split("/").filter { it.isNotEmpty() && it != "." }
    val result = mutableListOf<String>()
    for (p in parts) {
        if (p == "..") { if (result.isNotEmpty()) result.removeLast() }
        else result.add(p)
    }
    return "/" + result.joinToString("/")
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "${bytes}B"
    bytes < 1024 * 1024 -> "${bytes / 1024}K"
    bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)}M"
    else -> "${bytes / (1024 * 1024 * 1024)}G"
}
