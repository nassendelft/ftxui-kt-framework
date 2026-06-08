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

    private val editorWindow = TextEditorWindow(showLineNumbers = true, onContentChange = { text ->
        Logger.debug("Editor: ${text.lines().size} lines")
    })

    override val activeWindow get() = editorWindow

    override val globalShortcuts get() = listOf(
        Shortcut(Key.CtrlS, "^S Save", description = "Show current content length", showInStatusBar = true) {
            val lines = editorWindow.getText().lines().size
            navigator.notify("$lines line(s) — Ctrl+Z undo  Ctrl+Y redo", Toast.SHORT, Toast.Type.Success)
        },
    )

    override fun buildContent(state: TextEditorState): Component = editorWindow.render(state)

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

    private val picker = FilePickerWindow(
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

    private val fruitList = ListWindow<String>(
        renderItem = { name, focused ->
            if (focused) text("  $name").inverted() else text("  $name")
        },
        renderHeader = { name -> hbox(text("  $name").bold(), filler()) },
    )

    private val vegList = ListWindow<String>(
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

    private val pager = PagerWindow()
    private val pagerState = PagerState(listOf(
        "Dashboard Window", "──────────────────",
        "Four cells in a 2×2 grid.",
        "Tab / Shift+Tab cycles focus.",
        "Focused cell receives key input.",
        "Each cell wraps any Window type.",
    ))

    private val dashboard = DashboardWindow(
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

    private val pagedList = PaginatedListWindow<String>(
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

    private val progressWindow = StepProgressWindow()

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

class ResponsiveDemoScreen : Screen<Unit, Nothing>() {
    override val viewModel = object : ViewModel<Unit, Nothing>() {
        override val state = MutableStateFlow(Unit)
        override fun onEvent(event: Nothing) {}
    }

    private val leftList = ListWindow<String>(
        renderItem = { s, focused -> if (focused) text("  $s").inverted() else text("  $s") },
        renderHeader = { s -> hbox(text("  $s").bold(), filler()) },
    )
    private val rightPager = PagerWindow()
    private val splitView = SplitWindow(leftList, rightPager, "Items", "Details")

    private val items = ListState(
        (1..10).map { i -> ListEntry.Item("Item $i") as ListEntry<String> }
    )
    private val detail = PagerState(listOf(
        "Resize your terminal to test responsive layout.",
        "",
        "≥ 100 columns → split view (list + pager)",
        "< 100 columns → single list view",
        "",
        "Current width shown in status bar.",
    ))

    override val activeWindow: Window<*> get() = if (Terminal.size().dimx >= 100) splitView else leftList

    override fun buildContent(state: Unit): Component = responsive(breakpoint = 100,
        narrow = { leftList.render(items) },
        wide   = { splitView.render(items to detail) },
    )

    override fun buildStatusBar(state: Unit): Element {
        val w = Terminal.size().dimx
        val mode = if (w >= 100) "wide (split)" else "narrow (single)"
        return hbox(text(" "), text("width: $w  mode: $mode").dim(), filler())
    }
}
