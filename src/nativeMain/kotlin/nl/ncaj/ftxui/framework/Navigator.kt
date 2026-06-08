package nl.ncaj.ftxui.framework

import kotlin.time.Duration

interface Navigator {
    val currentScreen: Screen<*, *>?
    fun push(screen: Screen<*, *>)
    fun pop()
    fun showDialog(dialog: Dialog)
    fun dismissDialog()
    fun notify(message: String, duration: Duration, type: Toast.Type = Toast.Type.Info)
}
