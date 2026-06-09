import nl.ncaj.ftxui.framework.*
import kotlin.concurrent.Volatile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import nl.ncaj.ftxui.*
import kotlin.time.Duration.Companion.milliseconds

// =============================================================================
// Screen — Text Editor demo
// =============================================================================

class TextEditorDemoScreen : Screen<TextEditorState, Nothing>() {
    override val viewModel = object : ViewModel<TextEditorState, Nothing>() {
        override val state = MutableStateFlow(TextEditorState(INITIAL_TEXT))
        override fun onEvent(event: Nothing) {}
    }

    private lateinit var navigator: Navigator

    private val editorWindow = TextEditorView(
        showLineNumbers = true,
        onContentChange = { text ->
            Logger.debug("Editor: ${text.lines().size} lines")
        },
        onStateChange = { newState ->
            (viewModel.state as MutableStateFlow).value = newState
        }
    )

    override val activeWindow get() = editorWindow

    override val globalShortcuts get() = listOf(
        Shortcut(Key.CtrlS, "^S Save", description = "Show current content length", showInStatusBar = true) {
            val lines = editorWindow.getText().lines().size
            navigator.notify("$lines line(s) — Ctrl+Z undo  Ctrl+Y redo", Toast.SHORT, Toast.Type.Success)
        },
    )

    override fun buildContent(state: TextEditorState): Component = editorWindow.render(state)

    override fun buildStatusBar(state: TextEditorState): Element {
        val baseStatusBar = super.buildStatusBar(state)
        val cursorInfo = "  Ln ${state.cursorLine + 1}, Col ${state.cursorCol + 1}  "
        return hbox(baseStatusBar, filler(), text(cursorInfo).dim())
    }

    override fun handleInput(event: FtxUIEvent, navigator: Navigator): Boolean {
        this.navigator = navigator
        return super.handleInput(event, navigator)
    }

    companion object {
        val INITIAL_TEXT = """
            Welcome to TextEditorWindow!

            Type to edit. Use arrow keys to navigate.
            Ctrl+Z = undo  |  Ctrl+Y = redo
            Ctrl+S = show line count (demo save)

            Features:
              - Multi-line text with cursor rendering
              - Home / End / Page Up / Page Down
              - Backspace, Delete, Enter (line split/merge)
              - 100-snapshot undo/redo history
              - Optional line numbers (shown here)
        """.trimIndent()
    }
}

// =============================================================================
// Screen — File Picker demo
// =============================================================================

class FilePickerDemoScreen : Screen<FilePickerState, Nothing>() {
    override val viewModel = object : ViewModel<FilePickerState, Nothing>() {
        override val state = MutableStateFlow(FilePickerState())
        override fun onEvent(event: Nothing) {}
    }

    private lateinit var navigator: Navigator

    private val picker = FilePickerView(
        initialPath = ".",
        onFileSelected = { path ->
            navigator.notify("Selected: $path", Toast.LONG, Toast.Type.Success)
            navigator.pop()
        },
    )

    override val activeWindow get() = picker

    override fun buildContent(state: FilePickerState): Component = picker.render(state)

    override fun handleInput(event: FtxUIEvent, navigator: Navigator): Boolean {
        this.navigator = navigator
        return super.handleInput(event, navigator)
    }
}

// =============================================================================
// Screen — Dashboard demo
// =============================================================================

class DashboardDemoScreen : Screen<Unit, Nothing>() {
    override val viewModel = object : ViewModel<Unit, Nothing>() {
        override val state = MutableStateFlow(Unit)
        override fun onEvent(event: Nothing) {}
    }

    private val fruitList = ListView<String>(
        renderItem = { name, focused ->
            if (focused) text("  $name").inverted() else text("  $name")
        },
        renderHeader = { name -> hbox(text("  $name").bold(), filler()) },
    )

    private val vegList = ListView<String>(
        renderItem = { name, focused ->
            if (focused) text("  $name").inverted() else text("  $name")
        },
        renderHeader = { name -> hbox(text("  $name").bold(), filler()) },
    )

    private val fruitState = ListState(
        listOf("Apple", "Banana", "Cherry", "Dragon Fruit", "Elderberry", "Fig")
            .map { ListEntry.Item(it) as ListEntry<String> }
    )

