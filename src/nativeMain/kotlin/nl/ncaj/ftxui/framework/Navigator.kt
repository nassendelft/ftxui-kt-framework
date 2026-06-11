package nl.ncaj.ftxui.framework

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

    fun registerShortcuts(shortcuts: List<Shortcut>) = app.registerShortcuts(shortcuts)
    fun clearShortcuts() = app.clearShortcuts()

    internal fun registerShortcutsForComponent(component: Component, provider: () -> List<Shortcut>) {
        shortcutsMap[component] = provider
    }

    internal fun getShortcutsForComponent(component: Component): List<Shortcut> {
        return shortcutsMap[component]?.invoke() ?: emptyList()
    }

    internal fun removeShortcutsForComponent(component: Component) {
        shortcutsMap.remove(component)
    }
}

fun Component.registerShortcuts(navigator: Navigator, provider: () -> List<Shortcut>): Component {
    navigator.registerShortcutsForComponent(this, provider)
    return this
}

fun Component.registerShortcuts(navigator: Navigator, shortcuts: List<Shortcut>): Component {
    navigator.registerShortcutsForComponent(this) { shortcuts }
    return this
}
