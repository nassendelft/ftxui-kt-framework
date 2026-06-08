package nl.ncaj.ftxui.framework

import kotlin.concurrent.Volatile

class UndoRedoStack<T>(initial: T, private val maxSize: Int = 100) {

    private val past = ArrayDeque<T>()
    private val future = ArrayDeque<T>()

    @Volatile private var _current: T = initial

    val current: T get() = _current
    val canUndo: Boolean get() = past.isNotEmpty()
    val canRedo: Boolean get() = future.isNotEmpty()

    fun push(value: T) {
        if (value == _current) return
        past.addLast(_current)
        if (past.size > maxSize) past.removeFirst()
        future.clear()
        _current = value
    }

    fun undo(): T {
        if (past.isEmpty()) return _current
        future.addFirst(_current)
        _current = past.removeLast()
        return _current
    }

    fun redo(): T {
        if (future.isEmpty()) return _current
        past.addLast(_current)
        _current = future.removeFirst()
        return _current
    }

    fun reset(value: T) {
        past.clear()
        future.clear()
        _current = value
    }
}
