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
    val selectedStep: Int = 0,
    val expandedSteps: Set<Int> = emptySet()
)

fun AppContext.stepProgress(
    getState: () -> StepProgressState,
    keybindings: StepProgressKeybindings = StepProgressKeybindings(),
    style: StepProgressStyle = StepProgressStyle(),
    onStateChange: ((StepProgressState) -> Unit)? = null
): Component {
    var expandedSteps by mutableStateOf(emptySet<Int>())
    var selectedStep by mutableStateOf(0)

    val spinnerFrames = listOf("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏")

    val statusStyle: (StepStatus, Int) -> Pair<String, Color> = { status, tick ->
        when (status) {
            StepStatus.Pending -> "○" to style.pendingColor.or(Theme.current.muted)
            StepStatus.Running -> spinnerFrames[tick % spinnerFrames.size] to style.runningColor.or(Theme.current.accent)
            StepStatus.Done    -> "✓" to style.doneColor.or(Theme.current.success)
            StepStatus.Failed  -> "✗" to style.failedColor.or(Theme.current.error)
            StepStatus.Skipped -> "—" to style.skippedColor.or(Theme.current.mutedForeground)
        }
    }

    val buildStepRow: (ProgressStep, Int, Boolean, Int) -> Element = { step, idx, focused, tick ->
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
        if (focused) row.inverted() else row
    }

    val buildOverallStatus: (StepStatus, Int) -> Element = { status, tick ->
        val (icon, color) = statusStyle(status, tick)
        val label = when (status) {
            StepStatus.Pending -> "Waiting"
            StepStatus.Running -> "In progress"
            StepStatus.Done    -> "Complete"
            StepStatus.Failed  -> "Failed"
            StepStatus.Skipped -> "Skipped"
        }
        hbox(text("  $icon ").color(color), text(label).color(color), text("  "))
    }

    val base = focusableRenderer { focused ->
        val state = getState()
        val steps = state.steps
        selectedStep = selectedStep.coerceIn(0, maxOf(0, steps.lastIndex))

        val rows = mutableListOf<Element>()
        steps.forEachIndexed { idx, step ->
            val isFocused = idx == selectedStep
            rows.add(buildStepRow(step, idx, isFocused && focused, state.spinnerTick))
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

        vbox(
            *rows.toTypedArray(),
            separator(),
            overallEl,
        )
    }

    return base.catchEvent { event ->
        val state = getState()
        val lastIdx = state.steps.lastIndex
        val oldSelected = selectedStep
        val oldExpanded = expandedSteps

        val handled = when {
            event.matches(keybindings.moveUpKeys, keybindings.moveUpChars) -> {
                if (selectedStep > 0) selectedStep--
                true
            }
            event.matches(keybindings.moveDownKeys, keybindings.moveDownChars) -> {
                if (selectedStep < lastIdx) selectedStep++
                true
            }
            event.matches(keybindings.expandKeys, keybindings.expandChars) -> {
                expandedSteps = expandedSteps + selectedStep
                true
            }
            event.matches(keybindings.collapseKeys, keybindings.collapseChars) -> {
                expandedSteps = expandedSteps - selectedStep
                true
            }
            event.isKey(" ") -> {
                if (selectedStep in expandedSteps) expandedSteps = expandedSteps - selectedStep
                else expandedSteps = expandedSteps + selectedStep
                true
            }
            else -> false
        }

        if (handled) {
            selectedStep = selectedStep.coerceIn(0, maxOf(0, lastIdx))
            if (selectedStep != oldSelected || expandedSteps != oldExpanded) {
                val newState = StepProgressState(
                    steps = state.steps,
                    spinnerTick = state.spinnerTick,
                    selectedStep = selectedStep,
                    expandedSteps = expandedSteps
                )
                onStateChange?.invoke(newState)
            }
        }
        handled
    }
}
