package nl.ncaj.ftxui.framework

import kotlin.concurrent.Volatile
import platform.posix.time

object Logger {
    enum class Level { Debug, Info, Warn, Error }
    data class Entry(val time: String, val level: Level, val message: String)

    private const val MAX = 1000
    @Volatile private var entries: List<Entry> = emptyList()

    fun debug(msg: String) = add(Level.Debug, msg)
    fun info(msg: String)  = add(Level.Info, msg)
    fun warn(msg: String)  = add(Level.Warn, msg)
    fun error(msg: String) = add(Level.Error, msg)

    fun entries(): List<Entry> = entries
    fun clear() { entries = emptyList() }

    private fun add(level: Level, msg: String) {
        val cur = entries
        val e = Entry(currentTimestamp(), level, msg)
        entries = if (cur.size >= MAX) cur.drop(1) + e else cur + e
    }
}

internal fun currentTimestamp(): String {
    val t = time(null) % 86400L
    val h = t / 3600L
    val m = (t % 3600L) / 60L
    val s = t % 60L
    return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
}
