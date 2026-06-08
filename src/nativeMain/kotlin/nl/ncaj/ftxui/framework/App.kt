package nl.ncaj.ftxui.framework

import kotlin.concurrent.Volatile
import kotlinx.coroutines.*
import nl.ncaj.ftxui.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

fun runApp(
    initialScreen: Screen<*, *>,
    confirmOnQuit: Boolean = false,
    enableCtrlZ: Boolean = false,
) {
    val app = FtxUIApp.fullscreen()
    if (enableCtrlZ) app.forceHandleCtrlZ(false)
    val runner = AppRunner(app, confirmOnQuit = confirmOnQuit)
    runner.start(initialScreen)
}

internal class AppRunner(
    app: FtxUIApp,
    confirmOnQuit: Boolean = false,
) : BaseAppRunner(app, confirmOnQuit), Navigator {

    private class ScreenEntry(val screen: Screen<*, *>, @Volatile var component: Component)
    private class ToastData(val toast: Toast, @Volatile var progress: Float = 0f)

    private val stack = ArrayDeque<ScreenEntry>()
    private var activeCollectionJob: Job? = null

    @Volatile private var activeDialog: Dialog? = null
    @Volatile private var promptInput: String = ""
    @Volatile private var activeToasts: List<ToastData> = emptyList()
    @Volatile private var notificationLog: List<NotificationRecord> = emptyList()

    override val currentScreen: Screen<*, *>? get() = stack.lastOrNull()?.screen

    override fun activeScreen(): Screen<*, *>? = currentScreen
    override fun activeStackSize(): Int = stack.size
    override fun activeNotificationLog(): List<NotificationRecord> = notificationLog
    override fun isDialogActive(): Boolean = activeDialog != null
    override fun extraPerfLines(): List<String> = listOf("Stack: ${stack.size}")

    override fun buildContentElement(): Element =
        stack.lastOrNull()?.component?.render() ?: emptyElement()

    override fun buildActiveToastsElement(): Element? {
        val toasts = activeToasts
        if (toasts.isEmpty()) return null
        val pills = toasts.map { buildToastPill(it.toast, it.progress) }
        return vbox(
            filler(),
            *pills.map { hbox(filler(), it, text(" ")) }.toTypedArray(),
            text(" "),
        )
    }

    override fun buildActiveDialogElement(): Element? =
        activeDialog?.let { buildSharedDialogElement(it, promptInput) }

    override fun handleActiveDialogInput(event: FtxUIEvent): Boolean {
        val dialog = activeDialog ?: return false
        return handleSharedDialogInput(
            event, dialog,
            getPromptInput = { promptInput },
            setPromptInput = { promptInput = it },
            dismiss = { dismissDialog() },
            requestFrame = { app.requestAnimationFrame() },
        )
    }

    override fun handleScreenInput(event: FtxUIEvent): Boolean =
        currentScreen?.handleInput(event, this) ?: false

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
        if (stack.isEmpty()) app.exit() else startStateCollection(currentScreen!!)
    }

    override fun showDialog(dialog: Dialog) {
        activeDialog = dialog
        promptInput = ""
        app.requestAnimationFrame()
    }

    override fun dismissDialog() {
        activeDialog = null
        promptInput = ""
        app.requestAnimationFrame()
    }

    override fun notify(message: String, duration: Duration, type: Toast.Type) {
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

    fun start(initialScreen: Screen<*, *>) {
        if (confirmOnQuit) app.forceHandleCtrlC(false)
        push(initialScreen)
        val root = createRootComponent()
        app.loop(root)
        Preferences.save()
        scope.cancel()
    }

    private fun startStateCollection(screen: Screen<*, *>) {
        activeCollectionJob?.cancel()
        @Suppress("UNCHECKED_CAST")
        val typed = screen as Screen<Any?, Any?>
        activeCollectionJob = scope.launch {
            typed.viewModel.state.collect { state ->
                val top = stack.lastOrNull()
                if (top?.screen === typed) top.component = typed.render(state)
                app.requestAnimationFrame()
            }
        }
    }
}
