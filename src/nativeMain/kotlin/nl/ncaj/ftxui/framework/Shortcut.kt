package nl.ncaj.ftxui.framework

import nl.ncaj.ftxui.Element
import nl.ncaj.ftxui.dim
import nl.ncaj.ftxui.text

data class Shortcut(
    val key: String,
    val label: String,
    val labelElement: (String) -> Element = { text(it).dim() },
    val showInStatusBar: Boolean = true,
    val description: String = label,
    val action: () -> Unit,
)
