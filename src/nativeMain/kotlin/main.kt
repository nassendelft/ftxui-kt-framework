import nl.ncaj.ftxui.framework.*
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import nl.ncaj.ftxui.*
import kotlin.time.Duration.Companion.milliseconds

fun demoMain() {
    runApp("ftxui-kt-demo", { HomeScreen(navigator, this).build() }, confirmOnQuit = true, enableCtrlZ = true)
}

// =============================================================================
// Shared data
// =============================================================================

data class Fruit(val name: String, val category: String)

// =============================================================================
// Home screen — demo launcher
// =============================================================================

data class MenuItem(val label: String, val description: String = "")

class HomeScreen(private val navigator: Navigator, private val context: AppContext) {
    fun build(): Component = context.run {
        val entries = buildList {
            add(ListEntry.Header(MenuItem("Windows")))
            add(ListEntry.Item(MenuItem("Fruit List",       "Scrollable list with fuzzy search")) { navigator.push(FruitListScreen(navigator, this@run).build()) })
            add(ListEntry.Item(MenuItem("Table View",       "Sortable 2D table  (s = sort  j/k = nav)")) { navigator.push(TableDemoScreen(navigator, this@run).build()) })
            add(ListEntry.Item(MenuItem("Pager",            "Read-only text with search & line numbers")) { navigator.push(PagerDemoScreen(navigator, this@run).build()) })
            add(ListEntry.Item(MenuItem("Split View",       "Two-panel layout with Tab focus")) { navigator.push(SplitDemoScreen(navigator, this@run).build()) })
            add(ListEntry.Item(MenuItem("Tree View",        "Hierarchical tree with expand/collapse")) { navigator.push(TreeDemoScreen(navigator, this@run).build()) })
            add(ListEntry.Item(MenuItem("Async Loading",    "ViewModel with coroutine-based loading")) { navigator.push(AsyncDemoScreen(navigator, this@run).build()) })

            add(ListEntry.Header(MenuItem("Dialogs")))
            add(ListEntry.Item(MenuItem("Alert",            "Informational dismissible overlay")) {
                navigator.showDialog(Dialog.Alert(title = "About", message = "ftxui-kt-framework demo  v0.1"))
            })
            add(ListEntry.Item(MenuItem("Confirm",          "Yes / No confirmation dialog")) {
                navigator.showDialog(Dialog.Confirm(
                    title = "Delete all items?",
                    message = "This action cannot be undone.",
                    onConfirm = { navigator.notify("Deleted (just kidding)", Toast.SHORT, Toast.Type.Success) },
                    onCancel  = { navigator.notify("Cancelled", Toast.SHORT) },
                ))
            })
            add(ListEntry.Item(MenuItem("Prompt",           "Single-line text input dialog")) {
                navigator.showDialog(Dialog.Prompt(
                    title       = "Enter a name",
                    placeholder = "Name",
                    onSubmit    = { name -> navigator.notify("You entered: $name", Toast.LONG) },
                ))
            })

            add(ListEntry.Header(MenuItem("Toasts  (^N = history)")))
            add(ListEntry.Item(MenuItem("Info",    "")) { navigator.notify("Informational message",          Toast.SHORT, Toast.Type.Info) })
            add(ListEntry.Item(MenuItem("Success", "")) { navigator.notify("Operation completed",            Toast.SHORT, Toast.Type.Success) })
            add(ListEntry.Item(MenuItem("Warning", "")) { navigator.notify("Low disk space: 2 GB remaining", Toast.SHORT, Toast.Type.Warning) })
            add(ListEntry.Item(MenuItem("Error",   "")) { navigator.notify("Failed to connect to server",    Toast.LONG,  Toast.Type.Error) })
            add(ListEntry.Item(MenuItem("Stack 3x", "Fire 3 toasts at once to see stacking")) {
                navigator.notify("First toast — info",    Toast.LONG, Toast.Type.Info)
                navigator.notify("Second toast — success", Toast.LONG, Toast.Type.Success)
                navigator.notify("Third toast — warning",  Toast.LONG, Toast.Type.Warning)
            })

            add(ListEntry.Header(MenuItem("Logger  (^L = overlay)")))
            add(ListEntry.Item(MenuItem("Log debug",   "")) { Logger.debug("Debug message from HomeScreen") })
            add(ListEntry.Item(MenuItem("Log info",    "")) { Logger.info("Info: user opened HomeScreen") })
            add(ListEntry.Item(MenuItem("Log warning", "")) { Logger.warn("Warning: demo warning message") })
            add(ListEntry.Item(MenuItem("Log error",   "")) { Logger.error("Error: something went wrong") })

            add(ListEntry.Header(MenuItem("Preferences")))
            add(ListEntry.Item(MenuItem("Increment counter", "Survives restarts")) {
                val count = preferences.getInt("demo.counter") + 1
                preferences.setInt("demo.counter", count)
                navigator.notify("Counter = $count (saved)", Toast.SHORT, Toast.Type.Success)
            })
            add(ListEntry.Item(MenuItem("Reset counter", "")) {
                preferences.setInt("demo.counter", 0)
                navigator.notify("Counter reset to 0", Toast.SHORT)
            })

            add(ListEntry.Header(MenuItem("New Windows")))
            add(ListEntry.Item(MenuItem("Text Editor",      "Multiline editor with Ctrl+Z/Y undo")) { navigator.push(TextEditorDemoScreen(navigator, this@run).build()) })
            add(ListEntry.Item(MenuItem("File Picker",      "Browse filesystem, select a file")) { navigator.push(FilePickerDemoScreen(navigator, this@run).build()) })
            add(ListEntry.Item(MenuItem("Dashboard",        "2×2 grid of Dashboard cells")) { navigator.push(DashboardDemoScreen(navigator, this@run).build()) })
            add(ListEntry.Item(MenuItem("Paginated List",   "500 items loaded lazily in pages")) { navigator.push(PaginatedListDemoScreen(navigator, this@run).build()) })
            add(ListEntry.Item(MenuItem("Step Progress",    "Multi-step pipeline with spinner")) { navigator.push(StepProgressDemoScreen(navigator, this@run).build()) })
            add(ListEntry.Item(MenuItem("Spinners Demo",    "Catalog of all 65 animated spinners")) { navigator.push(SpinnersDemoScreen(navigator, this@run).build()) })
            add(ListEntry.Item(MenuItem("Responsive Layout","Narrow vs. wide terminal layout")) { navigator.push(ResponsiveDemoScreen(navigator, this@run).build()) })
            add(ListEntry.Header(MenuItem("App  (^Alt+P = perf  Ctrl+C = confirm quit)")))
            add(ListEntry.Item(MenuItem("Quit", "")) { navigator.pop() })
        }

        list(
            getEntries = { entries },
            renderItem = { item, focused ->
                val label = text("  ${item.label.padEnd(20)} ")
                val desc  = text(item.description).dim()
                if (focused) hbox(label, desc).inverted() else hbox(label, desc)
            },
            renderHeader = { item ->
                hbox(text("  ─── ${item.label} ───").bold(), filler())
            },
            toSearchString = { it.label }
        ).registerShortcuts(navigator, listOf(
            Shortcut(Key.CtrlQ, "^Q  Quit", description = "Quit the application", showInStatusBar = false) {
                navigator.pop()
            },
        ))
    }
}

