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
}
