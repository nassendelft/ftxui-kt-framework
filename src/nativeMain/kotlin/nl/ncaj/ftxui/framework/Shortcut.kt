package nl.ncaj.ftxui.framework

data class Shortcut(
    val key: String,
    val label: String,
    val showInStatusBar: Boolean = true,
    val description: String = label,
    val action: () -> Unit,
)