// =============================================================================
// Screen — Fruit list
// =============================================================================

data class FruitListState(val fruits: List<Fruit>)

sealed class FruitListEvent {
    data object AddRandom : FruitListEvent()
}

class FruitListViewModel {
    private val _state = MutableStateFlow(FruitListState(INITIAL_FRUITS))
    val state: StateFlow<FruitListState> = _state

    fun onEvent(event: FruitListEvent) {
        when (event) {
            FruitListEvent.AddRandom -> {
                val already    = _state.value.fruits.map { it.name }.toSet()
                val candidate  = RANDOM_POOL.filterNot { it.name in already }.randomOrNull() ?: return
                _state.value   = _state.value.copy(fruits = _state.value.fruits + candidate)
            }
        }
    }

    companion object {
        private val INITIAL_FRUITS = listOf(
            Fruit("Apple",       "Fruit"), Fruit("Banana",     "Fruit"), Fruit("Cherry",   "Fruit"),
            Fruit("Dragon Fruit","Fruit"), Fruit("Elderberry", "Fruit"), Fruit("Fig",      "Fruit"),
            Fruit("Grape",       "Fruit"), Fruit("Honeydew",   "Fruit"), Fruit("Kiwi",     "Fruit"),
            Fruit("Lemon",       "Fruit"), Fruit("Mango",      "Fruit"), Fruit("Nectarine","Fruit"),
            Fruit("Artichoke",   "Vegetable"), Fruit("Broccoli",  "Vegetable"),
            Fruit("Carrot",      "Vegetable"), Fruit("Daikon",    "Vegetable"),
            Fruit("Eggplant",    "Vegetable"), Fruit("Fennel",    "Vegetable"),
            Fruit("Garlic",      "Vegetable"), Fruit("Horseradish","Vegetable"),
        )
        private val RANDOM_POOL = listOf(
            Fruit("Papaya",  "Fruit"), Fruit("Quince",     "Fruit"),
            Fruit("Raspberry","Fruit"), Fruit("Strawberry","Fruit"),
            Fruit("Tangerine","Fruit"), Fruit("Watermelon","Fruit"),
            Fruit("Zucchini","Vegetable"), Fruit("Yam",   "Vegetable"),
        )
    }
}

