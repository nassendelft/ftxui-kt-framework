package nl.ncaj.ftxui.framework

import nl.ncaj.ftxui.*

internal fun AppContext.commandPalette(
    isOpen: () -> Boolean,
    onToggle: (Boolean) -> Unit,
    getShortcuts: () -> List<Shortcut>
): Component {
    var query by mutableStateOf("")
    var cursor by mutableStateOf(0)
    var lastOpen = false

    fun filteredItems(): List<Shortcut> {
        val shortcuts = getShortcuts()
        if (query.isEmpty()) return shortcuts
        val q = query.lowercase()
        return shortcuts.filter { it.label.lowercase().contains(q) || it.description.lowercase().contains(q) }
    }

    val base = focusableRenderer {
        val open = isOpen()
        if (open && !lastOpen) {
            query = ""
            cursor = 0
        }
        lastOpen = open

        val items = filteredItems()
        val maxVisible = 8
        val labelWidth = (items.take(maxVisible).maxOfOrNull { it.label.length } ?: 8).coerceAtLeast(8)
        val queryEl = hbox(
            text("> ").color(Theme.current.accent).bold(),
            text(query),
            text("█").color(Theme.current.accent),
        )
        val rows: List<Element> = if (items.isEmpty()) {
            listOf(hbox(text("  "), text("No matching commands").color(Theme.current.warning).dim(), text("  ")))
        } else {
            items.take(maxVisible).mapIndexed { i, sc ->
                val isFocused = i == cursor
                if (isFocused) {
                    hbox(
                        text(" ▶ ").color(Theme.current.accent).bold(),
                        text(sc.label.padEnd(labelWidth) + "  ").color(Theme.current.accent).bold(),
                        text(sc.description).dim(),
                        text("  "),
                    )
                } else {
                    hbox(text("   "), text(sc.label.padEnd(labelWidth) + "  "), text(sc.description), text("  ")).dim()
                }
            }
        }
        val body = vbox(hbox(text("  "), queryEl, text("  ")), separator(), *rows.toTypedArray(), text(""))
        body.window(text(" Commands ").color(Theme.current.accent).bold()).clearUnder().hcenter().vcenter()
    }

    return base.catchEvent { event ->
        if (!isOpen()) return@catchEvent false
        val items = filteredItems()
        when {
            event.isKey(Key.Escape) -> {
                onToggle(false)
                true
            }

            event.isKey(Key.Return) -> {
                items.getOrNull(cursor)?.action?.invoke()
                onToggle(false)
                true
            }

            event.isKey(Key.ArrowUp) -> {
                cursor = (cursor - 1).coerceAtLeast(0)
                true
            }

            event.isKey(Key.ArrowDown) -> {
                cursor = (cursor + 1).coerceAtMost(maxOf(0, items.lastIndex))
                true
            }

            event.isKey(Key.Backspace) -> {
                if (query.isNotEmpty()) {
                    query = query.dropLast(1)
                    cursor = 0
                }
                true
            }

            event is FtxUIEvent.Character -> {
                query += event.character
                cursor = 0
                true
            }
            else -> true
        }
    }
}
