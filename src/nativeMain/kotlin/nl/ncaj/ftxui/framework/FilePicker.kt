package nl.ncaj.ftxui.framework

import kotlinx.cinterop.*
import nl.ncaj.ftxui.*
import platform.posix.closedir
import platform.posix.getcwd
import platform.posix.opendir
import platform.posix.readdir

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
fun AppContext.filePicker(
    initialPath: String = ".",
    onFileSelected: (String) -> Unit = {},
    showHiddenInitially: Boolean = false,
    filter: ((FileEntry) -> Boolean)? = null,
    rowContent: ((entry: FileEntry, focused: Boolean) -> Element)? = null,
    keybindings: FilePickerKeybindings = FilePickerKeybindings(),
    style: FilePickerStyle = FilePickerStyle()
): Component {
    var currentPath by mutableStateOf(resolvePath(initialPath))
    var entries by mutableStateOf(emptyList<FileEntry>())
    var selectedIndex by mutableStateOf(0)
    var scrollOffset by mutableStateOf(0)
    var showHidden by mutableStateOf(showHiddenInitially)
    var filterQuery by mutableStateOf("")
    var filtering by mutableStateOf(false)

    val loadEntries: () -> Unit = {
        val raw = listDirectory(currentPath) ?: emptyList()
        entries = raw
            .filter { showHidden || !it.name.startsWith(".") }
            .let { list -> filter?.let { f -> list.filter(f) } ?: list }
    }

    val visibleEntries: () -> List<FileEntry> = {
        if (filterQuery.isEmpty()) entries
        else {
            val q = filterQuery.lowercase()
            entries.filter { it.name.lowercase().contains(q) }
        }
    }

    val viewport = viewport()
    val getListHeight: () -> Int = { maxOf(1, viewport.height) }
    val pageSize: () -> Int = { maxOf(1, getListHeight() - 2) }

    val ensureScrollCoversSelection: () -> Unit = {
        val listH = getListHeight()
        val visible = visibleEntries()
        if (selectedIndex < scrollOffset) scrollOffset = selectedIndex
        if (selectedIndex >= scrollOffset + listH) scrollOffset = selectedIndex - listH + 1
        scrollOffset = scrollOffset.coerceIn(0, maxOf(0, visible.size - listH))
    }

    val moveUp: (Int) -> Unit = { count ->
        selectedIndex = (selectedIndex - count).coerceAtLeast(0)
        ensureScrollCoversSelection()
    }

    val moveDown: (Int) -> Unit = { count ->
        selectedIndex = (selectedIndex + count).coerceAtMost(maxOf(0, visibleEntries().lastIndex))
        ensureScrollCoversSelection()
    }

    val onEnter: () -> Unit = {
        val entry = visibleEntries().getOrNull(selectedIndex)
        if (entry != null) {
            if (entry.isDirectory) {
                currentPath = "$currentPath/${entry.name}".normalizePath()
                loadEntries()
                selectedIndex = 0
                scrollOffset = 0
            } else {
                onFileSelected("$currentPath/${entry.name}")
            }
        }
    }

    val goUp: () -> Unit = {
        val parent = currentPath.substringBeforeLast('/', "")
        if (parent.isNotEmpty()) {
            val prevDir = currentPath.substringAfterLast('/')
            currentPath = parent
            loadEntries()
            selectedIndex = visibleEntries().indexOfFirst { it.name == prevDir }.coerceAtLeast(0)
            ensureScrollCoversSelection()
        }
    }

    // Initial load
    loadEntries()

    val buildEntryRow: (FileEntry, Boolean) -> Element = { entry, focused ->
        if (rowContent != null) rowContent(entry, focused)
        else {
            val icon = if (entry.isDirectory) "[d]" else "   "
            val size = if (entry.isDirectory) "" else formatSize(entry.sizeBytes).padStart(8)
            val entryColor = if (entry.isDirectory)
                style.directoryColor.or(Theme.current.directoryColor)
            else
                style.fileColor.or(Theme.current.fileColor)
            val row = hbox(
                text("  $icon ").color(entryColor),
                text(entry.name).flex(),
                text("  $size  ").dim(),
            )
            if (focused) row.inverted() else row
        }
    }

    val base = focusableRenderer { focused ->
        val visible = visibleEntries()
        val listH = getListHeight()

        ensureScrollCoversSelection()
        val start = scrollOffset.coerceIn(0, visible.size)
        val end = (scrollOffset + listH).coerceIn(0, visible.size)
        val slice = visible.subList(start, end)

        val rows: List<Element> = if (visible.isEmpty()) {
            listOf(hbox(text("  "), text("(empty)").dim(), text("  ")))
        } else {
            slice.mapIndexed { i, entry ->
                val absIdx = start + i
                val isFocused = absIdx == selectedIndex
                buildEntryRow(entry, isFocused && focused)
            }
        }

        val pathEl = hbox(
            text("  ").dim(),
            text(currentPath).color(style.pathColor.or(Theme.current.accent)),
            if (showHidden) text("  [.hidden]").dim() else emptyElement(),
            text("  "),
        )

        val listRow = viewport.measure(hbox(
            vbox(*rows.toTypedArray()).flex(),
            vScrollBar(scrollOffset, visible.size, listH, style.scrollThumb.or(Theme.current.scrollThumb)),
        ))

        val main = vbox(pathEl, separator(), listRow)

        if (filtering) {
            val queryEl = hbox(text("/ $filterQuery█"))
            vbox(main, separator(), queryEl)
        } else {
            main
        }
    }

    return base.catchEvent { event ->
        if (filtering) {
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
            true
        } else {
            when {
                event.matches(keybindings.moveUpKeys, keybindings.moveUpChars)  -> { moveUp(1); true }
                event.matches(keybindings.moveDownKeys, keybindings.moveDownChars) -> { moveDown(1); true }
                event.matches(keybindings.pageUpKeys, keybindings.pageUpChars) -> {
                    val count = pageSize()
                    val visible = visibleEntries()
                    if (selectedIndex >= 0 && visible.isNotEmpty()) {
                        val relOffset = selectedIndex - scrollOffset
                        val targetScroll = (scrollOffset - count).coerceIn(0, maxOf(0, visible.size - getListHeight()))
                        selectedIndex = if (targetScroll == scrollOffset) {
                            0
                        } else {
                            (targetScroll + relOffset).coerceIn(0, visible.lastIndex)
                        }
                        scrollOffset = targetScroll
                    }
                    true
                }
                event.matches(keybindings.pageDownKeys, keybindings.pageDownChars) -> {
                    val count = pageSize()
                    val visible = visibleEntries()
                    if (selectedIndex >= 0 && visible.isNotEmpty()) {
                        val relOffset = selectedIndex - scrollOffset
                        val targetScroll = (scrollOffset + count).coerceIn(0, maxOf(0, visible.size - getListHeight()))
                        selectedIndex = if (targetScroll == scrollOffset) {
                            visible.lastIndex
                        } else {
                            (targetScroll + relOffset).coerceIn(0, visible.lastIndex)
                        }
                        scrollOffset = targetScroll
                    }
                    true
                }
                event.matches(keybindings.homeKeys, keybindings.homeChars)  -> { selectedIndex = 0; ensureScrollCoversSelection(); true }
                event.matches(keybindings.endKeys, keybindings.endChars)  -> { selectedIndex = visibleEntries().lastIndex; ensureScrollCoversSelection(); true }
                event.matches(keybindings.selectKeys, keybindings.selectChars) -> { onEnter(); true }
                event.matches(keybindings.goUpKeys, keybindings.goUpChars) -> { goUp(); true }
                event.matches(keybindings.searchKeys, keybindings.searchChars) -> { filtering = true; filterQuery = ""; true }
                event.matches(keybindings.toggleHiddenKeys, keybindings.toggleHiddenChars) -> { showHidden = !showHidden; loadEntries(); true }
                else -> false
            }
        }
    }
}

// -----------------------------------------------------------------------
// POSIX helpers
// -----------------------------------------------------------------------

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