    private val vegState = ListState(
        listOf("Artichoke", "Broccoli", "Carrot", "Daikon", "Eggplant")
            .map { ListEntry.Item(it) as ListEntry<String> }
    )

    private val pager = PagerView()
    private val pagerState = PagerState(listOf(
        "Dashboard Window", "──────────────────",
        "Four cells in a 2×2 grid.",
        "Tab / Shift+Tab cycles focus.",
        "Focused cell receives key input.",
        "Each cell wraps any Window type.",
    ))

    private val dashboard = DashboardView(
        columns = 2,
        cells = listOf(
            DashboardCell(
                title = "Fruits",
                render = { fruitList.render(fruitState) },
                onInput = { event -> fruitList.onInput(event) },
            ),
            DashboardCell(
                title = "Vegetables",
                render = { vegList.render(vegState) },
                onInput = { event -> vegList.onInput(event) },
            ),
            DashboardCell(
                title = "Notes",
                render = { pager.render(pagerState) },
                onInput = { event -> pager.onInput(event) },
            ),
            DashboardCell(
                title = "Stats",
                render = {
                    renderer {
                        vbox(
                            hbox(text("  Fruits:     "), text("6").color(Theme.current.success)),
                            hbox(text("  Vegetables: "), text("5").color(Theme.current.accent)),
                            hbox(text("  Total:      "), text("11").bold()),
                            filler(),
                        )
                    }
                },
            ),
        ),
    )

    override val activeWindow get() = dashboard

    override fun buildContent(state: Unit): Component = dashboard.render(0)
}

// =============================================================================
// Screen — Paginated list demo
// =============================================================================

class PaginatedListDemoScreen : Screen<Unit, Nothing>() {
    override val viewModel = object : ViewModel<Unit, Nothing>() {
        override val state = MutableStateFlow(Unit)
        override fun onEvent(event: Nothing) {}
    }

    private lateinit var navigator: Navigator

    private val pagedList = PaginatedListView<String>(
        pageSize = 25,
        loadThreshold = 5,
        loadPage = { offset, limit ->
            delay(300.milliseconds)
            val total = 200
            (offset until minOf(offset + limit, total)).map { i ->
                ListEntry.Item("Item #${(i + 1).toString().padStart(3)} — batch ${offset / limit + 1}")
            }
        },
        renderItem = { name, focused ->
            if (focused) text("  $name").inverted() else text("  $name")
        },
        renderHeader = { name -> hbox(text("  $name").bold(), filler()) },
        onSelect = { item -> navigator.notify("Selected: ${item.data}", Toast.SHORT) },
        onTotalCount = { 200 },
    )

    override val activeWindow get() = pagedList

    override fun buildContent(state: Unit): Component = pagedList.render(Unit)

    override fun handleInput(event: FtxUIEvent, navigator: Navigator): Boolean {
        this.navigator = navigator
        return super.handleInput(event, navigator)
    }
}

// =============================================================================
// Screen — Step progress demo
// =============================================================================

sealed class StepDemoEvent {
    data object Start : StepDemoEvent()
    data object Reset : StepDemoEvent()
}

class StepDemoViewModel : ViewModel<StepProgressState, StepDemoEvent>() {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val _state = MutableStateFlow(StepProgressState(INITIAL_STEPS))
    override val state: StateFlow<StepProgressState> = _state
    @Volatile private var running = false

    override fun onEvent(event: StepDemoEvent) {
        when (event) {
            StepDemoEvent.Start -> if (!running) runPipeline()
            StepDemoEvent.Reset -> { running = false; _state.value = StepProgressState(INITIAL_STEPS) }
        }
    }

    private fun runPipeline() {
        running = true
        scope.launch {
            val spinnerJob = launch {
                var tick = 0
                while (running) {
                    delay(80.milliseconds)
                    if (_state.value.steps.any { it.status == StepStatus.Running }) {
                        _state.value = _state.value.copy(spinnerTick = ++tick)
                    }
                }
            }
            for (i in INITIAL_STEPS.indices) {
                if (!running) break
                updateStep(i, StepStatus.Running)
                delay(1200.milliseconds)
                val success = i != 2  // simulate failure at step index 2
                updateStep(i, if (success) StepStatus.Done else StepStatus.Failed,
                    output = listOf("  exit code: ${if (success) 0 else 1}", "  duration: 1.2s"))
                if (!success) break
            }
            spinnerJob.cancel()
            running = false
        }
    }

