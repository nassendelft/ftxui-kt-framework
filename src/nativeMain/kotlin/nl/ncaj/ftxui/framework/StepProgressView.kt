package nl.ncaj.ftxui.framework

import kotlin.concurrent.Volatile
import nl.ncaj.ftxui.*

enum class StepStatus { Pending, Running, Done, Failed, Skipped }

data class ProgressStep(
    val label: String,
    val status: StepStatus,
    val output: List<String> = emptyList(),
    val expanded: Boolean = false,
)

data class StepProgressState(
    val steps: List<ProgressStep>,
    val spinnerTick: Int = 0,
    val selectedStep: Int = 0,
    val expandedSteps: Set<Int> = emptySet()
)

open class StepProgressView(
    private val onStateChange: ((StepProgressState) -> Unit)? = null,
    private val keybindings: StepProgressKeybindings = StepProgressKeybindings(),
    private val style: StepProgressStyle = StepProgressStyle(),
) : InputReceiver {

    private val spinnerFrames = listOf("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏")

    private var expandedSteps: MutableSet<Int> = mutableSetOf()
    private var selectedStep: Int = 0
    @Volatile private var state: StepProgressState = StepProgressState(emptyList())

    fun render(state: StepProgressState): Component {
        this.state = state
        if (onStateChange != null) {
            this.selectedStep = state.selectedStep
            this.expandedSteps = state.expandedSteps.toMutableSet()
        }
        return renderer {
            buildElement(state)
        }
    }

    override fun onInput(event: FtxUIEvent): Boolean {
        val oldSelected = selectedStep
        val oldExpanded = expandedSteps.toSet()
        val lastIdx = state.steps.lastIndex

        val handled = when {
            event.matches(keybindings.moveUpKeys, keybindings.moveUpChars) -> { moveUp(); true }
            event.matches(keybindings.moveDownKeys, keybindings.moveDownChars) -> { moveDown(lastIdx); true }
            event.matches(keybindings.expandKeys, keybindings.expandChars) -> { expand(selectedStep); true }
            event.matches(keybindings.collapseKeys, keybindings.collapseChars) -> { collapse(selectedStep); true }
            isChar(event, " ") -> { toggleExpand(selectedStep); true }
            else -> false
        }

        if (handled) {
            selectedStep = selectedStep.coerceIn(0, maxOf(0, lastIdx))
            if (selectedStep != oldSelected || expandedSteps != oldExpanded) {
                notifyStateChange()
            }
        }
        return handled
    }

    private fun notifyStateChange() {
        val newState = StepProgressState(
            steps = state.steps,
            spinnerTick = state.spinnerTick,
            selectedStep = selectedStep,
            expandedSteps = expandedSteps.toSet()
        )
        onStateChange?.invoke(newState)
    }

    private fun moveUp() {
        if (selectedStep > 0) selectedStep--
    }

    private fun moveDown(lastIdx: Int) {
        if (selectedStep < lastIdx) selectedStep++
    }

    private fun expand(idx: Int) { expandedSteps.add(idx) }
    private fun collapse(idx: Int) { expandedSteps.remove(idx) }
    private fun toggleExpand(idx: Int) {
        if (idx in expandedSteps) expandedSteps.remove(idx) else expandedSteps.add(idx)
    }

    private fun buildElement(state: StepProgressState): Element {
        val steps = state.steps
        selectedStep = selectedStep.coerceIn(0, maxOf(0, steps.lastIndex))

        val rows = mutableListOf<Element>()
        steps.forEachIndexed { idx, step ->
            val isFocused = idx == selectedStep
            rows.add(buildStepRow(step, idx, isFocused, state.spinnerTick))
            if (idx in expandedSteps && step.output.isNotEmpty()) {
                step.output.forEach { line ->
                    rows.add(hbox(text("      "), text(line).dim()))
                }
            }
        }

        val overallStatus = when {
            steps.any { it.status == StepStatus.Failed }  -> StepStatus.Failed
            steps.any { it.status == StepStatus.Running } -> StepStatus.Running
            steps.all { it.status == StepStatus.Done || it.status == StepStatus.Skipped } -> StepStatus.Done
            else -> StepStatus.Pending
        }

        val overallEl = buildOverallStatus(overallStatus, state.spinnerTick)

        return vbox(
            *rows.toTypedArray(),
            separator(),
            overallEl,
        )
    }

    private fun buildStepRow(step: ProgressStep, idx: Int, focused: Boolean, tick: Int): Element {
        val (icon, color) = statusStyle(step.status, tick)
        val expandIcon = when {
            step.output.isEmpty() -> "  "
            idx in expandedSteps  -> "▼ "
            else                  -> "▶ "
        }
        val row = hbox(
            text("  $expandIcon").dim(),
            text("$icon ").color(color),
            text(step.label),
            if (step.status == StepStatus.Skipped) text("  (skipped)").dim() else emptyElement(),
            text("  "),
        )
        return if (focused) row.inverted() else row
    }

    private fun buildOverallStatus(status: StepStatus, tick: Int): Element {
        val (icon, color) = statusStyle(status, tick)
        val label = when (status) {
            StepStatus.Pending -> "Waiting"
            StepStatus.Running -> "In progress"
            StepStatus.Done    -> "Complete"
            StepStatus.Failed  -> "Failed"
            StepStatus.Skipped -> "Skipped"
        }
        return hbox(text("  $icon ").color(color), text(label).color(color), text("  "))
    }

    private fun statusStyle(status: StepStatus, tick: Int): Pair<String, Color> = when (status) {
        StepStatus.Pending -> "○" to style.pendingColor.or(Theme.current.muted)
        StepStatus.Running -> spinnerFrames[tick % spinnerFrames.size] to style.runningColor.or(Theme.current.accent)
        StepStatus.Done    -> "✓" to style.doneColor.or(Theme.current.success)
        StepStatus.Failed  -> "✗" to style.failedColor.or(Theme.current.error)
        StepStatus.Skipped -> "—" to style.skippedColor.or(Theme.current.mutedForeground)
    }
}
