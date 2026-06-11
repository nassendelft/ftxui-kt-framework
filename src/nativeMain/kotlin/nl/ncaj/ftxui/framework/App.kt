package nl.ncaj.ftxui.framework

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.ncaj.ftxui.*
import kotlin.concurrent.Volatile
import kotlin.reflect.KProperty
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

data class Dimension(val width: Int, val height: Int)

class AppContext(
    val preferences: Preferences,
    val navigator: Navigator,
    private val app: FtxUIApp
) {
    fun requestRedraw() = app.requestAnimationFrame()

    /** Runs [action] on the UI loop after the current frame has been drawn. */
    fun post(action: () -> Unit) = app.post(action)
    val terminalSize: Dimension get() = Dimension(Terminal.size().dimx, Terminal.size().dimy)
}

class MutableState<T>(
    private var internalValue: T,
    private val onStateChange: (T) -> Unit
) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>) = internalValue

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        if (internalValue != value) {
            internalValue = value
            onStateChange(value)
        }
    }
}

fun <T> FtxUIApp.mutableStateOf(initial: T): MutableState<T> =
    MutableState(initial) { requestAnimationFrame() }

fun <T> AppContext.mutableStateOf(initial: T): MutableState<T> =
    MutableState(initial) { requestRedraw() }

fun runApp(
    name: String,
    initialComponentBuilder: AppContext.() -> Component,
    confirmOnQuit: Boolean = false,
    enableCtrlZ: Boolean = false,
) {
    val app = FtxUIApp.fullscreen()
    if (enableCtrlZ) app.forceHandleCtrlZ(false)
    val runner = App(name, app, confirmOnQuit = confirmOnQuit)
    val comp = runner.context.initialComponentBuilder()
    runner.start(comp)
}

