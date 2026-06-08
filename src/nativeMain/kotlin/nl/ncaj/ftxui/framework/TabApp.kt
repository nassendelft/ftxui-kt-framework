package nl.ncaj.ftxui.framework

import kotlin.concurrent.Volatile
import kotlinx.coroutines.*
import nl.ncaj.ftxui.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

data class Tab(
    val label: String,
    val initialScreen: () -> Screen<*, *>,
)

fun runTabApp(
    tabs: List<Tab>,
    confirmOnQuit: Boolean = false,
    enableCtrlZ: Boolean = false,
) {
    require(tabs.isNotEmpty()) { "TabApp requires at least one tab" }
    val app = FtxUIApp.fullscreen()
    if (enableCtrlZ) app.forceHandleCtrlZ(false)
    val runner = TabAppRunner(app, tabs, confirmOnQuit)
    runner.start()
}

// ---------------------------------------------------------------------------
// Per-tab context (navigator + overlays)
// ---------------------------------------------------------------------------

private class TabToastData(val toast: Toast, @Volatile var progress: Float = 0f)

internal class TabContext(
    val label: String,
    initialScreen: () -> Screen<*, *>,
    private val requestFrame: () -> Unit,
) : Navigator {

    private class ScreenEntry(val screen: Screen<*, *>, @Volatile var component: Component)

    private val stack = ArrayDeque<ScreenEntry>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var activeCollectionJob: Job? = null

    @Volatile var activeDialog: Dialog? = null
        private set
    @Volatile private var promptInput: String = ""
    @Volatile private var activeToasts: List<TabToastData> = emptyList()
    @Volatile var notificationLog: List<NotificationRecord> = emptyList()
        private set

    override val currentScreen: Screen<*, *>? get() = stack.lastOrNull()?.screen
    val stackSize: Int get() = stack.size

    var onExit: (() -> Unit)? = null

    init {
        push(initialScreen())
    }

    override fun push(screen: Screen<*, *>) {
        @Suppress("UNCHECKED_CAST")
        val typed = screen as Screen<Any?, Any?>
        val component = typed.render(typed.viewModel.state.value)
        stack.addLast(ScreenEntry(screen, component))
        startStateCollection(screen)
    }

    override fun pop() {
        activeCollectionJob?.cancel()
        if (stack.isEmpty()) return
        stack.removeLast()
        if (stack.isEmpty()) onExit?.invoke() else startStateCollection(currentScreen!!)
    }

    override fun showDialog(dialog: Dialog) {
        activeDialog = dialog
        promptInput = ""
        requestFrame()
    }

    override fun dismissDialog() {
        activeDialog = null
        promptInput = ""
        requestFrame()
    }

    override fun notify(message: String, duration: Duration, type: Toast.Type) {
        notificationLog = (listOf(NotificationRecord(message, type, currentTimestamp())) + notificationLog).take(100)
        val entry = TabToastData(Toast(message, type))
        activeToasts = (activeToasts + entry).takeLast(3)
        requestFrame()
        scope.launch {
            val start = TimeSource.Monotonic.markNow()
            val animJob = launch {
                while (true) {
                    delay(16.milliseconds)
                    entry.progress = (start.elapsedNow() / duration).toFloat().coerceIn(0f, 1f)
                    requestFrame()
                }
            }
            delay(duration)
            animJob.cancel()
            activeToasts = activeToasts.filter { it !== entry }
            requestFrame()
        }
    }

    fun renderActiveScreen(): Element =
        stack.lastOrNull()?.component?.render() ?: emptyElement()

    fun handleInput(event: FtxUIEvent): Boolean {
        val dialog = activeDialog
        if (dialog != null) return handleSharedDialogInput(
            event, dialog,
            getPromptInput = { promptInput },
            setPromptInput = { promptInput = it },
            dismiss = { dismissDialog() },
            requestFrame = requestFrame,
        )
        return currentScreen?.handleInput(event, this) ?: false
    }

    fun buildToastsElement(): Element? {
        val toasts = activeToasts
        if (toasts.isEmpty()) return null
        val pills = toasts.map { buildToastPill(it.toast, it.progress) }
        return vbox(
            filler(),
            *pills.map { hbox(filler(), it, text(" ")) }.toTypedArray(),
            text(" "),
        )
    }

    fun buildDialogElement(): Element? =
        activeDialog?.let { buildSharedDialogElement(it, promptInput) }

    fun cancel() { scope.cancel() }

    private fun startStateCollection(screen: Screen<*, *>) {
        activeCollectionJob?.cancel()
        @Suppress("UNCHECKED_CAST")
        val typed = screen as Screen<Any?, Any?>
        activeCollectionJob = scope.launch {
            typed.viewModel.state.collect { state ->
                val top = stack.lastOrNull()
                if (top?.screen === typed) top.component = typed.render(state)
                requestFrame()
            }
        }
    }
}

