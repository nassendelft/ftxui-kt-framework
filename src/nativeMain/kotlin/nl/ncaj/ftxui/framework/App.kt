package nl.ncaj.ftxui.framework

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

interface AppContext {
    val preferences: Preferences
    fun requestRedraw()

    /** Runs [action] on the UI loop after the current frame has been drawn. */
    fun post(action: () -> Unit)
    val terminalSize: Dimension
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
    initialScreen: Screen,
    confirmOnQuit: Boolean = false,
    enableCtrlZ: Boolean = false,
) {
    val app = FtxUIApp.fullscreen()
    if (enableCtrlZ) app.forceHandleCtrlZ(false)
    val runner = AppRunner(name, app, confirmOnQuit = confirmOnQuit)
    runner.start(initialScreen)
}

internal class AppRunner(
    name: String,
    app: FtxUIApp,
    confirmOnQuit: Boolean = false,
) : BaseAppRunner(name, app, confirmOnQuit), Navigator {

    private class ScreenEntry(val screen: Screen, val component: Component, val tabIndex: Int)
    private class ToastData(val toast: Toast, @Volatile var progress: Float = 0f)

    private val stack = ArrayDeque<ScreenEntry>()
    private val tabSelector = IntState(0)
    private val tabContainer = tab(tabSelector)
    private var totalComponentsAdded = 0

    @Volatile private var activeDialog: Dialog? = null
    @Volatile private var promptInput: String = ""
    @Volatile private var activeToasts: List<ToastData> = emptyList()
    @Volatile private var notificationLog: List<NotificationRecord> = emptyList()

    override val currentScreen: Screen? get() = stack.lastOrNull()?.screen
    override fun getScreenContainer(): Component = tabContainer

    override fun activeScreen(): Screen? = currentScreen
    override fun activeStackSize(): Int = stack.size
    override fun activeNotificationLog(): List<NotificationRecord> = notificationLog
    override fun isDialogActive(): Boolean = activeDialog != null
    override fun extraPerfLines(): List<String> = listOf("Stack: ${stack.size}")

    override fun buildContentElement(): Element =
        tabContainer.render()

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

    override fun push(screen: Screen) {
        val component = screen.build(this, this)
        tabContainer.add(component)
        val index = totalComponentsAdded++
        stack.addLast(ScreenEntry(screen, component, index))
        tabSelector.value = index
        app.requestAnimationFrame()
    }

    override fun pop() {
        if (stack.isEmpty()) return
        stack.removeLast()
        if (stack.isEmpty()) {
            app.exit()
        } else {
            tabSelector.value = stack.last().tabIndex
            app.requestAnimationFrame()
        }
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

    fun start(initialScreen: Screen) {
        app.forceHandleCtrlC(!confirmOnQuit)
        push(initialScreen)
        val root = createRootComponent()
        app.loop(root)
        preferences.save()
        scope.cancel()
    }
}