class FruitListScreen(private val navigator: Navigator, private val context: AppContext) {
    val viewModel = FruitListViewModel()

    fun build(): Component = context.run {
        GlobalScope.launch {
            viewModel.state.collect { state ->
                requestRedraw()
            }
        }

        list(
            getEntries = { buildListState(viewModel.state.value) },
            renderItem = { fruit, focused ->
                if (focused) hbox(text(" ▶ ").bold(), text(fruit.name)).inverted()
                else hbox(text("   "), text(fruit.name))
            },
            renderHeader = { fruit ->
                hbox(text(" ── ${fruit.name} ──").bold(), filler())
            },
            toSearchString = { it.name }
        ).registerShortcuts(navigator, listOf(
            Shortcut(Key.CtrlN, "^N  Add", description = "Add a random item to the list") {
                viewModel.onEvent(FruitListEvent.AddRandom)
            },
        ))
    }

    private fun buildListState(state: FruitListState): List<ListEntry<Fruit>> {
        val fruits = state.fruits.filter { it.category == "Fruit" }
        val vegs   = state.fruits.filter { it.category == "Vegetable" }
        return buildList {
            add(ListEntry.Header(Fruit("Fruits", "")))
            fruits.forEach { f -> add(ListEntry.Item(f) { navigator.push(DetailScreen(navigator, this@FruitListScreen.context, f).build()) }) }
            add(ListEntry.Header(Fruit("Vegetables", "")))
            vegs.forEach { v -> add(ListEntry.Item(v) { navigator.push(DetailScreen(navigator, this@FruitListScreen.context, v).build()) }) }
        }
    }
}

// =============================================================================
// Screen — Detail view
// =============================================================================

class DetailScreen(private val navigator: Navigator, private val context: AppContext, fruit: Fruit) {
    private val entries = buildList {
        add(ListEntry.Header(fruit.name))
        add(ListEntry.Item("Category   : ${fruit.category}"))
        add(ListEntry.Item("Length     : ${fruit.name.length} characters"))
        add(ListEntry.Item("Upper-case : ${fruit.name.uppercase()}"))
        add(ListEntry.Item("Lower-case : ${fruit.name.lowercase()}"))
        add(ListEntry.Item("First char : '${fruit.name.first()}'"))
        add(ListEntry.Item("Last char  : '${fruit.name.last()}'"))
    }

