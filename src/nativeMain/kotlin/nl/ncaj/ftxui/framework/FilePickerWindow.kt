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
)

@OptIn(ExperimentalForeignApi::class)
open class FilePickerWindow(
    initialPath: String = ".",
    val onFileSelected: (String) -> Unit = {},
    showHiddenInitially: Boolean = false,
    val filter: ((FileEntry) -> Boolean)? = null,
    val rowContent: ((entry: FileEntry, focused: Boolean) -> Element)? = null,
    override val extraHeader: WindowSection? = null,
    override val extraFooter: WindowSection? = null,
) : Window<FilePickerState> {

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

    override fun getVisibleHeight(): Int = Terminal.size().dimy - Screen.STATUS_BAR_HEIGHT

    override fun render(state: FilePickerState): Component = renderer { buildElement() }

    override fun onInput(event: FtxUIEvent): Boolean {
        if (filtering) return handleFilterInput(event)
        return handleNormalInput(event)
    }

    private fun handleNormalInput(event: FtxUIEvent): Boolean = when {
        event.isKey(Key.ArrowUp) || isChar(event, "k")  -> { moveUp(1); true }
        event.isKey(Key.ArrowDown) || isChar(event, "j") -> { moveDown(1); true }
        event.isKey(Key.PageUp)   || event.isKey(Key.CtrlU) -> { moveUp(pageSize()); true }
        event.isKey(Key.PageDown) || event.isKey(Key.CtrlD) -> { moveDown(pageSize()); true }
        event.isKey(Key.Home) || isChar(event, "g")  -> { selectedIndex = 0; ensureScrollCoversSelection(); true }
        event.isKey(Key.End)  || isChar(event, "G")  -> { selectedIndex = visibleEntries().lastIndex; ensureScrollCoversSelection(); true }
        event.isKey(Key.Return) -> { onEnter(); true }
        event.isKey(Key.Backspace) || isChar(event, "h") -> { goUp(); true }
        isChar(event, "/") -> { filtering = true; filterQuery = ""; true }
        isChar(event, ".") -> { showHidden = !showHidden; loadEntries(); true }
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
        return wrapWithDecorations(content)
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

@OptIn(ExperimentalForeignApi::class)
private fun listDirectory(path: String): List<FileEntry>? {
    val dir = opendir(path) ?: return null
    val entries = mutableListOf<FileEntry>()
    try {
        while (true) {
            val entry = readdir(dir) ?: break
            val name = entry.pointed.d_name.toKString()
            if (name == "." || name == "..") continue
            val fullPath = "$path/$name"
            memScoped {
                val st = alloc<stat>()
                if (stat(fullPath, st.ptr) == 0) {
                    val isDir = st.st_mode.toInt() and S_IFMT == S_IFDIR
                    entries.add(FileEntry(name, isDir, st.st_size))
                } else {
                    entries.add(FileEntry(name, false, 0L))
                }
            }
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
