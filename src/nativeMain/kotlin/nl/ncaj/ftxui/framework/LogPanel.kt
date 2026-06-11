package nl.ncaj.ftxui.framework

import nl.ncaj.ftxui.*

internal fun AppContext.logPanel(
    isOpen: () -> Boolean,
    onToggle: (Boolean) -> Unit
): Component {
    var minLevel by mutableStateOf<Logger.Level?>(null)

    fun filteredEntries(): List<Logger.Entry> {
        val min = minLevel ?: return Logger.entries()
        return Logger.entries().filter { it.level >= min }
    }

    val listComp = list(
        getEntries = { filteredEntries().map { ListEntry.Item(it) } },
        renderItem = { entry, _ ->
            val (icon, color) = when (entry.level) {
                Logger.Level.Debug -> "·" to Color.GrayLight
                Logger.Level.Info -> "ℹ" to Theme.current.info
                Logger.Level.Warn -> "⚠" to Theme.current.warning
                Logger.Level.Error -> "✗" to Theme.current.error
            }
            hbox(text("  ${entry.time} ").dim(), text("$icon ").color(color), text(entry.message), text("  "))
        },
        renderHeader = { emptyElement() },
        stateId = { minLevel },
    )

    return listComp.decorateRender { listElement ->
        val entries = filteredEntries()
        val allCount = Logger.entries().size
        val termH = terminalSize.height
        val targetH = (termH * 0.45f).toInt().coerceAtLeast(8)
        if (targetH < 2) return@decorateRender emptyElement()

        val filterLabel = when (minLevel) {
            null -> "all"
            Logger.Level.Info -> "≥info"
            Logger.Level.Warn -> "≥warn"
            Logger.Level.Error -> "error"
            else -> ""
        }
        val titleCount = if (minLevel == null) "${entries.size}" else "${entries.size}/${allCount}"
        val header = hbox(
            text("  "),
            text("Logs").color(Theme.current.accent).bold(),
            text(" [$titleCount]${if (filterLabel.isNotEmpty()) "  $filterLabel" else ""}").color(Theme.current.accent),
            filler(),
        )
        val content = if (entries.isEmpty()) {
            hbox(text("  "), text("No log entries").dim(), text("  "))
        } else {
            listElement
        }
        val hint = hbox(
            text("  "),
            text("j/k scroll  g/G top/bottom  f filter  Esc/^L close").dim(),
            text("  "),
        )
        val body = vbox(
            header,
            separator(),
            content.flex(),
            separator(),
            hint,
            text("")
        ).borderStyled(BorderStyle.Heavy, Theme.current.accent)
            .clearUnder()
            .size(WidthOrHeight.Height, Constraint.Equal, targetH)
            .xflex()

        vbox(filler(), body)
    }.catchEvent { event ->
        when {
            event.isKey(Key.Escape) && isOpen() -> {
                onToggle(false)
                true
            }
            event.isKey(Key.CtrlL) -> {
                onToggle(!isOpen())
                true
            }
            isChar(event, "f") && isOpen() -> {
                minLevel = when (minLevel) {
                    null -> Logger.Level.Info
                    Logger.Level.Info -> Logger.Level.Warn
                    Logger.Level.Warn -> Logger.Level.Error
                    Logger.Level.Error -> null
                    else -> null
                }
                true
            }
            else -> false
        }
    }
}
