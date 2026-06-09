package nl.ncaj.ftxui.framework

import kotlin.concurrent.Volatile
import nl.ncaj.ftxui.*

open class SplitView(
    private val left: InputReceiver,
    private val right: InputReceiver,
    private val leftTitle: String = "",
    private val rightTitle: String = "",
    private val focusedPane: Focus? = null,
    private val onFocusChanged: ((Focus) -> Unit)? = null,
    private val keybindings: SplitKeybindings = SplitKeybindings(),
    private val style: SplitStyle = SplitStyle(),
) : InputReceiver {

    enum class Focus { LEFT, RIGHT }

    @Volatile var focus: Focus = Focus.LEFT
        private set

    private var leftComponent: Component? = null
    private var rightComponent: Component? = null
    private var containerComponent: Component? = null

    fun render(leftComp: Component, rightComp: Component): Component {
        leftComponent = leftComp
        rightComponent = rightComp

        if (focusedPane != null) {
            focus = focusedPane
        }

        val container = horizontal(leftComp, rightComp)
        containerComponent = container
        container.activeChild = if (focus == Focus.LEFT) leftComp else rightComp

        return container.decorateRender {
            val leftEl = leftComp.render()
            val rightEl = rightComp.render()
            val activeFg = style.activeTitleForeground.or(Theme.current.accent)
            val l = if (leftTitle.isEmpty()) leftEl
                    else if (leftComp.focused) leftEl.window(text(" $leftTitle ").color(activeFg).bold())
                    else leftEl.window(text(" $leftTitle ").let { t ->
                        style.inactiveTitleForeground?.let { t.color(it) } ?: t
                    })
            val r = if (rightTitle.isEmpty()) rightEl
                    else if (rightComp.focused) rightEl.window(text(" $rightTitle ").color(activeFg).bold())
                    else rightEl.window(text(" $rightTitle ").let { t ->
                        style.inactiveTitleForeground?.let { t.color(it) } ?: t
                    })
            val activeBs = style.activeBorderStyle.or(Theme.current.focusedBorderStyle)
            val inactiveBs = style.borderStyle.or(Theme.current.borderStyle)
            val leftFinal = if (leftComp.focused) {
                (if (leftTitle.isEmpty()) l.borderStyled(activeBs) else l).let { it }
            } else {
                (if (leftTitle.isEmpty() && style.borderStyle != null) l.borderStyled(inactiveBs) else l).dim()
            }
            val rightFinal = if (rightComp.focused) {
                (if (rightTitle.isEmpty()) r.borderStyled(activeBs) else r).let { it }
            } else {
                (if (rightTitle.isEmpty() && style.borderStyle != null) r.borderStyled(inactiveBs) else r).dim()
            }
            hbox(leftFinal.flex(), rightFinal.flex())
        }
    }

    override fun onInput(event: FtxUIEvent): Boolean {
        val leftComp = leftComponent
        val rightComp = rightComponent
        val container = containerComponent

        if (container != null && leftComp != null && rightComp != null) {
            val active = container.activeChild
            if (active != null) {
                focus = if (active == leftComp) Focus.LEFT else Focus.RIGHT
            }
        }

        val focused = if (focus == Focus.LEFT) left else right
        if (focused.onInput(event)) return true
        val oldFocus = focus
        val handled = when {
            event.matches(keybindings.focusNextKeys, keybindings.focusNextChars) -> { focus = if (focus == Focus.LEFT) Focus.RIGHT else Focus.LEFT; true }
            event.matches(keybindings.focusPrevKeys, keybindings.focusPrevChars) -> { focus = if (focus == Focus.RIGHT) Focus.LEFT else Focus.RIGHT; true }
            else -> false
        }
        if (handled && focus != oldFocus) {
            if (container != null && leftComp != null && rightComp != null) {
                container.activeChild = if (focus == Focus.LEFT) leftComp else rightComp
            }
            onFocusChanged?.invoke(focus)
        }
        return handled
    }
}
