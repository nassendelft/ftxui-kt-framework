package nl.ncaj.ftxui.framework

import nl.ncaj.ftxui.Component
import nl.ncaj.ftxui.Terminal

fun AppContext.responsiveHorizontal(
    breakpoint: Int = 120,
    narrow: () -> Component,
    wide: () -> Component,
): Component = if (terminalSize.width >= breakpoint) wide() else narrow()

fun AppContext.responsiveVertical(
    breakpoint: Int = 30,
    short: () -> Component,
    tall: () -> Component,
): Component = if (terminalSize.height >= breakpoint) tall() else short()