    fun build(): Component = context.run {
        list(
            getEntries = { entries },
            renderItem   = { str, focused -> if (focused) text("  $str").inverted() else text("  $str") },
            renderHeader = { str -> hbox(text(" ─── $str ───").bold(), filler()) }
        ).registerShortcuts(navigator, listOf(
            Shortcut(Key.CtrlB, "^B  Back", description = "Return to fruit list") { navigator.pop() },
        ))
    }
}

// =============================================================================
// Screen — Pager demo
// =============================================================================

class PagerDemoScreen(private val navigator: Navigator, private val context: AppContext) {
    val state = MutableStateFlow(PagerState(DEMO_LINES, showLineNumbers = true))

    fun build(): Component = context.run {
        pager(
            getState = { state.value }
        )
    }

    companion object {
        val DEMO_LINES = """
            ftxui-kt-framework — Release Notes
            ====================================

            v0.4.0 — Pager & Command Palette
            ---------------------------------
            Added PagerWindow: a read-only scrollable text view with incremental
            search, match navigation (n/N), optional line numbers, and a persistent
            search bar that stays visible (dimmed) after closing so you can keep
            jumping between matches.

            Added command palette (Ctrl+P): a floating fuzzy-search overlay that
            lists all global shortcuts registered on the current screen. Type to
            filter by label or description, arrow keys to select, Enter to execute.

            v0.3.0 — Tree & Split Views
            ----------------------------
            Added TreeWindow: hierarchical tree with expand/collapse via arrow keys,
            path-based focus tracking that survives state updates, and a scroll bar
            that mirrors the ListWindow design.

            Added SplitWindow: composes two Window instances side by side with Tab /
            Shift-Tab focus cycling. The inactive panel is automatically dimmed.

            v0.2.0 — List & Search
            -----------------------
            Added ListWindow: a scrollable list with Header/Item entries, vim-style
            navigation (j/k, Ctrl+U/D, g/G), and an incremental fuzzy search
            sub-window (/) that highlights matches and scrolls to the first hit.

            Added Toast notifications with four severity levels and an animated
            countdown border that drains around the pill as the timer runs out.

            Added Dialog overlays: Alert, Confirm, and single-line Prompt.

            v0.1.0 — Core Architecture
            ---------------------------
            Established the Screen / ViewModel / Window / Navigator architecture.

            Screen owns layout and global shortcuts. ViewModel owns state (StateFlow).
            Windows are dumb renderers that receive state and emit events upward.
            AppRunner drives the event loop, manages the navigation stack, and layers
            overlays (toast, dialog, help, palette) above the active screen.

            Keyboard shortcut priority:
              1. Active Window (highest — e.g., list navigation, search input)
              2. Screen global shortcuts (e.g., Ctrl+S save, Ctrl+R refresh)
              3. Framework defaults (Esc = back, ? = help, Ctrl+P = palette)

            Theme system: ThemeColors is open for extension. Theme.current is a
            global swap point; Theme.ext<MyTheme>() casts it safely.

            AsyncViewModel wraps the loading / loaded / error lifecycle so screens
            don't repeat that boilerplate. AsyncScreen extends Screen and renders
            a spinner during loading automatically.

            Architecture notes
            -------------------
            All mutable state in Windows uses @Volatile. Windows are not recomposed
            on each frame — they hold their own scroll/focus state and mutate it in
            place, then request a repaint via AppRunner.requestAnimationFrame() which
            is triggered by state collection in the coroutine scope.

            The navigation stack stores a pre-rendered Component per screen entry.
            When the ViewModel emits a new state the component is replaced in the
            stack slot, not rebuilt from scratch, keeping the scroll position of a
            background screen alive when you navigate back to it.

            Known limitations
            ------------------
            - ListEntry.Item.onEnter and TreeNode.onToggle are nullable callbacks;
              the caller must supply them explicitly. A future version may unify
              these behind a single interaction model.
            - SplitWindow currently supports exactly two panels. An N-panel split
              (with configurable weights) is planned.
            - PagerWindow search uses substring matching. Regex support is planned.
              - The command palette only surfaces Screen-level global shortcuts. Window-
              level shortcuts (e.g., / for search, j/k for navigation) are intentionally
              excluded because they are context-sensitive and always active.
        """.trimIndent().lines()
    }
}

