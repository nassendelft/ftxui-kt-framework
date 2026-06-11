package nl.ncaj.ftxui.framework

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import nl.ncaj.ftxui.*

internal abstract class BaseAppRunner(
    protected val name: String,
    protected val app: FtxUIApp,
    protected val confirmOnQuit: Boolean,
) : AppContext {
    protected val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override val preferences = Preferences(name)

    override fun requestRedraw() = app.requestAnimationFrame()
    override fun post(action: () -> Unit) = app.post(action)

    override val terminalSize: Dimension get() = Dimension(Terminal.size().dimx, Terminal.size().dimy)

    protected var showHelp by mutableStateOf(false)
    protected var showPalette by mutableStateOf(false)
    protected var showPerfOverlay by mutableStateOf(false)
    protected var confirmingQuit by mutableStateOf(false)

    internal var logPanelOpen by mutableStateOf(false)
    internal var notificationPanelOpen by mutableStateOf(false)

    protected val logPanel by lazy {
        logPanel(
            isOpen = { logPanelOpen },
            onToggle = { open ->
                logPanelOpen = open
                if (!open) restoreFocus()
            }
        )
    }
    protected val notificationPanel by lazy {
        notificationPanel(
            isOpen = { notificationPanelOpen },
            onToggle = { open ->
                notificationPanelOpen = open
                if (!open) restoreFocus()
            },
            getLog = ::activeNotificationLog
        )
    }
    protected val palettePanel by lazy {
        commandPalette(
            isOpen = { showPalette },
            onToggle = { open ->
                showPalette = open
                if (!open) restoreFocus()
            },
            getShortcuts = { activeScreen()?.globalShortcuts ?: emptyList() }
        )
    }
    protected val perfPanel by lazy {
        perfOverlay(
            isVisible = { showPerfOverlay },
            getExtraLines = ::extraPerfLines
        )
    }

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

    internal abstract fun getScreenContainer(): Component

    protected fun restoreFocus() {
        activeScreen()?.activeWindow?.takeFocus() ?: getScreenContainer().takeFocus()
    }

    protected fun createRootComponent(): Component {
        val rootContainer = stacked()
        rootContainer.add(getScreenContainer())
        rootContainer.add(logPanel.maybe { logPanelOpen })
        rootContainer.add(notificationPanel.maybe { notificationPanelOpen })
        rootContainer.add(palettePanel.maybe { showPalette })
        rootContainer.add(perfPanel.maybe { showPerfOverlay })
        return renderer(child = rootContainer) {
            var el = getScreenContainer().render()
            buildActiveToastsElement()?.let { el = dbox(el, it) }
            val anyModal = isDialogActive() || showHelp || showPalette ||
                    logPanelOpen || notificationPanelOpen || confirmingQuit
            if (anyModal) el = el.dim()
            buildActiveDialogElement()?.let { el = dbox(el, it) }
            if (confirmingQuit) el = dbox(el, buildConfirmQuitElement())
            if (showHelp) el = dbox(el, buildHelpElement())
            if (showPalette) el = dbox(el, palettePanel.render())
            if (logPanelOpen) el = dbox(el, logPanel.render())
            if (notificationPanelOpen) el = dbox(el, notificationPanel.render())
            if (showPerfOverlay) el = dbox(el, perfPanel.render())
            el
        }.catchEvent { event ->
            val anyModalExceptPanels = isDialogActive() || showHelp || confirmingQuit
            if (anyModalExceptPanels) {
                handleEvent(event)
            } else {
                false
            }
        }.postCatchEvent { event -> handleEvent(event) }
    }

    private fun handleEvent(event: FtxUIEvent): Boolean = when {
        isDialogActive() -> handleActiveDialogInput(event)
        showHelp -> {
            if (event.isKey(Key.Escape) || event.isKey(Key.Return) || isChar(event, "?")) {
                showHelp = false
            }
            true
        }

        confirmingQuit -> {
            when {
                event.isKey(Key.Return) || isChar(event, "y") || isChar(event, "Y") -> {
                    confirmingQuit = false; app.exit()
                }

                else -> {
                    confirmingQuit = false
                }
            }
            true
        }

        else -> {
            if (handleExtraInput(event)) return true
            val handled = if (logPanelOpen || notificationPanelOpen) false else handleScreenInput(event)
            when {
                !handled && confirmOnQuit && event.isKey(Key.CtrlC) -> {
                    confirmingQuit = true; true
                }

                !handled && event.isKey(Key.CtrlAltP) -> {
                    showPerfOverlay = !showPerfOverlay; true
                }

                !handled && isChar(event, "?") -> {
                    showHelp = true; true
                }

                !handled && event.isKey(Key.CtrlP) -> {
                    showPalette = !showPalette
                    if (showPalette) {
                        palettePanel.takeFocus()
                    } else {
                        restoreFocus()
                    }
                    true
                }

                !handled && event.isKey(Key.CtrlN) -> {
                    notificationPanelOpen = !notificationPanelOpen
                    if (notificationPanelOpen) {
                        logPanelOpen = false
                        notificationPanel.takeFocus()
                    } else {
                        restoreFocus()
                    }
                    true
                }

                !handled && event.isKey(Key.CtrlL) -> {
                    logPanelOpen = !logPanelOpen
                    if (logPanelOpen) {
                        notificationPanelOpen = false
                        logPanel.takeFocus()
                    } else {
                        restoreFocus()
                    }
                    true
                }

                else -> handled
            }
        }
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

}

internal fun isChar(event: FtxUIEvent, char: String) =
    event is FtxUIEvent.Character && event.character == char

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
                val onDismiss = dialog.onDismiss
                dismiss()
                onDismiss()
            }

        is Dialog.Confirm -> when {
            event.isKey(Key.Return) || isChar(event, "y") || isChar(event, "Y") -> {
                val onConfirm = dialog.onConfirm
                dismiss()
                onConfirm()
            }

            event.isKey(Key.Escape) || isChar(event, "n") || isChar(event, "N") -> {
                val onCancel = dialog.onCancel
                dismiss()
                onCancel()
            }
        }

        is Dialog.Prompt -> when {
            event.isKey(Key.Return) -> {
                val onSubmit = dialog.onSubmit
                val input = getPromptInput()
                dismiss()
                onSubmit(input)
            }

            event.isKey(Key.Escape) -> {
                val onCancel = dialog.onCancel
                dismiss()
                onCancel()
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
