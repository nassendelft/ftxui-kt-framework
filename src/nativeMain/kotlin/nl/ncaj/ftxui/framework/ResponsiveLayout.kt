package nl.ncaj.ftxui.framework

import nl.ncaj.ftxui.Component
import nl.ncaj.ftxui.Terminal

fun responsiveHorizontal(
    breakpoint: Int = 120,
    narrow: () -> Component,
    wide: () -> Component,
): Component = if (Terminal.size().dimx >= breakpoint) wide() else narrow()

fun responsiveVertical(
    breakpoint: Int = 30,
    short: () -> Component,
    tall: () -> Component,
): Component = if (Terminal.size().dimy >= breakpoint) tall() else short()