// =============================================================================
// Screen — Split view demo
// =============================================================================

data class SplitDemoState(val fruits: List<Fruit>, val vegetables: List<Fruit>)

class SplitDemoScreen(private val navigator: Navigator, private val context: AppContext) {
    val state = MutableStateFlow(
        SplitDemoState(
            fruits = listOf(
                Fruit("Apple", "Fruit"), Fruit("Banana", "Fruit"), Fruit("Cherry", "Fruit"),
                Fruit("Dragon Fruit", "Fruit"), Fruit("Elderberry", "Fruit"),
            ),
            vegetables = listOf(
                Fruit("Artichoke", "Vegetable"), Fruit("Broccoli", "Vegetable"),
                Fruit("Carrot",    "Vegetable"), Fruit("Daikon",   "Vegetable"),
            )
        )
    )

    fun build(): Component = context.run {
        val s = state.value
        val leftEntries = s.fruits.map { ListEntry.Item(it) }
        val rightEntries = s.vegetables.map { ListEntry.Item(it) }

        val leftList = list(
            getEntries = { leftEntries },
            renderItem = { fruit, focused ->
                if (focused) hbox(text(" ▶ ").bold(), text(fruit.name)).inverted()
                else hbox(text("   "), text(fruit.name))
            },
            renderHeader = { fruit -> hbox(text(" ── ${fruit.name} ──").bold(), filler()) },
        )

        val rightList = list(
            getEntries = { rightEntries },
            renderItem = { fruit, focused ->
                if (focused) hbox(text(" ▶ ").bold(), text(fruit.name)).inverted()
                else hbox(text("   "), text(fruit.name))
            },
            renderHeader = { fruit -> hbox(text(" ── ${fruit.name} ──").bold(), filler()) },
        )

        split(leftList, rightList, leftTitle = "Fruits", rightTitle = "Vegetables")
    }
}

// =============================================================================
// Screen — Async loading demo
// =============================================================================

data class FruitReport(val fruits: List<Fruit>, val loadTimeMs: Long)

sealed class AsyncState<out T> {
    data class Loading(val tick: Int = 0) : AsyncState<Nothing>()
    data class Success<T>(val data: T) : AsyncState<T>()
    data class Error(val message: String, val canRetry: Boolean = false) : AsyncState<Nothing>()
}