// ---------------------------------------------------------------------------
// TabAppRunner
// ---------------------------------------------------------------------------

internal class TabAppRunner(
    app: FtxUIApp,
    tabs: List<Tab>,
    confirmOnQuit: Boolean,
) : BaseAppRunner(app, confirmOnQuit) {

    private val contexts: List<TabContext>

    @Volatile private var activeTabIndex: Int = 0

    private val activeContext get() = contexts[activeTabIndex]

    init {
        contexts = tabs.map { tab ->
            TabContext(tab.label, tab.initialScreen) { app.requestAnimationFrame() }
        }
        contexts.forEach { ctx ->
            ctx.onExit = {
                if (contexts.all { it.stackSize == 0 }) app.exit()
            }
        }
    }

    override fun buildContentElement(): Element = buildTabLayout()
    override fun buildActiveToastsElement(): Element? = activeContext.buildToastsElement()
    override fun buildActiveDialogElement(): Element? = activeContext.buildDialogElement()
    override fun isDialogActive(): Boolean = activeContext.activeDialog != null
    override fun handleActiveDialogInput(event: FtxUIEvent): Boolean = activeContext.handleInput(event)
    override fun handleScreenInput(event: FtxUIEvent): Boolean = activeContext.handleInput(event)
    override fun activeScreen(): Screen<*, *>? = activeContext.currentScreen
    override fun activeStackSize(): Int = activeContext.stackSize
    override fun activeNotificationLog(): List<NotificationRecord> = activeContext.notificationLog
    override fun extraPerfLines(): List<String> = listOf("Stack: ${activeContext.stackSize}", "Tabs:  ${contexts.size}")
    override fun extraHelpRows(): List<Element> = listOf(
        hbox(text("  ^Tab         "), text("next tab").dim(), text("  ")),
    )

    override fun handleExtraInput(event: FtxUIEvent): Boolean = when {
        event.isKey(Key.AltH) -> { cycleTab(-1); true }
        event.isKey(Key.AltL) -> { cycleTab(+1); true }
        else -> false
    }

    fun start() {
        if (confirmOnQuit) app.forceHandleCtrlC(false)
        val root = createRootComponent()
        app.loop(root)
        Preferences.save()
        scope.cancel()
        contexts.forEach { it.cancel() }
    }

    private fun cycleTab(dir: Int) {
        activeTabIndex = (activeTabIndex + dir + contexts.size) % contexts.size
        app.requestAnimationFrame()
    }

    private fun buildTabLayout(): Element {
        val tabBarEl = buildTabBar()
        val contentEl = activeContext.renderActiveScreen()
        return vbox(tabBarEl, contentEl.flex())
    }

    private fun buildTabBar(): Element {
        val tabs = contexts.mapIndexed { i, ctx ->
            val active = i == activeTabIndex
            val label = " ${ctx.label} "
            if (active) text(label).color(Theme.current.accent).bold().underlined()
            else text(label).dim()
        }
        return hbox(text(" "), *tabs.toTypedArray(), filler(), text("  ^Tab").dim(), text(" "))
            .borderStyled(BorderStyle.Light)
    }
}
