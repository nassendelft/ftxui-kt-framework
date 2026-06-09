package nl.ncaj.ftxui.framework

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import nl.ncaj.ftxui.*
import kotlin.time.Duration.Companion.milliseconds

sealed class AsyncState<out T> {
    data class Loading(val tick: Int = 0) : AsyncState<Nothing>()
    data class Success<T>(val data: T) : AsyncState<T>()
    data class Error(val message: String, val canRetry: Boolean = false) : AsyncState<Nothing>()
}

abstract class AsyncViewModel<T, E> : ViewModel<AsyncState<T>, E>() {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val _state = MutableStateFlow<AsyncState<T>>(AsyncState.Loading())
    override val state: StateFlow<AsyncState<T>> = _state

    protected fun setLoading() { _state.value = AsyncState.Loading() }
    protected fun setSuccess(data: T) { _state.value = AsyncState.Success(data) }
    protected fun setError(message: String, canRetry: Boolean = false) {
        _state.value = AsyncState.Error(message, canRetry)
    }

    protected fun launchLoad(block: suspend () -> T) {
        scope.launch {
            val animJob = scope.launch {
                var tick = 0
                while (true) { delay(100.milliseconds); _state.value = AsyncState.Loading(++tick) }
            }
            try {
                val result = block()
                animJob.cancel()
                setSuccess(result)
            } catch (e: Exception) {
                animJob.cancel()
                setError(e.message ?: "Unknown error", canRetry = true)
            }
        }
    }
}

abstract class AsyncScreen<T, E> : Screen() {
    abstract val viewModel: AsyncViewModel<T, E>

    final override fun buildContent(context: ScreenContext): Component {
        val tabIndex = IntState(0)
        val tabContainer = tab(tabIndex)

        var tick by context.mutableStateOf(0)
        val loadingComp = renderer {
            buildLoading(tick).render()
        }
        tabContainer.add(loadingComp)

        var errorMessage by context.mutableStateOf("")
        var canRetry by context.mutableStateOf(false)
        val errorComp = renderer {
            buildError(errorMessage, canRetry).render()
        }
        tabContainer.add(errorComp)

        var successCount = 0
        var lastData: T? = null

        GlobalScope.launch {
            viewModel.state.collect { state ->
                when (state) {
                    is AsyncState.Loading -> {
                        tick = state.tick
                        tabIndex.value = 0
                    }
                    is AsyncState.Error -> {
                        errorMessage = state.message
                        canRetry = state.canRetry
                        tabIndex.value = 1
                    }
                    is AsyncState.Success -> {
                        if (state.data != lastData || successCount == 0) {
                            lastData = state.data
                            val loadedComp = with(context) { buildLoaded(state.data) }
                            tabContainer.add(loadedComp)
                            successCount++
                        }
                        tabIndex.value = 1 + successCount
                    }
                }
                context.requestRedraw()
            }
        }

        return tabContainer
    }

    protected open fun buildLoading(tick: Int): Component = renderer {
        val frame = listOf("-", "\\", "|", "/")[tick % 4]
        vbox(filler(), hbox(filler(), text("$frame Loading…"), filler()), filler())
    }

    protected open fun buildError(message: String, canRetry: Boolean): Component = renderer {
        val hint = if (canRetry) "  [R] Retry" else ""
        vbox(filler(), hbox(filler(), text("✗ $message$hint").color(Theme.current.error), filler()), filler())
    }

    protected abstract fun ScreenContext.buildLoaded(data: T): Component
}