class AsyncDemoScreen(private val navigator: Navigator, private val context: AppContext) {
    private var state by context.mutableStateOf<AsyncState<FruitReport>>(AsyncState.Loading())
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        load()
    }

    private fun load() {
        state = AsyncState.Loading()
        scope.launch {
            val animJob = launch {
                var tick = 0
                while (state is AsyncState.Loading) {
                    delay(100.milliseconds)
                    state = AsyncState.Loading(++tick)
                }
            }
            try {
                delay(2500.milliseconds)
                val result = FruitReport(
                    fruits = listOf(
                        Fruit("Papaya",    "Fruit"), Fruit("Quince",   "Fruit"),
                        Fruit("Starfruit", "Fruit"), Fruit("Yuzu",     "Fruit"),
                    ),
                    loadTimeMs = 2500,
                )
                animJob.cancel()
                state = AsyncState.Success(result)
            } catch (e: Exception) {
                animJob.cancel()
                state = AsyncState.Error(e.message ?: "Unknown error", canRetry = true)
            }
        }
    }

    fun build(): Component = context.run {
        val tabIndex = IntState(0)
        val tabContainer = tab(tabIndex)

        val loadingComp = renderer {
            val s = state
            val tick = if (s is AsyncState.Loading) s.tick else 0
            val frame = listOf("-", "\\", "|", "/")[tick % 4]
            vbox(filler(), hbox(filler(), text("$frame Loading…"), filler()), filler())
        }
        tabContainer.add(loadingComp)

        val errorComp = renderer {
            val s = state
            val msg = if (s is AsyncState.Error) s.message else ""
            val canRetry = if (s is AsyncState.Error) s.canRetry else false
            val hint = if (canRetry) "  [R] Retry" else ""
            vbox(filler(), hbox(filler(), text("✗ $msg$hint").color(Theme.current.error), filler()), filler())
        }
        tabContainer.add(errorComp)

        var successCount = 0
        var loadedComp: Component? = null

        val mainComp = renderer {
            val s = state
            when (s) {
                is AsyncState.Loading -> {
                    tabIndex.value = 0
                }
                is AsyncState.Error -> {
                    tabIndex.value = 1
                }
                is AsyncState.Success -> {
                    if (loadedComp == null) {
                        val data = s.data
                        val entries = buildList {
                            add(ListEntry.Header("Loaded in ${data.loadTimeMs} ms"))
                            data.fruits.forEach { add(ListEntry.Item(it.name)) }
                        }
                        loadedComp = list(
                            getEntries = { entries },
                            renderItem = { str, focused -> if (focused) text("  $str").inverted() else text("  $str") },
                            renderHeader = { str -> hbox(text(" ─── $str ───").bold(), filler()) }
                        )
                        tabContainer.add(loadedComp)
                        successCount++
                    }
                    tabIndex.value = 1 + successCount
                }
            }
            tabContainer.render()
        }.catchEvent { event ->
            if (state is AsyncState.Error && (isChar(event, "r") || isChar(event, "R"))) {
                load()
                true
            } else {
                false
            }
        }

        mainComp.registerShortcuts(navigator) {
            if (state is AsyncState.Error) {
                listOf(
                    Shortcut("r", "r Retry", description = "Retry loading") {
                        load()
                    }
                )
            } else {
                emptyList()
            }
        }
    }
}

// =============================================================================
// Screen — Tree view demo
// =============================================================================

private typealias StringTree = List<TreeNode<String>>

data class TreeDemoState(val roots: StringTree)

sealed class TreeDemoEvent {
    data class Toggle(val path: List<Int>) : TreeDemoEvent()
    data object CollapseAll : TreeDemoEvent()
}

class TreeDemoViewModel {
    private val _state = MutableStateFlow(TreeDemoState(INITIAL_TREE))
    val state: StateFlow<TreeDemoState> = _state

    fun onEvent(event: TreeDemoEvent) {
        when (event) {
            is TreeDemoEvent.Toggle ->
                _state.value = TreeDemoState(toggleAt(_state.value.roots, event.path))
            is TreeDemoEvent.CollapseAll ->
                _state.value = TreeDemoState(_state.value.roots.map { it.copy(isExpanded = false) })
        }
    }

    companion object {
        val INITIAL_TREE: StringTree = listOf(
            TreeNode("Fruits", isExpanded = true, children = listOf(
                TreeNode("Apple"), TreeNode("Banana"), TreeNode("Cherry"),
                TreeNode("Dragon Fruit"), TreeNode("Elderberry"),
            )),
            TreeNode("Vegetables", isExpanded = true, children = listOf(
                TreeNode("Artichoke"), TreeNode("Broccoli"), TreeNode("Carrot"),
                TreeNode("Daikon"),    TreeNode("Eggplant"),
            )),
            TreeNode("Grains", isExpanded = false, children = listOf(
                TreeNode("Barley"), TreeNode("Oats"), TreeNode("Rice"), TreeNode("Wheat"),
            )),
        )

        fun toggleAt(nodes: StringTree, path: List<Int>): StringTree {
            if (path.isEmpty()) return nodes
            return nodes.mapIndexed { i, node ->
                if (i != path.first()) node
                else if (path.size == 1) node.copy(isExpanded = !node.isExpanded)
                else node.copy(children = toggleAt(node.children, path.drop(1)))
            }
        }
    }
}

