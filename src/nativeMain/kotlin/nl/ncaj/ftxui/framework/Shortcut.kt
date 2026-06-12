package nl.ncaj.ftxui.framework

import nl.ncaj.ftxui.Element
import nl.ncaj.ftxui.dim
import nl.ncaj.ftxui.text

import nl.ncaj.ftxui.Key

data class Shortcut(
    val key: String,
    val label: String,
    val labelElement: (String) -> Element = { text(it).dim() },
    val showInStatusBar: Boolean = true,
    val description: String = label,
    val action: () -> Unit,
) {
    fun getShortcutDisplay(): Pair<String, String> {
        if (key.isEmpty()) {
            return Pair("", label)
        }

        val formattedKey = when (key) {
            Key.Return -> "Enter"
            Key.Escape -> "Esc"
            Key.Tab -> "Tab"
            Key.Backspace -> "Backspace"
            Key.ArrowUp -> "↑"
            Key.ArrowDown -> "↓"
            Key.ArrowLeft -> "←"
            Key.ArrowRight -> "→"
            else -> {
                if (key.length == 1 && key[0] in '\u0001'..'\u001a') {
                    "^" + ('A'.code + (key[0].code - 1)).toChar()
                } else {
                    key
                }
            }
        }

        val isAlreadyPrepend = label.equals(formattedKey, ignoreCase = true) ||
                label.startsWith("$formattedKey ", ignoreCase = true) ||
                label.startsWith("$formattedKey\t", ignoreCase = true) ||
                label.startsWith("$formattedKey:", ignoreCase = true) ||
                label.startsWith("[$formattedKey]", ignoreCase = true) ||
                (formattedKey.startsWith("^") && label.startsWith(formattedKey, ignoreCase = true))

        return if (isAlreadyPrepend) {
            Pair("", label)
        } else {
            Pair(formattedKey, label)
        }
    }
}

