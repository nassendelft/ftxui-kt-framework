package nl.ncaj.ftxui.framework

import kotlinx.coroutines.*
import nl.ncaj.ftxui.*
import kotlin.concurrent.Volatile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

data class Tab(
    val label: String,
    val initialScreen: () -> Screen,
)

fun runTabApp(
    tabs: List<Tab>,
    confirmOnQuit: Boolean = false,
    enableCtrlZ: Boolean = false,
    tabBarStyle: TabBarStyle = TabBarStyle(),
) {
    require(tabs.isNotEmpty()) { "TabApp requires at least one tab" }
    val app = FtxUIApp.fullscreen()
    if (enableCtrlZ) app.forceHandleCtrlZ(false)
    val runner = TabAppRunner(app, tabs, confirmOnQuit, tabBarStyle)
    runner.start()
}

// ---------------------------------------------------------------------------
// Per-tab context (navigator + overlays)
// ---------------------------------------------------------------------------

private class TabToastData(val toast: Toast, @Volatile var progress: Float = 0f)

internal class TabContext(
    val label: String,
    initialScreen: () -> Screen,
    private val requestFrame: () -> Unit,
) : Navigator {

    private class ScreenEntry(val screen: Screen, val component: Component, val tabIndex: Int)

    private val stack = ArrayDeque<ScreenEntry>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val tabSelector = IntState(0)
    val screensContainer = tab(tabSelector)
    private var totalComponentsAdded = 0

    @Volatile var activeDialog: Dialog? = null
        private set
    @Volatile private var promptInput: String = ""
    @Volatile private var activeToasts: List<TabToastData> = emptyList()
    @Volatile var notificationLog: List<NotificationRecord> = emptyList()
        private set

    override val currentScreen: Screen? get() = stack.lastOrNull()?.screen
    val stackSize: Int get() = stack.size

    var onExit: (() -> Unit)? = null

    init {
        push(initialScreen())
    }

    override fun push(screen: Screen) {
        val context = object : ScreenContext {
            override val navigator: Navigator get() = this@TabContext
            override fun requestRedraw() {
                requestFrame()
            }
        }
        val component = screen.build(context)
        screensContainer.add(component)
        val index = totalComponentsAdded++
        stack.addLast(ScreenEntry(screen, component, index))
        tabSelector.value = index
        requestFrame()
    }

    override fun pop() {
        if (stack.isEmpty()) return
        stack.removeLast()
        if (stack.isEmpty()) {
            onExit?.invoke()
        } else {
            tabSelector.value = stack.last().tabIndex
            requestFrame()
        }
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
        screensContainer.render()

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
}

// ---------------------------------------------------------------------------
// TabAppRunner
// ---------------------------------------------------------------------------

internal class TabAppRunner(
    app: FtxUIApp,
    tabs: List<Tab>,
    confirmOnQuit: Boolean,
    private val style: TabBarStyle = TabBarStyle(),
) : BaseAppRunner(app, confirmOnQuit) {

    private val contexts: List<TabContext>

    private val tabSelector = IntState(0)
    private val tabsContainer = tab(tabSelector)

    private val activeContext get() = contexts[tabSelector.value]

    init {
        contexts = tabs.map { tab ->
            TabContext(tab.label, tab.initialScreen) { app.requestAnimationFrame() }
        }
        contexts.forEach { ctx ->
            ctx.onExit = {
                if (contexts.all { it.stackSize == 0 }) app.exit()
            }
            tabsContainer.add(ctx.screensContainer)
        }
    }

    override fun getScreenContainer(): Component = tabsContainer

    override fun buildContentElement(): Element = buildTabLayout()
    override fun buildActiveToastsElement(): Element? = activeContext.buildToastsElement()
    override fun buildActiveDialogElement(): Element? = activeContext.buildDialogElement()
    override fun isDialogActive(): Boolean = activeContext.activeDialog != null
    override fun handleActiveDialogInput(event: FtxUIEvent): Boolean = activeContext.handleInput(event)
    override fun handleScreenInput(event: FtxUIEvent): Boolean = activeContext.handleInput(event)
    override fun activeScreen(): Screen? = activeContext.currentScreen
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
        tabSelector.value = (tabSelector.value + dir + contexts.size) % contexts.size
        app.requestAnimationFrame()
    }

    private fun buildTabLayout(): Element {
        val tabBarEl = buildTabBar()
        val contentEl = activeContext.renderActiveScreen()
        return vbox(tabBarEl, contentEl.flex())
    }

    private fun buildTabBar(): Element {
        val activeFg = style.activeTabForeground.or(Theme.current.accent)
        val tabs = contexts.mapIndexed { i, ctx ->
            val active = i == tabSelector.value
            val label = " ${ctx.label} "
            if (active) text(label).color(activeFg).bold().underlined()
            else text(label).let { t -> style.inactiveTabForeground?.let { t.color(it) } ?: t.dim() }
        }
        val bs = style.borderStyle.or(Theme.current.borderStyle)
        val bc = style.borderColor
        return hbox(text(" "), *tabs.toTypedArray(), filler(), text("  ^Tab").dim(), text(" "))
            .let { if (bc != null) it.borderStyled(bs, bc) else it.borderStyled(bs) }
    }
}