class TreeDemoScreen(private val navigator: Navigator, private val context: AppContext) {
    val viewModel = TreeDemoViewModel()

    fun build(): Component = context.run {
        GlobalScope.launch {
            viewModel.state.collect {
                requestRedraw()
            }
        }
        tree(
            getState = { TreeState(withCallbacks(viewModel.state.value.roots, emptyList())) },
            renderNode = { label, _, focused, _, _ ->
                if (focused) text(label).inverted() else text(label)
            }
        ).registerShortcuts(navigator, listOf(
            Shortcut(Key.CtrlR, "^R  Collapse all", description = "Collapse all top-level nodes") {
                viewModel.onEvent(TreeDemoEvent.CollapseAll)
            },
        ))
    }

    private fun withCallbacks(nodes: StringTree, prefix: List<Int>): StringTree =
        nodes.mapIndexed { i, node ->
            val path = prefix + i
            node.copy(
                onToggle  = if (node.children.isNotEmpty()) {
                    { viewModel.onEvent(TreeDemoEvent.Toggle(path)) }
                } else null,
                children  = withCallbacks(node.children, path),
            )
        }
}

// =============================================================================
// Screen — Table demo
// =============================================================================

data class FruitRow(val name: String, val category: String, val length: Int)

class TableDemoScreen(private val navigator: Navigator, private val context: AppContext) {
    fun build(): Component = context.run {
        table(
            getRows = { FRUIT_ROWS },
            columns = listOf(
                TableColumn("Name", extract = { it.name }),
                TableColumn(
                     header = "Category",
                     extract = { it.category },
                     renderHeader = { text, width -> text(text.padEnd(width + 3)).bold().underlined() },
                     renderCell = { item, width, focused ->
                         val color = if (item.category == "Fruit") Color.Green else Color.Yellow
                         val el = text(item.category.padEnd(width + 3)).color(color)
                         if (focused) el.inverted() else el
                     },
                ),
                TableColumn(
                     header = "Length",
                     extract = { it.length.toString() },
                     renderCell = { item, width, focused ->
                         val bar = "█".repeat(item.length) + " ".repeat((width - item.length).coerceAtLeast(0) + 3)
                         val el = text(bar).color(Color.Blue)
                         if (focused) el.inverted() else el
                     },
                ),
            ),
            onEnter = { row ->
                Logger.info("Selected: ${row.name} (${row.category})")
            }
        ).registerShortcuts(navigator, listOf(
            Shortcut(Key.CtrlB, "^B  Back", description = "Return to home") { navigator.pop() },
        ))
    }

    companion object {
        val FRUIT_ROWS = listOf(
            FruitRow("Apple",       "Fruit",     5),
            FruitRow("Banana",      "Fruit",     6),
            FruitRow("Cherry",      "Fruit",     6),
            FruitRow("Dragon Fruit","Fruit",     11),
            FruitRow("Elderberry",  "Fruit",     10),
            FruitRow("Fig",         "Fruit",     3),
            FruitRow("Grape",       "Fruit",     5),
            FruitRow("Honeydew",    "Fruit",     8),
            FruitRow("Kiwi",        "Fruit",     4),
            FruitRow("Lemon",       "Fruit",     5),
            FruitRow("Mango",       "Fruit",     5),
            FruitRow("Artichoke",   "Vegetable", 9),
            FruitRow("Broccoli",    "Vegetable", 8),
            FruitRow("Carrot",      "Vegetable", 6),
            FruitRow("Daikon",      "Vegetable", 6),
            FruitRow("Eggplant",    "Vegetable", 8),
            FruitRow("Fennel",      "Vegetable", 6),
            FruitRow("Garlic",      "Vegetable", 6),
        )
    }
}
