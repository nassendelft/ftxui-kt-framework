package nl.ncaj.ftxui.framework

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import nl.ncaj.ftxui.*

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
                while (true) { delay(100); _state.value = AsyncState.Loading(++tick) }
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

abstract class AsyncScreen<T, E> : Screen<AsyncState<T>, E>() {
    final override fun buildContent(state: AsyncState<T>): Component = when (state) {
        is AsyncState.Loading -> buildLoading(state.tick)
        is AsyncState.Success -> buildLoaded(state.data)
        is AsyncState.Error -> buildError(state.message, state.canRetry)
    }

    protected open fun buildLoading(tick: Int): Component = renderer {
        val frame = listOf("-", "\\", "|", "/")[tick % 4]
        vbox(filler(), hbox(filler(), text("$frame Loading…"), filler()), filler())
    }

    protected open fun buildError(message: String, canRetry: Boolean): Component = renderer {
        val hint = if (canRetry) "  [R] Retry" else ""
        vbox(filler(), hbox(filler(), text("✗ $message$hint").color(Theme.current.error), filler()), filler())
    }

    protected abstract fun buildLoaded(data: T): Component
}
