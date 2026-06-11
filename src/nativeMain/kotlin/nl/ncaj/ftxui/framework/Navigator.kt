package nl.ncaj.ftxui.framework

import nl.ncaj.ftxui.Component
import kotlin.time.Duration

interface Navigator {
    val currentComponent: Component?
    fun push(component: Component)
    fun pop()
    fun showDialog(dialog: Dialog)
    fun dismissDialog()
    fun notify(message: String, duration: Duration, type: Toast.Type = Toast.Type.Info)

    fun registerShortcuts(shortcuts: List<Shortcut>)
    fun clearShortcuts()
}
