package nl.ncaj.ftxui.framework

import nl.ncaj.ftxui.Component
import nl.ncaj.ftxui.Terminal

fun responsive(
    breakpoint: Int = 120,
    narrow: () -> Component,
    wide: () -> Component,
): Component = if (Terminal.size().dimx >= breakpoint) wide() else narrow()
