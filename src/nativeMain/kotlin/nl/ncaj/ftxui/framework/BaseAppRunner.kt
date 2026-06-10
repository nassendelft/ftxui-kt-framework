package nl.ncaj.ftxui.framework

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import nl.ncaj.ftxui.*
import kotlin.concurrent.Volatile
import kotlin.time.TimeSource

internal abstract class BaseAppRunner(
    protected val app: FtxUIApp,
    protected val confirmOnQuit: Boolean,
) {
    protected val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val logPanel = LogPanel(scope) { app.requestAnimationFrame() }
    private val notifPanel = NotificationPanel(scope, { app.requestAnimationFrame() }) { activeNotificationLog() }

    @Volatile
    protected var showHelp: Boolean = false
    @Volatile
    protected var showPalette: Boolean = false
    @Volatile
    protected var paletteQuery: String = ""
    @Volatile
    protected var paletteCursor: Int = 0

    @Volatile
    protected var showPerfOverlay: Boolean = false
    @Volatile
    protected var avgFrameMs: Float = 0f
    protected var lastFrameMark: TimeSource.Monotonic.ValueTimeMark? = null

    @Volatile
    protected var confirmingQuit: Boolean = false

    protected abstract fun buildContentElement(): Element
    protected abstract fun buildActiveToastsElement(): Element?
    protected abstract fun buildActiveDialogElement(): Element?
    protected abstract fun isDialogActive(): Boolean
    protected abstract fun handleActiveDialogInput(event: FtxUIEvent): Boolean
    protected abstract fun handleScreenInput(event: FtxUIEvent): Boolean
    protected abstract fun activeScreen(): Screen?
    protected abstract fun activeStackSize(): Int
    protected abstract fun activeNotificationLog(): List<NotificationRecord>
    protected open fun extraPerfLines(): List<String> = emptyList()
    protected open fun extraHelpRows(): List<Element> = emptyList()
    protected open fun handleExtraInput(event: FtxUIEvent): Boolean = false

    protected abstract fun getScreenContainer(): Component

    protected fun createRootComponent() = renderer(child = getScreenContainer()) {
        measureFrame()
        var el = buildContentElement()
        buildActiveToastsElement()?.let { el = dbox(el, it) }
        val anyModal = isDialogActive() || showHelp || showPalette ||
                logPanel.progress > 0f || notifPanel.progress > 0f || confirmingQuit
        if (anyModal) el = el.dim()
        buildActiveDialogElement()?.let { el = dbox(el, it) }
        if (confirmingQuit) el = dbox(el, buildConfirmQuitElement())
        if (showHelp) el = dbox(el, buildHelpElement())
        if (showPalette) el = dbox(el, buildPaletteElement())
        notifPanel.buildElement()?.let { el = dbox(el, it) }
        logPanel.buildElement()?.let { el = dbox(el, it) }
        if (showPerfOverlay) el = dbox(el, buildPerfOverlayElement())
        el
    }.catchEvent { event -> handleEvent(event) }

    private fun handleEvent(event: FtxUIEvent): Boolean = when {
        isDialogActive() -> handleActiveDialogInput(event)
        logPanel.isOpen -> logPanel.handleInput(event)
        notifPanel.isOpen -> notifPanel.handleInput(event)
        showPalette -> handlePaletteInput(event)
        showHelp -> {
            if (event.isKey(Key.Escape) || event.isKey(Key.Return) || isChar(event, "?")) {
                showHelp = false
                app.requestAnimationFrame()
            }
            true
        }

        confirmingQuit -> {
            when {
                event.isKey(Key.Return) || isChar(event, "y") || isChar(event, "Y") -> {
                    confirmingQuit = false; app.exit()
                }

                else -> {
                    confirmingQuit = false; app.requestAnimationFrame()
                }
            }
            true
        }

        else -> {
            if (handleExtraInput(event)) return true
            val handled = handleScreenInput(event)
            when {
                !handled && confirmOnQuit && event.isKey(Key.CtrlC) -> {
                    confirmingQuit = true; app.requestAnimationFrame(); true
                }

                !handled && event.isKey(Key.CtrlAltP) -> {
                    showPerfOverlay = !showPerfOverlay; app.requestAnimationFrame(); true
                }

                !handled && isChar(event, "?") -> {
                    showHelp = true; app.requestAnimationFrame(); true
                }

                !handled && event.isKey(Key.CtrlP) -> {
                    showPalette = true; paletteQuery = ""; paletteCursor = 0
                    app.requestAnimationFrame(); true
                }

                !handled && event.isKey(Key.CtrlN) -> {
                    notifPanel.open(); true
                }

                !handled && event.isKey(Key.CtrlL) -> {
                    if (logPanel.isOpen) logPanel.close() else logPanel.open(); true
                }

                else -> handled
            }
        }
    }

    protected fun measureFrame() {
        val now = TimeSource.Monotonic.markNow()
        val prev = lastFrameMark
        if (prev != null) {
            val ms = prev.elapsedNow().inWholeMicroseconds / 1000f
            avgFrameMs = avgFrameMs * 0.9f + ms * 0.1f
        }
        lastFrameMark = now
    }

    private fun paletteItems(): List<Shortcut> {
        val shortcuts = activeScreen()?.globalShortcuts ?: emptyList()
        if (paletteQuery.isEmpty()) return shortcuts
        val q = paletteQuery.lowercase()
        return shortcuts.filter { it.label.lowercase().contains(q) || it.description.lowercase().contains(q) }
    }

    private fun handlePaletteInput(event: FtxUIEvent): Boolean {
        val items = paletteItems()
        when {
            event.isKey(Key.Escape) -> {
                showPalette = false; app.requestAnimationFrame()
            }

            event.isKey(Key.Return) -> {
                items.getOrNull(paletteCursor)?.action?.invoke()
                showPalette = false
                app.requestAnimationFrame()
            }

            event.isKey(Key.ArrowUp) -> {
                paletteCursor = (paletteCursor - 1).coerceAtLeast(0)
                app.requestAnimationFrame()
            }

            event.isKey(Key.ArrowDown) -> {
                paletteCursor = (paletteCursor + 1).coerceAtMost(maxOf(0, items.lastIndex))
                app.requestAnimationFrame()
            }

            event.isKey(Key.Backspace) -> {
                if (paletteQuery.isNotEmpty()) {
                    paletteQuery = paletteQuery.dropLast(1)
                    paletteCursor = 0
                    app.requestAnimationFrame()
                }
            }

            event is FtxUIEvent.Character -> {
                paletteQuery += event.character
                paletteCursor = 0
                app.requestAnimationFrame()
            }
        }
        return true
    }

    private fun buildPaletteElement(): Element {
        val items = paletteItems()
        val maxVisible = 8
        val labelWidth = (items.take(maxVisible).maxOfOrNull { it.label.length } ?: 8).coerceAtLeast(8)
        val queryEl = hbox(
            text("> ").color(Theme.current.accent).bold(),
            text(paletteQuery),
            text("█").color(Theme.current.accent),
        )
        val rows: List<Element> = if (items.isEmpty()) {
            listOf(hbox(text("  "), text("No matching commands").color(Theme.current.warning).dim(), text("  ")))
        } else {
            items.take(maxVisible).mapIndexed { i, sc ->
                val focused = i == paletteCursor
                if (focused) {
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
        return body.window(text(" Commands ").color(Theme.current.accent).bold()).clearUnder().hcenter().vcenter()
    }

    private fun buildConfirmQuitElement(): Element {
        val body = vbox(
            text(""),
            hbox(text("  ⚠  ").color(Theme.current.warning), text("Quit the application?"), text("  ")),
            text(""),
            separator(),
            hbox(
                filler(),
                text("[Y/Enter]").bold().color(Theme.current.success), text(" Quit  "),
                text("[Any key]").bold().color(Theme.current.error), text(" Cancel"),
                filler(),
            ),
        )
        return body.window(text(" Confirm Quit ").color(Theme.current.accent).bold()).clearUnder().hcenter().vcenter()
    }

    private fun buildHelpElement(): Element {
        val screen = activeScreen()
        val rows = buildList {
            screen?.globalShortcuts?.forEach { sc ->
                add(hbox(text("  ${sc.label.padEnd(12)} "), text(sc.description).dim(), text("  ")))
            }
            if (activeStackSize() > 1) {
                add(hbox(text("  Esc          "), text("go back").dim(), text("  ")))
            }
            add(hbox(text("  ?            "), text("show this help").dim(), text("  ")))
            add(hbox(text("  ^P           "), text("command palette").dim(), text("  ")))
            add(hbox(text("  ^N           "), text("notification history").dim(), text("  ")))
            add(hbox(text("  ^L           "), text("log viewer").dim(), text("  ")))
            addAll(extraHelpRows())
        }
        val body = if (rows.isEmpty()) vbox(text("  No shortcuts defined  ")) else vbox(*rows.toTypedArray())
        return body.window(text(" Keyboard Shortcuts ").bold()).clearUnder().hcenter().vcenter()
    }

    private fun buildPerfOverlayElement(): Element {
        val fps = if (avgFrameMs > 0) (1000f / avgFrameMs).toInt() else 0
        val termSize = Terminal.size()
        val frameInt = (avgFrameMs * 10).toInt()
        val lines = buildList {
            add("FPS:   $fps")
            add("Frame: ${frameInt / 10}.${frameInt % 10}ms")
            addAll(extraPerfLines())
            add("Term:  ${termSize.dimx}×${termSize.dimy}")
        }
        val body = vbox(*lines.map { hbox(text("  $it  ")) }.toTypedArray())
        return hbox(filler(), body.window(text(" perf ").dim()).clearUnder(), text(" "))
    }
}

internal fun isChar(event: FtxUIEvent, char: String) =
    event is FtxUIEvent.Character && event.character == char

internal fun vScrollBar(
    scrollY: Int,
    total: Int,
    visible: Int,
    thumbColor: Color = Theme.current.scrollThumb
): Element {
    if (total <= visible) return vbox(*(0 until maxOf(1, visible)).map { text(" ") }.toTypedArray())
    val thumbH = maxOf(1, visible * visible / total)
    val thumbY = ((scrollY.toLong() * maxOf(0, visible - thumbH)) / maxOf(1, total - visible)).toInt()
    return vbox(*(0 until visible).map { i ->
        if (i in thumbY until thumbY + thumbH) text("▐").color(thumbColor) else text("▕").dim()
    }.toTypedArray())
}

internal fun buildToastPill(toast: Toast, progress: Float): Element {
    val (icon, color) = when (toast.type) {
        Toast.Type.Info -> "ℹ" to Theme.current.info
        Toast.Type.Success -> "✓" to Theme.current.success
        Toast.Type.Warning -> "⚠" to Theme.current.warning
        Toast.Type.Error -> "✗" to Theme.current.error
    }
    val innerWidth = 5 + toast.message.length + 2
    val boxW = innerWidth + 2
    val totalPerim = 2 * boxW + 2
    val dimColor = Color.GrayDark
    val transitionWidth = 5f
    val stripe = totalPerim * 0.30f
    val frontier = progress * (totalPerim + stripe)

    fun borderEl(pos: Int, thickChar: String, medChar: String, thinChar: String): Element {
        val distA = pos - frontier
        val distB = pos - (frontier - stripe)
        val t = ((-distA / transitionWidth) + 0.5f).coerceIn(0f, 1f)
        val blended = Color.interpolate(t, color, dimColor)
        val char = when {
            distA > 0f -> thickChar; distB > 0f -> medChar; else -> thinChar
        }
        return text(char).color(blended)
    }

    val topElements = buildList {
        add(borderEl(0, "╔", "┏", "╭"))
        for (col in 1 until boxW - 1) add(borderEl(col, "═", "━", "─"))
        add(borderEl(boxW - 1, "╗", "┓", "╮"))
    }
    val leftEl = borderEl(2 * boxW + 1, "║", "┃", "│")
    val rightEl = borderEl(boxW, "║", "┃", "│")
    val contentEl = hbox(text("  $icon  ").color(color), text(toast.message), text("  "))
    val bottomElements = buildList {
        add(borderEl(2 * boxW, "╚", "┗", "╰"))
        for (col in 1 until boxW - 1) add(borderEl(2 * boxW - col, "═", "━", "─"))
        add(borderEl(boxW + 1, "╝", "┛", "╯"))
    }
    return vbox(
        hbox(*topElements.toTypedArray()),
        hbox(leftEl, contentEl, rightEl),
        hbox(*bottomElements.toTypedArray()),
    ).clearUnder()
}

internal fun buildSharedDialogElement(dialog: Dialog, promptInput: String): Element {
    val (icon, iconColor) = when (dialog) {
        is Dialog.Alert -> "ℹ" to Theme.current.info
        is Dialog.Confirm -> "⚠" to Theme.current.warning
        is Dialog.Prompt -> "›" to Theme.current.accent
    }
    val body: Element = when (dialog) {
        is Dialog.Alert -> vbox(
            text(""),
            hbox(text("  "), text("$icon  ").color(iconColor), text(dialog.message), text("  ")),
            text(""),
            separator(),
            hbox(filler(), text("[Enter/Esc]").bold().color(Theme.current.accent), text(" OK"), filler()),
        )

        is Dialog.Confirm -> vbox(
            text(""),
            hbox(text("  "), text("$icon  ").color(iconColor), text(dialog.message), text("  ")),
            text(""),
            separator(),
            hbox(
                filler(),
                text("[Y]").bold().color(Theme.current.success), text(" Confirm  "),
                text("[N/Esc]").bold().color(Theme.current.error), text(" Cancel"),
                filler(),
            ),
        )

        is Dialog.Prompt -> vbox(
            text(""),
            hbox(
                text("  "),
                text("$icon  ").color(iconColor),
                text("${dialog.placeholder}: "),
                text(promptInput),
                text("█").color(Theme.current.accent),
                text("  ")
            ),
            text(""),
            separator(),
            hbox(
                filler(),
                text("[Enter]").bold().color(Theme.current.accent), text(" OK  "),
                text("[Esc]").bold().color(Theme.current.error), text(" Cancel"),
                filler(),
            ),
        )
    }
    val title = when (dialog) {
        is Dialog.Alert -> dialog.title
        is Dialog.Confirm -> dialog.title
        is Dialog.Prompt -> dialog.title
    }
    return body.window(text(" $title ").color(Theme.current.accent).bold()).clearUnder().hcenter().vcenter()
}

internal fun handleSharedDialogInput(
    event: FtxUIEvent,
    dialog: Dialog,
    getPromptInput: () -> String,
    setPromptInput: (String) -> Unit,
    dismiss: () -> Unit,
    requestFrame: () -> Unit,
): Boolean {
    when (dialog) {
        is Dialog.Alert ->
            if (event.isKey(Key.Return) || event.isKey(Key.Escape)) {
                dialog.onDismiss(); dismiss()
            }

        is Dialog.Confirm -> when {
            event.isKey(Key.Return) || isChar(event, "y") || isChar(event, "Y") -> {
                dialog.onConfirm(); dismiss()
            }

            event.isKey(Key.Escape) || isChar(event, "n") || isChar(event, "N") -> {
                dialog.onCancel(); dismiss()
            }
        }

        is Dialog.Prompt -> when {
            event.isKey(Key.Return) -> {
                dialog.onSubmit(getPromptInput()); dismiss()
            }

            event.isKey(Key.Escape) -> {
                dialog.onCancel(); dismiss()
            }

            event.isKey(Key.Backspace) -> {
                val cur = getPromptInput()
                if (cur.isNotEmpty()) {
                    setPromptInput(cur.dropLast(1)); requestFrame()
                }
            }

            event is FtxUIEvent.Character -> {
                val cur = getPromptInput()
                if (cur.length < dialog.maxLength) {
                    setPromptInput(cur + event.character); requestFrame()
                }
            }
        }
    }
    return true
}
