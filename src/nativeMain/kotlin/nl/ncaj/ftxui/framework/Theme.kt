package nl.ncaj.ftxui.framework

import nl.ncaj.ftxui.Color

open class ThemeColors(
    val accent: Color = Color.Cyan,
    val error: Color = Color.Red,
    val success: Color = Color.Green,
    val warning: Color = Color.Yellow,
    val info: Color = Color.Cyan,
)

object Theme {
    var current: ThemeColors = ThemeColors()

    inline fun <reified T : ThemeColors> ext(): T =
        current as? T ?: error("Theme.current is not ${T::class.simpleName}")
}
