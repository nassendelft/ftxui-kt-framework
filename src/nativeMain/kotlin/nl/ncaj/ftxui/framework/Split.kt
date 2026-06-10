package nl.ncaj.ftxui.framework

import nl.ncaj.ftxui.*

fun ScreenContext.split(
    left: Component,
    right: Component,
    leftTitle: String = "",
    rightTitle: String = "",
    style: SplitStyle = SplitStyle()
): Component {
    val container = horizontal(left, right)
    
    return container.decorateRender {
        val leftEl = left.render()
        val rightEl = right.render()
        val activeFg = style.activeTitleForeground.or(Theme.current.accent)
        
        val l = if (leftTitle.isEmpty()) leftEl
                else if (left.focused) leftEl.window(text(" $leftTitle ").color(activeFg).bold())
                else leftEl.window(text(" $leftTitle ").let { t -> style.inactiveTitleForeground?.let { t.color(it) } ?: t })
                
        val r = if (rightTitle.isEmpty()) rightEl
                else if (right.focused) rightEl.window(text(" $rightTitle ").color(activeFg).bold())
                else rightEl.window(text(" $rightTitle ").let { t -> style.inactiveTitleForeground?.let { t.color(it) } ?: t })
                
        val activeBs = style.activeBorderStyle.or(Theme.current.focusedBorderStyle)
        val inactiveBs = style.borderStyle.or(Theme.current.borderStyle)
        
        val leftFinal = if (left.focused) {
            if (leftTitle.isEmpty()) l.borderStyled(activeBs) else l
        } else {
            (if (leftTitle.isEmpty() && style.borderStyle != null) l.borderStyled(inactiveBs) else l).dim()
        }
        
        val rightFinal = if (right.focused) {
            if (rightTitle.isEmpty()) r.borderStyled(activeBs) else r
        } else {
            (if (rightTitle.isEmpty() && style.borderStyle != null) r.borderStyled(inactiveBs) else r).dim()
        }
        
        hbox(leftFinal.flex(), rightFinal.flex())
    }
}
