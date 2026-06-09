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

class TextEditorDemoScreen : Screen() {
    private var editorText = INITIAL_TEXT
    private var editorState = TextEditorState(INITIAL_TEXT)
    private var navigator: Navigator? = null
    private lateinit var editorComponent: Component

    override val activeWindow get() = editorComponent

    override val globalShortcuts get() = listOf(
        Shortcut(Key.CtrlS, "^S Save", description = "Show current content length", showInStatusBar = true) {
            val lines = editorText.lines().size
            navigator?.notify("$lines line(s) — Ctrl+Z undo  Ctrl+Y redo", Toast.SHORT, Toast.Type.Success)
        },
    )

    override fun buildContent(context: ScreenContext): Component {
        editorComponent = context.textEditorView(
            content = ::editorText,
            showLineNumbers = true,
            onContentChange = { text ->
                Logger.debug("Editor: ${text.lines().size} lines")
            },
            onStateChange = { newState ->
                editorState = newState
                context.requestRedraw()
            }
        )
        return editorComponent
    }

    override fun buildStatusBar(): Element {
        val baseStatusBar = super.buildStatusBar()
        val cursorInfo = "  Ln ${editorState.cursorLine + 1}, Col ${editorState.cursorCol + 1}  "
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

class FilePickerDemoScreen : Screen() {
    private lateinit var pickerComponent: Component
    private var navigator: Navigator? = null

    override val activeWindow get() = pickerComponent

    override fun buildContent(context: ScreenContext): Component {
        pickerComponent = context.filePickerView(
            initialPath = ".",
            onFileSelected = { path ->
                navigator?.notify("Selected: $path", Toast.LONG, Toast.Type.Success)
                navigator?.pop()
            }
        )
        return pickerComponent
    }

    override fun handleInput(event: FtxUIEvent, navigator: Navigator): Boolean {
        this.navigator = navigator
        return super.handleInput(event, navigator)
    }
}

// =============================================================================
// Screen — Dashboard demo
// =============================================================================

class DashboardDemoScreen : Screen() {
    private lateinit var dashboardComponent: Component

    override val activeWindow get() = dashboardComponent

    override fun buildContent(context: ScreenContext): Component {
        val fruits = listOf("Apple", "Banana", "Cherry", "Dragon Fruit", "Elderberry", "Fig")
            .map { ListEntry.Item(it) }
        val fruitList = context.listView(
            getEntries = { fruits },
            renderItem = { name, focused ->
                if (focused) text("  $name").inverted() else text("  $name")
            },
            renderHeader = { name -> hbox(text("  $name").bold(), filler()) }
        )

        val veggies = listOf("Artichoke", "Broccoli", "Carrot", "Daikon", "Eggplant")
            .map { ListEntry.Item(it) }
        val vegList = context.listView(
            getEntries = { veggies },
            renderItem = { name, focused ->
                if (focused) text("  $name").inverted() else text("  $name")
            },
            renderHeader = { name -> hbox(text("  $name").bold(), filler()) }
        )

        val pagerState = PagerState(listOf(
            "Dashboard Window", "──────────────────",
            "Four cells in a 2×2 grid.",
            "Tab / Shift+Tab cycles focus.",
            "Focused cell receives key input.",
            "Each cell wraps any Window type.",
        ))
        val pager = context.pagerView(
            getState = { pagerState }
        )

        val stats = renderer {
            vbox(
                hbox(text("  Fruits:     "), text("6").color(Theme.current.success)),
                hbox(text("  Vegetables: "), text("5").color(Theme.current.accent)),
                hbox(text("  Total:      "), text("11").bold()),
                filler(),
            )
        }

        dashboardComponent = context.dashboardView(
            columns = 2,
            cells = listOf(
                DashboardCell(
                    title = "Fruits",
                    render = { fruitList }
                ),
                DashboardCell(
                    title = "Vegetables",
                    render = { vegList }
                ),
                DashboardCell(
                    title = "Notes",
                    render = { pager }
                ),
                DashboardCell(
                    title = "Stats",
                    render = { stats }
                )
            )
        )
        return dashboardComponent
    }
}

// =============================================================================
// Screen — Paginated list demo
// =============================================================================

class PaginatedListDemoScreen : Screen() {
    private lateinit var pagedList: Component
    private var navigator: Navigator? = null

    override val activeWindow get() = pagedList

    override fun buildContent(context: ScreenContext): Component {
        pagedList = context.paginatedListView(
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
            onSelect = { item -> navigator?.notify("Selected: ${item.data}", Toast.SHORT) },
            onTotalCount = { 200 },
        )
        return pagedList
    }

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

class StepProgressDemoScreen : Screen() {
    val viewModel = StepDemoViewModel()

    private var navigator: Navigator? = null
    private lateinit var progressComponent: Component

    override val globalShortcuts get() = listOf(
        Shortcut(Key.Return, "Enter Start", description = "Start the pipeline", showInStatusBar = true) {
            viewModel.onEvent(StepDemoEvent.Start)
        },
        Shortcut(Key.CtrlR, "^R Reset", description = "Reset all steps") {
            viewModel.onEvent(StepDemoEvent.Reset)
        },
    )

    override val activeWindow get() = progressComponent

    override fun buildContent(context: ScreenContext): Component {
        GlobalScope.launch {
            viewModel.state.collect {
                context.requestRedraw()
            }
        }
        progressComponent = context.stepProgressView(
            getState = { viewModel.state.value }
        )
        return progressComponent
    }

    override fun handleInput(event: FtxUIEvent, navigator: Navigator): Boolean {
        this.navigator = navigator
        return super.handleInput(event, navigator)
    }
}

// =============================================================================
// Screen — Responsive layout demo
// =============================================================================

class ResponsiveDemoScreen : Screen() {
    private val items = (1..10).map { i -> ListEntry.Item("Item $i") }
    private var selectedItemName = "Item 1"
    private var detailState = PagerState(getDetails("Item 1"))
    private var navigator: Navigator? = null

    private lateinit var leftList: Component
    private lateinit var rightPager: Component
    private lateinit var splitView: Component

    override val activeWindow get() = if (Terminal.size().dimx >= 100) splitView else leftList

    override fun buildContent(context: ScreenContext): Component {
        leftList = context.listView(
            getEntries = { items },
            renderItem = { s, focused -> if (focused) text("  $s").inverted() else text("  $s") },
            renderHeader = { s -> hbox(text("  $s").bold(), filler()) },
            onFocusChange = { idx ->
                val entry = items.getOrNull(idx)
                selectedItemName = entry?.data ?: "none"
                detailState = PagerState(getDetails(selectedItemName))
                context.requestRedraw()
            }
        )

        rightPager = context.pagerView(
            getState = { detailState }
        )

        splitView = context.splitView(
            left = leftList,
            right = rightPager,
            leftTitle = "Items",
            rightTitle = "Details"
        )

        return renderer {
            responsive(breakpoint = 100,
                narrow = { leftList },
                wide   = { splitView },
            ).render()
        }
    }

    override fun buildStatusBar(): Element {
        val w = Terminal.size().dimx
        val mode = if (w >= 100) "wide (split)" else "narrow (single)"
        return hbox(text(" "), text("width: $w  mode: $mode").dim(), filler())
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
