package nl.ncaj.ftxui.framework

import nl.ncaj.ftxui.*

abstract class Screen<S, E> {
    abstract val viewModel: ViewModel<S, E>
    open val globalShortcuts: List<Shortcut> = emptyList()
    open val activeWindow: Window<*>? get() = null

    // Subclasses implement this to provide their main content area.
    protected abstract fun buildContent(state: S): Component

    // Called by the framework — wraps buildContent with the status bar.
    fun render(state: S): Component {
        val content = buildContent(state)
        return renderer {
            vbox(
                content.render().flex(),
                buildStatusBar(state),
            )
        }
    }

    // Override to customize the entire status bar area (separator included).
    protected open fun buildStatusBar(state: S): Element {
        val visible = globalShortcuts.filter { it.showInStatusBar }
        val elems = buildList {
            add(text(" "))
            visible.forEachIndexed { i, sc ->
                if (i > 0) add(text("  "))
                add(text(sc.label).dim())
            }
            add(filler())
        }
        return hbox(*elems.toTypedArray())
    }

    open fun handleInput(event: FtxUIEvent, navigator: Navigator): Boolean {
        if (activeWindow?.onInput(event) == true) return true
        val shortcut = globalShortcuts.find { event.isKey(it.key) }
        if (shortcut != null) { shortcut.action(); return true }
        if (event.isKey(Key.Escape) || event.isKey(Key.Backspace)) { navigator.pop(); return true }
        return false
    }

    companion object {
        const val STATUS_BAR_HEIGHT = 2
    }
}
