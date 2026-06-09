package nl.ncaj.ftxui.framework

import kotlin.concurrent.Volatile
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

open class TreeView<T>(
    private val renderNode: (data: T, depth: Int, focused: Boolean, hasChildren: Boolean, isExpanded: Boolean) -> Element,
    private val onStateChange: ((TreeState<T>) -> Unit)? = null,
    private val onSelectionChanged: ((focusedNode: TreeNode<T>?) -> Unit)? = null,
    private val keybindings: TreeKeybindings = TreeKeybindings(),
) : InputReceiver {

    @Volatile private var state: TreeState<T> = TreeState(emptyList())
    @Volatile private var focusedPath: List<Int> = emptyList()
    @Volatile private var scrollOffset: Int = 0

    fun updateState(newState: TreeState<T>) {
        state = newState
        if (onStateChange != null) {
            focusedPath = newState.focusedPath
            scrollOffset = newState.scrollOffset
        }
        ensureValidFocus()
        ensureScrollCoversSelection(flattenVisible())
    }

    fun render(state: TreeState<T>): Component {
        this.state = state
        if (onStateChange != null) {
            this.focusedPath = state.focusedPath
            this.scrollOffset = state.scrollOffset
        }
        val prevPath = focusedPath
        val flat = flattenVisible()
        ensureValidFocus(flat)
        if (onStateChange != null && focusedPath != prevPath) {
            notifyStateChange()
        }
        return renderer { buildElement() }
    }

    override fun onInput(event: FtxUIEvent): Boolean {
        val oldPath = focusedPath
        val oldScroll = scrollOffset

        val handled = when {
            event.matches(keybindings.moveDownKeys, keybindings.moveDownChars) -> { moveBy(+1); true }
            event.matches(keybindings.moveUpKeys, keybindings.moveUpChars) -> { moveBy(-1); true }
            event.matches(keybindings.expandKeys, keybindings.expandChars) -> { expandOrDescend(); true }
            event.matches(keybindings.collapseKeys, keybindings.collapseChars) -> { collapseOrAscend(); true }
            event.matches(keybindings.selectKeys, keybindings.selectChars) -> {
                val item = flattenVisible().find { it.path == focusedPath }
                item?.node?.onEnter?.invoke()
                item?.node?.onEnter != null
            }
            else -> false
        }

        if (handled) {
            if (focusedPath != oldPath || scrollOffset != oldScroll) {
                notifyStateChange()
            }
        }
        return handled
    }

    private fun notifyStateChange() {
        val newState = TreeState(
            roots = state.roots,
            focusedPath = focusedPath,
            scrollOffset = scrollOffset
        )
        onStateChange?.invoke(newState)
        val item = flattenVisible().find { it.path == focusedPath }
        onSelectionChanged?.invoke(item?.node)
    }

    // ---------------------------------------------------------------------------
    // Flat traversal
    // ---------------------------------------------------------------------------

    private fun flattenVisible(): List<FlatItem<T>> {
        val result = mutableListOf<FlatItem<T>>()
        fun traverse(nodes: List<TreeNode<T>>, depth: Int, prefix: List<Int>) {
            nodes.forEachIndexed { i, node ->
                val path = prefix + i
                result += FlatItem(node, depth, path)
                if (node.isExpanded && node.children.isNotEmpty()) traverse(node.children, depth + 1, path)
            }
        }
        traverse(state.roots, 0, emptyList())
        return result
    }

    // ---------------------------------------------------------------------------
    // Rendering
    // ---------------------------------------------------------------------------

    private fun buildElement(): Element {
        val flat = flattenVisible()
        if (flat.isEmpty()) { focusedPath = emptyList(); return emptyElement() }
        ensureValidFocus(flat)
        val visibleH = Terminal.size().dimy
        ensureScrollCoversSelection(flat)
        val start = scrollOffset.coerceIn(0, flat.size)
        val end = (scrollOffset + visibleH).coerceIn(0, flat.size)
        val rows = flat.subList(start, end).map { item ->
            val focused = item.path == focusedPath
            val prefix = when {
                item.node.children.isEmpty() -> "  "
                item.node.isExpanded         -> "▾ "
                else                         -> "▸ "
            }
            hbox(
                text("  ".repeat(item.depth) + prefix),
                renderNode(item.node.data, item.depth, focused, item.node.children.isNotEmpty(), item.node.isExpanded),
            )
        }
        return hbox(
            vbox(*rows.toTypedArray()).flex(),
            vScrollBar(scrollOffset, flat.size, visibleH),
        )
    }

    // ---------------------------------------------------------------------------
    // Navigation
    // ---------------------------------------------------------------------------

    private fun moveBy(delta: Int) {
        val flat = flattenVisible()
        val idx = flat.indexOfFirst { it.path == focusedPath }
        val newIdx = (idx + delta).coerceIn(0, flat.lastIndex)
        if (newIdx != idx) {
            focusedPath = flat[newIdx].path
            ensureScrollCoversSelection(flat)
        }
    }

    private fun expandOrDescend() {
        val flat = flattenVisible()
        val item = flat.find { it.path == focusedPath } ?: return
        when {
            item.node.children.isEmpty() -> Unit          // leaf — nothing to do
            !item.node.isExpanded        -> item.node.onToggle?.invoke()
            else -> {                                     // expanded — move into first child
                val childPath = focusedPath + 0
                if (flat.any { it.path == childPath }) {
                    focusedPath = childPath
                    ensureScrollCoversSelection(flat)
                }
            }
        }
    }

    private fun collapseOrAscend() {
        val flat = flattenVisible()
        val item = flat.find { it.path == focusedPath } ?: return
        when {
            item.node.isExpanded && item.node.children.isNotEmpty() -> item.node.onToggle?.invoke()
            focusedPath.size > 1 -> {
                val parent = focusedPath.dropLast(1)
                if (flat.any { it.path == parent }) {
                    focusedPath = parent
                    ensureScrollCoversSelection(flat)
                }
            }
            else -> Unit  // top-level collapsed — nothing to do
        }
    }

    // ---------------------------------------------------------------------------
    // Focus / scroll helpers
    // ---------------------------------------------------------------------------

    private fun ensureValidFocus() = ensureValidFocus(flattenVisible())

    private fun ensureValidFocus(flat: List<FlatItem<T>>) {
        if (flat.isEmpty()) { focusedPath = emptyList(); return }
        var path = focusedPath
        while (path.isNotEmpty() && flat.none { it.path == path }) path = path.dropLast(1)
        focusedPath = if (flat.any { it.path == path }) path else flat.first().path
    }

    private fun ensureScrollCoversSelection(flat: List<FlatItem<T>>) {
        val visibleH = Terminal.size().dimy
        val fi = flat.indexOfFirst { it.path == focusedPath }.takeIf { it >= 0 } ?: return
        if (fi < scrollOffset) scrollOffset = fi
        if (fi >= scrollOffset + visibleH) scrollOffset = fi - visibleH + 1
        scrollOffset = scrollOffset.coerceIn(0, maxOf(0, flat.size - visibleH))
    }
}
