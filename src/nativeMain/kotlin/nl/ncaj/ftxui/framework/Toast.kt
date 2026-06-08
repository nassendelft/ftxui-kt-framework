package nl.ncaj.ftxui.framework

import kotlin.time.Duration.Companion.seconds

data class Toast(
    val message: String,
    val type: Type = Type.Info,
) {
    enum class Type { Info, Success, Warning, Error }

    companion object {
        val SHORT = 2.seconds
        val LONG = 5.seconds
    }
}

data class NotificationRecord(
    val message: String,
    val type: Toast.Type,
    val timestamp: String,
)