    private fun updateStep(idx: Int, status: StepStatus, output: List<String> = emptyList()) {
        val steps = _state.value.steps.toMutableList()
        steps[idx] = steps[idx].copy(status = status, output = output)
        _state.value = _state.value.copy(steps = steps)
    }

    companion object {
        val INITIAL_STEPS = listOf(
            ProgressStep("Install dependencies",  StepStatus.Pending),
            ProgressStep("Compile sources",       StepStatus.Pending),
            ProgressStep("Run tests",             StepStatus.Pending),  // will fail
            ProgressStep("Package artifact",      StepStatus.Pending),
            ProgressStep("Deploy to staging",     StepStatus.Pending),
        )
    }
}

class StepProgressDemoScreen : Screen<StepProgressState, StepDemoEvent>() {
    override val viewModel = StepDemoViewModel()

    private lateinit var navigator: Navigator

    override val globalShortcuts get() = listOf(
        Shortcut(Key.Return, "Enter Start", description = "Start the pipeline", showInStatusBar = true) {
            viewModel.onEvent(StepDemoEvent.Start)
        },
        Shortcut(Key.CtrlR, "^R Reset", description = "Reset all steps") {
            viewModel.onEvent(StepDemoEvent.Reset)
        },
    )

    private val progressWindow = StepProgressView()

    override val activeWindow get() = progressWindow

    override fun buildContent(state: StepProgressState): Component = progressWindow.render(state)

    override fun handleInput(event: FtxUIEvent, navigator: Navigator): Boolean {
        this.navigator = navigator
        return super.handleInput(event, navigator)
    }
}

// =============================================================================
// Screen — Responsive layout demo
// =============================================================================

data class ResponsiveDemoState(
    val listState: ListState<String>,
    val detailState: PagerState
)

class ResponsiveDemoViewModel : ViewModel<ResponsiveDemoState, Nothing>() {
    private val initialItems = (1..10).map { i -> ListEntry.Item("Item $i") as ListEntry<String> }

    private val _state = MutableStateFlow(ResponsiveDemoState(
        listState = ListState(initialItems, focusedIndex = 0),
        detailState = PagerState(getDetails("Item 1"))
    ))
    override val state: StateFlow<ResponsiveDemoState> = _state

    override fun onEvent(event: Nothing) {}

    fun updateListState(newListState: ListState<String>) {
        val selectedIndex = newListState.focusedIndex
        val selectedItem = newListState.entries.getOrNull(selectedIndex) as? ListEntry.Item<String>
        val itemName = selectedItem?.data ?: "none"

        _state.value = _state.value.copy(
            listState = newListState,
            detailState = PagerState(getDetails(itemName))
        )
    }

    private fun getDetails(itemName: String) = listOf(
        "Details for $itemName:",
        "───────────────────────────────",
        "This is dynamically synced!",
        "Selected via state hoisting.",
        "",
        "Resize terminal to test split.",
        "≥ 100 columns → split view",
        "< 100 columns → single list",
        "",
        "Width: ${Terminal.size().dimx} columns."
    )
}

class ResponsiveDemoScreen : Screen<ResponsiveDemoState, Nothing>() {
    override val viewModel = ResponsiveDemoViewModel()

    private val leftList = ListView<String>(
        renderItem = { s, focused -> if (focused) text("  $s").inverted() else text("  $s") },
        renderHeader = { s -> hbox(text("  $s").bold(), filler()) },
        onStateChange = { viewModel.updateListState(it) }
    )
    private val rightPager = PagerView()
    private val splitView = SplitView(leftList, rightPager, "Items", "Details")

    override val activeWindow: InputReceiver get() = if (Terminal.size().dimx >= 100) splitView else leftList

    override fun buildContent(state: ResponsiveDemoState): Component = responsive(breakpoint = 100,
        narrow = { leftList.render(state.listState) },
        wide   = { splitView.render(leftList.render(state.listState), rightPager.render(state.detailState)) },
    )

    override fun buildStatusBar(state: ResponsiveDemoState): Element {
        val w = Terminal.size().dimx
        val mode = if (w >= 100) "wide (split)" else "narrow (single)"
        return hbox(text(" "), text("width: $w  mode: $mode").dim(), filler())
    }
}