internal class App(
    private val name: String,
    private val app: FtxUIApp,
    private val confirmOnQuit: Boolean = false,
) {
    val context = AppContext(Preferences(name), Navigator(this), app)

    private class ComponentEntry(
        val component: Component,
        val tabIndex: Int,
        var shortcuts: List<Shortcut> = emptyList()
    )
    private class ToastData(val toast: Toast, @Volatile var progress: Float = 0f)

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val stack = ArrayDeque<ComponentEntry>()
    private val tabSelector = IntState(0)
    private val tabContainer = tab(tabSelector)
    private var totalComponentsAdded = 0

    @Volatile private var activeDialog: Dialog? = null
    @Volatile private var promptInput: String = ""
    @Volatile private var activeToasts: List<ToastData> = emptyList()
    @Volatile private var notificationLog: List<NotificationRecord> = emptyList()

    val currentComponent: Component? get() = stack.lastOrNull()?.component

    private var showHelp by app.mutableStateOf(false)
    private var showPalette by app.mutableStateOf(false)
    private var showPerfOverlay by app.mutableStateOf(false)
    private var confirmingQuit by app.mutableStateOf(false)

    internal var logPanelOpen by app.mutableStateOf(false)
    internal var notificationPanelOpen by app.mutableStateOf(false)

    private val logPanel by lazy {
        context.logPanel(
            isOpen = { logPanelOpen },
            onToggle = { open ->
                logPanelOpen = open
                if (!open) restoreFocus()
            }
        )
    }
    private val notificationPanel by lazy {
        context.notificationPanel(
            isOpen = { notificationPanelOpen },
            onToggle = { open ->
                notificationPanelOpen = open
                if (!open) restoreFocus()
            },
            getLog = { notificationLog }
        )
    }
    private val palettePanel by lazy {
        context.commandPalette(
            isOpen = { showPalette },
            onToggle = { open ->
                showPalette = open
                if (!open) restoreFocus()
            },
            getShortcuts = { getActiveShortcuts() }
        )
    }
    private val perfPanel by lazy {
        context.perfOverlay(
            isVisible = { showPerfOverlay },
            getExtraLines = { listOf("Stack: ${stack.size}") }
        )
    }

    private fun getActiveShortcuts(): List<Shortcut> {
        val entry = stack.lastOrNull() ?: return emptyList()
        val componentShortcuts = context.navigator.getShortcutsForComponent(entry.component)
        return entry.shortcuts.ifEmpty { componentShortcuts }
    }

    private fun restoreFocus() {
        currentComponent?.takeFocus() ?: tabContainer.takeFocus()
    }

    internal fun registerShortcuts(shortcuts: List<Shortcut>) {
        stack.lastOrNull()?.let { entry ->
            entry.shortcuts = shortcuts
            app.requestAnimationFrame()
        }
    }

    internal fun clearShortcuts() {
        stack.lastOrNull()?.let { entry ->
            entry.shortcuts = emptyList()
            app.requestAnimationFrame()
        }
    }

    private fun buildStatusBar(): Element {
        val shortcuts = getActiveShortcuts()
        val visible = shortcuts.filter { it.showInStatusBar }
        val elems = buildList {
            add(text(" "))
            visible.forEachIndexed { i, sc ->
                if (i > 0) add(text("  "))
                add(text(sc.label).dim())
            }
            add(filler())
        }
        return hbox(*elems.toTypedArray())
    }

    private fun createRootComponent(): Component {
        val rootContainer = stacked()
        rootContainer.add(tabContainer)
        rootContainer.add(logPanel.maybe { logPanelOpen })
        rootContainer.add(notificationPanel.maybe { notificationPanelOpen })
        rootContainer.add(palettePanel.maybe { showPalette })
        rootContainer.add(perfPanel.maybe { showPerfOverlay })
        return renderer(child = rootContainer) {
            var el = vbox(
                tabContainer.render().flex(),
                buildStatusBar()
            )
            buildActiveToastsElement()?.let { el = dbox(el, it) }
            val anyModal = activeDialog != null || showHelp || showPalette ||
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
            val anyModalExceptPanels = activeDialog != null || showHelp || confirmingQuit
            if (anyModalExceptPanels) {
                handleEvent(event)
            } else {
                false
            }
        }.postCatchEvent { event -> handleEvent(event) }
    }

    private fun buildActiveToastsElement(): Element? {
        val toasts = activeToasts
        if (toasts.isEmpty()) return null
        val pills = toasts.map { buildToastPill(it.toast, it.progress) }
        return vbox(
            filler(),
            *pills.map { hbox(filler(), it, text(" ")) }.toTypedArray(),
            text(" "),
        )
    }

    private fun buildActiveDialogElement(): Element? =
        activeDialog?.let { buildSharedDialogElement(it, promptInput) }

    private fun handleEvent(event: FtxUIEvent): Boolean = when {
        activeDialog != null -> handleActiveDialogInput(event)
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
            val handled = if (logPanelOpen || notificationPanelOpen) false else handleComponentInput(event)
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

    private fun handleActiveDialogInput(event: FtxUIEvent): Boolean {
        val dialog = activeDialog ?: return false
        return handleSharedDialogInput(
            event, dialog,
            getPromptInput = { promptInput },
            setPromptInput = { promptInput = it },
            dismiss = { dismissDialog() },
            requestFrame = { app.requestAnimationFrame() },
        )
    }

    private fun handleComponentInput(event: FtxUIEvent): Boolean {
        val shortcuts = getActiveShortcuts()
        val shortcut = shortcuts.find { event.isKey(it.key) }
        if (shortcut != null) {
            shortcut.action()
            return true
        }
        if (event.isKey(Key.Escape) || event.isKey(Key.Backspace)) {
            pop()
            return true
        }
        return false
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
        val shortcuts = getActiveShortcuts()
        val rows = buildList {
            shortcuts.forEach { sc ->
                add(hbox(text("  ${sc.label.padEnd(12)} "), text(sc.description).dim(), text("  ")))
            }
            if (stack.size > 1) {
                add(hbox(text("  Esc          "), text("go back").dim(), text("  ")))
            }
            add(hbox(text("  ?            "), text("show this help").dim(), text("  ")))
            add(hbox(text("  ^P           "), text("command palette").dim(), text("  ")))
            add(hbox(text("  ^N           "), text("notification history").dim(), text("  ")))
            add(hbox(text("  ^L           "), text("log viewer").dim(), text("  ")))
        }
        val body = if (rows.isEmpty()) vbox(text("  No shortcuts defined  ")) else vbox(*rows.toTypedArray())
        return body.window(text(" Keyboard Shortcuts ").bold()).clearUnder().hcenter().vcenter()
    }

    internal fun push(component: Component) {
        tabContainer.add(component)
        val index = totalComponentsAdded++
        stack.addLast(ComponentEntry(component, index))
        tabSelector.value = index
        app.requestAnimationFrame()
    }

    internal fun pop() {
        if (stack.isEmpty()) return
        val entry = stack.removeLast()
        context.navigator.removeShortcutsForComponent(entry.component)
        if (stack.isEmpty()) {
            app.exit()
        } else {
            tabSelector.value = stack.last().tabIndex
            app.requestAnimationFrame()
        }
    }

    internal fun showDialog(dialog: Dialog) {
        activeDialog = dialog
        promptInput = ""
        app.requestAnimationFrame()
    }

    internal fun dismissDialog() {
        activeDialog = null
        promptInput = ""
        app.requestAnimationFrame()
    }

    internal fun notify(message: String, duration: Duration, type: Toast.Type) {
        notificationLog = (listOf(NotificationRecord(message, type, currentTimestamp())) + notificationLog).take(100)
        val entry = ToastData(Toast(message, type))
        activeToasts = (activeToasts + entry).takeLast(3)
        app.requestAnimationFrame()
        scope.launch {
            val start = TimeSource.Monotonic.markNow()
            val animJob = launch {
                while (true) {
                    delay(16.milliseconds)
                    entry.progress = (start.elapsedNow() / duration).toFloat().coerceIn(0f, 1f)
                    app.requestAnimationFrame()
                }
            }
            delay(duration)
            animJob.cancel()
            activeToasts = activeToasts.filter { it !== entry }
            app.requestAnimationFrame()
        }
    }

    internal fun start(initialComponent: Component) {
        app.forceHandleCtrlC(!confirmOnQuit)
        push(initialComponent)
        val root = createRootComponent()
        app.loop(root)
        context.preferences.save()
        scope.cancel()
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
