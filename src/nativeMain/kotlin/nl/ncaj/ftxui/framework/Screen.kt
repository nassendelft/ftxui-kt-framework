package nl.ncaj.ftxui.framework

import nl.ncaj.ftxui.*
import kotlin.reflect.KProperty

interface ScreenContext {
    val navigator: Navigator
    fun requestRedraw()
}

class MutableState<T>(
    private var internalValue: T,
    private val onStateChange: (T) -> Unit
) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return internalValue
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        if (internalValue != value) {
            internalValue = value
            onStateChange(value)
        }
    }
}

fun <T> ScreenContext.mutableStateOf(initial: T): MutableState<T> =
    MutableState(initial) { _ -> requestRedraw() }

abstract class Screen {
    open val globalShortcuts: List<Shortcut> = emptyList()
    open val activeWindow: Component? get() = null

    // Subclasses implement this to build their main content component once.
    protected abstract fun buildContent(context: ScreenContext): Component

    // Called by the framework — wraps buildContent with the status bar.
    fun build(context: ScreenContext): Component {
        val content = buildContent(context)
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
