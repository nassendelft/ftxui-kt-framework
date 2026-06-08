package nl.ncaj.ftxui.framework

import kotlinx.coroutines.flow.StateFlow

abstract class ViewModel<S, E> {
    abstract val state: StateFlow<S>
    abstract fun onEvent(event: E)
}
