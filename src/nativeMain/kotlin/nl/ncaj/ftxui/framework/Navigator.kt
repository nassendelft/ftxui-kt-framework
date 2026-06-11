package nl.ncaj.ftxui.framework

import nl.ncaj.ftxui.Component
import kotlin.time.Duration

class Navigator internal constructor(private val app: App) {
    val currentComponent: Component? get() = app.currentComponent
    fun push(component: Component) = app.push(component)
    fun pop() = app.pop()
    fun showDialog(dialog: Dialog) = app.showDialog(dialog)
    fun dismissDialog() = app.dismissDialog()
    fun notify(message: String, duration: Duration, type: Toast.Type = Toast.Type.Info) =
        app.notify(message, duration, type)

    fun registerShortcuts(shortcuts: List<Shortcut>) = app.registerShortcuts(shortcuts)
    fun clearShortcuts() = app.clearShortcuts()
}
