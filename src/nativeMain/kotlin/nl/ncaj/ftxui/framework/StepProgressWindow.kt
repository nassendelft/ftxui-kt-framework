package nl.ncaj.ftxui.framework

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
)

open class StepProgressWindow(
    override val extraHeader: WindowSection? = null,
    override val extraFooter: WindowSection? = null,
) : Window<StepProgressState> {

    private val spinnerFrames = listOf("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏")

    private var expandedSteps: MutableSet<Int> = mutableSetOf()
    private var selectedStep: Int = 0

    override fun render(state: StepProgressState): Component = renderer {
        buildElement(state)
    }

    override fun onInput(event: FtxUIEvent): Boolean = when {
        event.isKey(Key.ArrowUp) || isChar(event, "k") -> { moveUp(); true }
        event.isKey(Key.ArrowDown) || isChar(event, "j") -> { moveDown(0); true }
        event.isKey(Key.ArrowRight) || isChar(event, "l") -> { expand(selectedStep); true }
        event.isKey(Key.ArrowLeft)  || isChar(event, "h") -> { collapse(selectedStep); true }
        isChar(event, " ") -> { toggleExpand(selectedStep); true }
        else -> false
    }

    private fun moveUp() {
        if (selectedStep > 0) selectedStep--
    }

    private fun moveDown(lastIdx: Int) {
        // called with state.steps.lastIndex; we store selected from outside
        selectedStep++
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

        return wrapWithDecorations(vbox(
            *rows.toTypedArray(),
            separator(),
            overallEl,
        ))
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
        StepStatus.Pending -> "○" to Color.GrayDark
        StepStatus.Running -> spinnerFrames[tick % spinnerFrames.size] to Theme.current.accent
        StepStatus.Done    -> "✓" to Theme.current.success
        StepStatus.Failed  -> "✗" to Theme.current.error
        StepStatus.Skipped -> "—" to Color.GrayLight
    }
}
