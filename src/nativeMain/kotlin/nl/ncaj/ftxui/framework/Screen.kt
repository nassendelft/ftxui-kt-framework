package nl.ncaj.ftxui.framework

import nl.ncaj.ftxui.*
import kotlin.reflect.KProperty

abstract class Screen {
    open val globalShortcuts: List<Shortcut> = emptyList()
    open val activeWindow: Component? get() = null

    // Subclasses implement this to build their main content component once.
    protected abstract fun AppContext.buildContent(navigator: Navigator): Component

    // Called by the framework — wraps buildContent with the status bar.
    fun build(context: AppContext, navigator: Navigator): Component {
        val content = context.buildContent(navigator)
        return renderer(child = content) {
            vbox(
                content.render().flex(),
                buildStatusBar(),
            )
        }
    }

    // Override to customize the status bar area (separator included).
    protected open fun buildStatusBar(): Element {
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
        val shortcut = globalShortcuts.find { event.isKey(it.key) }
        if (shortcut != null) { shortcut.action(); return true }
        if (event.isKey(Key.Escape) || event.isKey(Key.Backspace)) { navigator.pop(); return true }
        return false
    }

    companion object {
        const val STATUS_BAR_HEIGHT = 2
    }
}
