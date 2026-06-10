package nl.ncaj.ftxui.framework

import nl.ncaj.ftxui.*

data class TreeNode<T>(
    val data: T,
    val children: List<TreeNode<T>> = emptyList(),
    val isExpanded: Boolean = true,
    val onEnter: (() -> Unit)? = null,
    val onToggle: (() -> Unit)? = null,
)

data class TreeState<T>(
    val roots: List<TreeNode<T>>,
    val focusedPath: List<Int> = emptyList(),
    val scrollOffset: Int = 0
)

private data class FlatItem<T>(val node: TreeNode<T>, val depth: Int, val path: List<Int>)

fun <T> ScreenContext.tree(
    getState: () -> TreeState<T>,
    renderNode: (data: T, depth: Int, focused: Boolean, hasChildren: Boolean, isExpanded: Boolean) -> Element,
    keybindings: TreeKeybindings = TreeKeybindings(),
    style: TreeStyle = TreeStyle(),
    onSelectionChanged: ((focusedNode: TreeNode<T>?) -> Unit)? = null
): Component {
    var focusedPath by mutableStateOf(emptyList<Int>())
    var scrollOffset by mutableStateOf(0)

    val flattenVisible: () -> List<FlatItem<T>> = {
        val result = mutableListOf<FlatItem<T>>()
        fun traverse(nodes: List<TreeNode<T>>, depth: Int, prefix: List<Int>) {
            nodes.forEachIndexed { i, node ->
                val path = prefix + i
                result += FlatItem(node, depth, path)
                if (node.isExpanded && node.children.isNotEmpty()) traverse(node.children, depth + 1, path)
            }
        }
        traverse(getState().roots, 0, emptyList())
        result
    }

    val ensureValidFocus: (List<FlatItem<T>>) -> Unit = { flat ->
        if (flat.isEmpty()) {
            focusedPath = emptyList()
        } else {
            var path = focusedPath
            while (path.isNotEmpty() && flat.none { it.path == path }) path = path.dropLast(1)
            focusedPath = if (flat.any { it.path == path }) path else flat.first().path
        }
    }

    val ensureScrollCoversSelection: (List<FlatItem<T>>) -> Unit = { flat ->
        val visibleH = Terminal.size().dimy
        val fi = flat.indexOfFirst { it.path == focusedPath }
        if (fi >= 0) {
            if (fi < scrollOffset) scrollOffset = fi
            if (fi >= scrollOffset + visibleH) scrollOffset = fi - visibleH + 1
            scrollOffset = scrollOffset.coerceIn(0, maxOf(0, flat.size - visibleH))
        }
    }

    val stepSelection: (Int) -> Unit = { delta ->
        val flat = flattenVisible()
        val idx = flat.indexOfFirst { it.path == focusedPath }
        val newIdx = (idx + delta).coerceIn(0, flat.lastIndex)
        if (newIdx != idx) {
            focusedPath = flat[newIdx].path
            ensureScrollCoversSelection(flat)
            onSelectionChanged?.invoke(flat[newIdx].node)
        }
    }

    val expandOrDescend: () -> Unit = {
        val flat = flattenVisible()
        val item = flat.find { it.path == focusedPath }
        if (item != null) {
            when {
                item.node.children.isEmpty() -> Unit
                !item.node.isExpanded        -> item.node.onToggle?.invoke()
                else -> {
                    val childPath = focusedPath + 0
                    if (flat.any { it.path == childPath }) {
                        focusedPath = childPath
                        ensureScrollCoversSelection(flat)
                        onSelectionChanged?.invoke(flat.find { it.path == focusedPath }?.node)
                    }
                }
            }
        }
    }

    val collapseOrAscend: () -> Unit = {
        val flat = flattenVisible()
        val item = flat.find { it.path == focusedPath }
        if (item != null) {
            when {
                item.node.isExpanded && item.node.children.isNotEmpty() -> item.node.onToggle?.invoke()
                focusedPath.size > 1 -> {
                    val parent = focusedPath.dropLast(1)
                    if (flat.any { it.path == parent }) {
                        focusedPath = parent
                        ensureScrollCoversSelection(flat)
                        onSelectionChanged?.invoke(flat.find { it.path == focusedPath }?.node)
                    }
                }
            }
        }
    }

    val base = focusableRenderer { focused ->
        val flat = flattenVisible()
        if (flat.isEmpty()) {
            focusedPath = emptyList()
            return@focusableRenderer emptyElement()
        }
        ensureValidFocus(flat)
        val visibleH = Terminal.size().dimy
        ensureScrollCoversSelection(flat)

        val start = scrollOffset.coerceIn(0, flat.size)
        val end = (scrollOffset + visibleH).coerceIn(0, flat.size)
        val rows = flat.subList(start, end).map { item ->
            val isFocused = item.path == focusedPath
            val prefix = when {
                item.node.children.isEmpty() -> style.leafIndent
                item.node.isExpanded         -> style.expandedIcon
                else                         -> style.collapsedIcon
            }
            val row = hbox(
                text("  ".repeat(item.depth) + prefix),
                renderNode(item.node.data, item.depth, isFocused, item.node.children.isNotEmpty(), item.node.isExpanded),
            )
            if (isFocused && focused) {
                val fg = style.focusedNodeForeground
                val bg = style.focusedNodeBackground
                when {
                    fg != null && bg != null -> row.color(fg).bgcolor(bg)
                    fg != null -> row.color(fg)
                    bg != null -> row.bgcolor(bg)
                    else -> row
                }
            } else row
        }

        hbox(
            vbox(*rows.toTypedArray()).flex(),
            vScrollBar(scrollOffset, flat.size, visibleH, style.scrollThumb.or(Theme.current.scrollThumb)),
        )
    }

    return base.catchEvent { event ->
        val flat = flattenVisible()
        when {
            event.matches(keybindings.moveDownKeys, keybindings.moveDownChars) -> { stepSelection(+1); true }
            event.matches(keybindings.moveUpKeys, keybindings.moveUpChars) -> { stepSelection(-1); true }
            event.matches(keybindings.expandKeys, keybindings.expandChars) -> { expandOrDescend(); true }
            event.matches(keybindings.collapseKeys, keybindings.collapseChars) -> { collapseOrAscend(); true }
            event.matches(keybindings.selectKeys, keybindings.selectChars) -> {
                val item = flat.find { it.path == focusedPath }
                item?.node?.onEnter?.invoke()
                item?.node?.onEnter != null
            }
            else -> false
        }
    }
}
