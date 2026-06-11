package nl.ncaj.ftxui.framework

import nl.ncaj.ftxui.*

internal fun AppContext.notificationPanel(
    isOpen: () -> Boolean,
    getLog: () -> List<NotificationRecord>,
    onToggle: (Boolean) -> Unit,
) : Component {
    fun notifIconAndColor(type: Toast.Type): Pair<String, Color> = when (type) {
        Toast.Type.Info    -> "ℹ" to Theme.current.info
        Toast.Type.Success -> "✓" to Theme.current.success
        Toast.Type.Warning -> "⚠" to Theme.current.warning
        Toast.Type.Error   -> "✗" to Theme.current.error
    }

    val listComp = list(
        getEntries = { getLog().map { ListEntry.Item(it) } },
        renderItem = { record, _ ->
            val (icon, color) = notifIconAndColor(record.type)
            hbox(text("  ${record.timestamp} ").dim(), text("$icon  ").color(color), text(record.message), text("  "))
        },
        renderHeader = { emptyElement() },
    )

    return listComp.decorateRender { listElement ->
        val termW = terminalSize.width
        val targetWidth = (termW * 0.38f).toInt().coerceIn(42, 90)
        if (targetWidth < 3) return@decorateRender emptyElement()

        val log = getLog()
        val header = hbox(
            text("  "),
            text("Notifications").color(Theme.current.accent).bold(),
            text(" [${log.size}]").color(Theme.current.accent),
            filler()
        )
        val content = if (log.isEmpty()) {
            hbox(text("  "), text("No notifications yet").dim(), text("  "))
        } else {
            listElement
        }
        val hint = hbox(text("  "), text("j/k scroll  Esc/^N close").dim(), text("  "))
        val body = vbox(header, separator(), content.flex(), separator(), hint, text(""))

        hbox(
            filler(),
            body.borderStyled(BorderStyle.Heavy, Theme.current.accent)
                .clearUnder()
                .size(WidthOrHeight.Width, Constraint.Equal, targetWidth)
                .yflex()
        )
    }.catchEvent { event ->
        when {
            event.isKey(Key.Escape) && isOpen() -> {
                onToggle(false)
                true
            }
            event.isKey(Key.CtrlN) -> {
                onToggle(!isOpen())
                true
            }
            else -> false
        }
    }
}

