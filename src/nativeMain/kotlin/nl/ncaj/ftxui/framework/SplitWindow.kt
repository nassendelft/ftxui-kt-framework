package nl.ncaj.ftxui.framework

import kotlin.concurrent.Volatile
import nl.ncaj.ftxui.*

open class SplitWindow<L, R>(
    private val left: Window<L>,
    private val right: Window<R>,
    private val leftTitle: String = "",
    private val rightTitle: String = "",
    override val extraHeader: WindowSection? = null,
    override val extraFooter: WindowSection? = null,
) : Window<Pair<L, R>> {

    enum class Focus { LEFT, RIGHT }

    @Volatile var focus: Focus = Focus.LEFT
        private set

    override fun render(state: Pair<L, R>): Component {
        val leftComp = left.render(state.first)
        val rightComp = right.render(state.second)
        return renderer {
            val leftEl = leftComp.render()
            val rightEl = rightComp.render()
            val l = if (leftTitle.isEmpty()) leftEl else leftEl.window(text(" $leftTitle ").bold())
            val r = if (rightTitle.isEmpty()) rightEl else rightEl.window(text(" $rightTitle ").bold())
            val leftFinal = if (focus == Focus.LEFT) l else l.dim()
            val rightFinal = if (focus == Focus.RIGHT) r else r.dim()
            wrapWithDecorations(hbox(leftFinal.flex(), rightFinal.flex()))
        }
    }

    override fun onInput(event: FtxUIEvent): Boolean {
        val focused = if (focus == Focus.LEFT) left else right
        if (focused.onInput(event)) return true
        return when {
            event.isKey(Key.Tab) -> { focus = if (focus == Focus.LEFT) Focus.RIGHT else Focus.LEFT; true }
            event.isKey(Key.TabReverse) -> { focus = if (focus == Focus.RIGHT) Focus.LEFT else Focus.RIGHT; true }
            else -> false
        }
    }
}
