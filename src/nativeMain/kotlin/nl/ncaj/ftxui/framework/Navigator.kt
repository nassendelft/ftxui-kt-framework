package nl.ncaj.ftxui.framework

import kotlinx.coroutines.CoroutineScope
import nl.ncaj.ftxui.Component
import kotlin.time.Duration

class Navigator internal constructor(private val app: App) {
    private val shortcutsMap = mutableMapOf<Component, () -> List<Shortcut>>()

    val currentComponent: Component? get() = app.currentComponent
    fun push(component: Component) = app.push(component)
    fun pop() = app.pop()
    fun showDialog(dialog: Dialog) = app.showDialog(dialog)
    fun dismissDialog() = app.dismissDialog()
    fun notify(message: String, duration: Duration, type: Toast.Type = Toast.Type.Info) =
        app.notify(message, duration, type)

    fun registerShortcutsForComponent(component: Component, provider: () -> List<Shortcut>) {
        shortcutsMap[component] = provider
    }

    fun registerShortcutsForComponent(component: Component, shortcuts: List<Shortcut>) {
        shortcutsMap[component] = { shortcuts }
    }

    internal fun getShortcutsForComponent(component: Component): List<Shortcut> {
        return shortcutsMap[component]?.invoke() ?: emptyList()
    }

    fun removeShortcutsForComponent(component: Component) {
        shortcutsMap.remove(component)
    }

    /**
     * Returns a [CoroutineScope] tied to [component]'s lifetime on the navigation stack.
     * The scope is cancelled when the component is popped (or when the app exits), making
     * it the right place to collect ViewModel state from screen builders.
     */
    fun scopeFor(component: Component): CoroutineScope = app.scopeFor(component)

    /**
     * Registers a callback invoked with `true` when [component] becomes the top of the
     * navigation stack and `false` when it is covered by a push or removed by a pop.
     * Use it to pause background work (polling, refresh loops) while a screen is hidden.
     * The registration is removed automatically when the component is popped.
     */
    fun onTopChanged(component: Component, callback: (visible: Boolean) -> Unit) =
        app.registerOnTopChanged(component, callback)
}
