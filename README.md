# ftxui-kt-framework

An opinionated TUI application framework for Kotlin Multiplatform Native, built on top of [ftxui-kt](https://github.com/nassendelft/ftxui-kt). It provides a Screen / Component architecture, a navigation stack, built-in views, and framework-level overlays (help, toasts, dialogs, logging, preferences) so you can focus on writing application logic rather than plumbing.

**Targets:** macOS ARM64, Linux x64  
**Kotlin:** 2.3.21

---

## Architecture

The framework follows a declarative and component-based UI architecture with a clean unidirectional flow:

```
Screen (layout + shortcuts)
   ↓  builds (once)
Component (persistent views/layout)
   ↓  subscribes to
State (local or ViewModel StateFlow)
   ↑  triggers redraw on change
ScreenContext.requestRedraw()
```

### Screen

A `Screen` is a full-screen container. Subclass it to define layouts, keyboard shortcuts, and active focus. Unlike traditional model-driven views, screens build their content hierarchy once in `buildContent` and manage reactivity by redrawing the UI when state changes.

```kotlin
class MyScreen : Screen() {
    private var count by context.mutableStateOf(0) // Local reactive state
    private val myListView: Component by lazy { ... }

    override val globalShortcuts = listOf(
        Shortcut(Key.CtrlS, "^S  Save", description = "Save changes") {
            saveData()
        },
    )

    // Specifies which component receives key events by default
    override val activeWindow get() = myListView

    override fun buildContent(context: ScreenContext): Component {
        // Built once; reacts to `count` updates automatically triggering redraw
        return context.list(
            getEntries = { getItems(count) },
            renderItem = { item, focused ->
                if (focused) text(item.name).inverted() else text(item.name)
            },
            renderHeader = { name -> hbox(text("── $name ──").bold(), filler()) }
        )
    }
}
```

`handleInput` dispatches keyboard events in priority order: active component (`activeWindow`) → screen global shortcuts → Esc/Backspace (pop screen).

### State & Reactivity

Reactivity in screens can be managed in two ways:

1. **Local State (`mutableStateOf`)**: A Compose-like delegate available on `ScreenContext`. Mutating a variable defined with `mutableStateOf` automatically schedules a screen redraw.
   ```kotlin
   var selectedIndex by context.mutableStateOf(0)
   ```

2. **Flow / ViewModel State**: Screens can declare a traditional `ViewModel`. To react to its state changes, launch a coroutine to collect the state flow and request a redraw:
   ```kotlin
   override fun buildContent(context: ScreenContext): Component {
       GlobalScope.launch {
           viewModel.state.collect {
               context.requestRedraw()
           }
       }
       // ... build component using viewModel.state.value ...
   }
   ```

### Views & Components

Views are not subclassed classes anymore; they are declarative `Component` functions built as extensions on `ScreenContext` (e.g. `list`, `table`, `split`). Under the hood, they wrap native FTXUI focus and event handling logic, and accept lambda retrievers (e.g., `getEntries = { ... }`) to pull state dynamically on render.

### Navigator

`Navigator` is passed to input handlers and screen builders. Use it to push/pop screens or show global UI overlays:

```kotlin
override fun handleInput(event: FtxUIEvent, navigator: Navigator): Boolean {
    this.navigator = navigator
    return super.handleInput(event, navigator)
}

// Then use:
navigator.push(DetailScreen(item))
navigator.pop()
navigator.showDialog(Dialog.Alert(title = "Done", message = "Saved."))
navigator.notify("Item saved", Toast.SHORT, Toast.Type.Success)
```

---

## Entry points

### Single-screen app

```kotlin
fun main() {
    Preferences.init("my-app")          // must come first
    runApp(HomeScreen(), confirmOnQuit = true, enableCtrlZ = true)
}
```

---

## Built-in Views

All views are instantiated via `ScreenContext` extension methods and return a standard FTXUI `Component`.

### list

Scrollable list with headers, fuzzy search, and vim-style navigation.

```kotlin
val list = context.list(
    getEntries = {
        buildList {
            add(ListEntry.Header("Fruits"))
            fruits.forEach { add(ListEntry.Item(it) { navigator.push(DetailScreen(it)) }) }
        }
    },
    renderItem = { fruit, focused ->
        if (focused) text(fruit.name).inverted() else text(fruit.name)
    },
    renderHeader = { name -> hbox(text("── $name ──").bold(), filler()) },
    toSearchString = { it.name },
    style = ListStyle(focusedItemBackground = Color.Blue)
)
```

Keys: `j`/`k` navigate, `g`/`G` top/bottom, `Ctrl+U`/`D` half-page, `/` to activate fuzzy search.

### table

Sortable table with customisable column renderers.

```kotlin
val table = context.table(
    getRows = { FRUIT_ROWS },
    columns = listOf(
        TableColumn("Name", extract = { it.name }),
        TableColumn(
            header = "Category",
            extract = { it.category },
            renderCell = { item, width, focused ->
                val color = if (item.category == "Fruit") Color.Green else Color.Yellow
                val el = text(item.category.padEnd(width + 3)).color(color)
                if (focused) el.inverted() else el
            }
        ),
    ),
    onEnter = { row -> navigator.push(DetailScreen(row)) }
)
```

Keys: `j`/`k` navigate, `s` cycles column sort (▲ / ▼ / off), `Enter` triggers `onEnter`.

### pager

Read-only scrollable text panel with incremental search.

```kotlin
val pager = context.pager(
    getState = { PagerState(lines = myLines, showLineNumbers = true) }
)
```

Keys: `j`/`k`/`g`/`G` scroll, `/` search, `n`/`N` next/prev match.

### tree

Hierarchical tree with expand/collapse nodes.

```kotlin
val tree = context.tree(
    getState = { TreeState(roots) },
    renderNode = { label, depth, focused, hasChildren, isExpanded ->
        if (focused) text(label).inverted() else text(label)
    }
)
```

Create a tree of `TreeNode<T>` which defines `children`, `isExpanded`, and optional `onToggle`/`onEnter` callbacks.

### split

Two components side-by-side; Tab/Shift+Tab switches focus, and the inactive panel is dimmed automatically.

```kotlin
val split = context.split(
    left = leftComponent,
    right = rightComponent,
    leftTitle = "Left Pane",
    rightTitle = "Right Pane"
)
```

### textEditor

Multiline text editor with built-in undo/redo stack. Requires `enableCtrlZ = true` in `runApp()`.

```kotlin
var text = "Initial Text"

val editor = context.textEditor(
    content = ::text, // KMutableProperty0<String>
    showLineNumbers = true,
    onContentChange = { newText -> Logger.debug("Text length: ${newText.length}") },
    onStateChange = { newState ->
        // Capture editor state (cursor position, scroll offset)
        editorState = newState
        context.requestRedraw()
    }
)
```

Keys: Arrow keys, `Ctrl+Z`/`Y` undo/redo. Line numbers are rendered automatically.

### filePicker

POSIX filesystem browser.

```kotlin
val picker = context.filePicker(
    initialPath = ".",
    onFileSelected = { path -> navigator.pop(); handleFile(path) },
    showHiddenInitially = false,
    filter = { entry -> entry.name.endsWith(".kt") }
)
```

Keys: `j`/`k` navigate, `Enter` enter directory or select file, `Backspace`/`h` go up, `/` filter, `.` toggle hidden files.

### dashboard

Grid of independent cells, each rendering a child `Component`.

```kotlin
val dashboard = context.dashboard(
    columns = 2,
    cells = listOf(
        DashboardCell("CPU Gauge", render = { cpuGaugeComponent }),
        DashboardCell("Logs", render = { logListComponent })
    )
)
```

Keys: `Tab`/`Shift+Tab` cycle active cell focus.

### paginatedList

Lazily loads pages of data reactively via a suspend loader.

```kotlin
val paginated = context.paginatedList(
    pageSize = 50,
    loadThreshold = 10,
    loadPage = { offset, limit -> fetchItems(offset, limit) }, // suspend function returning List<ListEntry<T>>
    renderItem = { item, focused -> if (focused) text(item.name).inverted() else text(item.name) },
    renderHeader = { name -> hbox(text("  $name").bold(), filler()) }
)
```

A loading row appears at the bottom while next page fetches. Fuzzy search `/` filters over all currently loaded items.

### stepProgress

Renders a pipeline pipeline of steps with status icons, spinners, and expandable output.

```kotlin
val progress = context.stepProgress(
    getState = {
        StepProgressState(
            steps = listOf(
                ProgressStep("Build", StepStatus.Done, output = buildLogs),
                ProgressStep("Test", StepStatus.Running)
            ),
            spinnerTick = currentTick
        )
    }
)
```

Keys: Arrow keys navigate steps, `Space`/`Enter` expand/collapse selected step outputs.

---

## Component Styling

Each built-in view can be styled individually by passing a specific `*Style` config class. By default, styles fallback to the active theme's colors defined in `Theme.current`.

```kotlin
val customListStyle = ListStyle(
    focusedItemForeground = Color.Black,
    focusedItemBackground = Color.Yellow,
    headerForeground = Color.Cyan
)

val listView = context.list(
    getEntries = { entries },
    renderItem = { item, focused -> text(item) },
    renderHeader = { text(it) },
    style = customListStyle
)
```

### Supported Styles
* `ListStyle`: `focusedItemForeground`, `focusedItemBackground`, `headerForeground`, `scrollThumb`, `searchHighlight`
* `TableStyle`: `headerForeground`, `focusedRowForeground`, `focusedRowBackground`, `sortIndicatorColor`, `scrollThumb`, `borderStyle`
* `TreeStyle`: `focusedNodeForeground`, `focusedNodeBackground`, `expandedIcon`, `collapsedIcon`, `leafIndent`, `scrollThumb`
* `SplitStyle`: `activeTitleForeground`, `inactiveTitleForeground`, `borderStyle`, `activeBorderStyle`
* `DashboardStyle`: `focusedTitleForeground`, `unfocusedTitleForeground`, `borderStyle`, `focusedBorderStyle`
* `PagerStyle`: `searchHighlight`, `lineNumberColor`, `scrollThumb`
* `StepProgressStyle`: `pendingColor`, `runningColor`, `doneColor`, `failedColor`, `skippedColor`
* `FilePickerStyle`: `directoryColor`, `fileColor`, `pathColor`, `scrollThumb`
* `TextEditorStyle`: `lineNumbersColor`, `cursorForeground`, `cursorBackground`, `scrollThumb`

To configure global colors, subclass or instantiate `ThemeColors` and assign it:
```kotlin
Theme.current = ThemeColors(
    accent = Color.Green,
    border = Color.GrayDark
)
```

---

## Async loading

`AsyncViewModel` and `AsyncScreen` handle loading, success, and error states seamlessly:

```kotlin
class MyViewModel : AsyncViewModel<MyData, MyEvent>() {
    init { load() }

    override fun onEvent(event: MyEvent) {
        if (event is MyEvent.Retry) load()
    }

    private fun load() = launchLoad {
        delay(500) // suspend call
        fetchData()
    }
}

class MyScreen : AsyncScreen<MyData, MyEvent>() {
    override val viewModel = MyViewModel()

    override fun ScreenContext.buildLoaded(data: MyData): Component =
        list(
            getEntries = { data.items.map { ListEntry.Item(it) } },
            renderItem = { name, focused -> text(name) },
            renderHeader = { text(it) }
        )
}
```

A loading spinner is shown automatically. Subclasses of `AsyncScreen` implement `ScreenContext.buildLoaded(data: T)` to define the loaded view, and can override `buildLoading` or `buildError` to customize those states.

---

## Framework overlays (built-in, no setup needed)

| Shortcut     | Overlay |
|---|---|
| `?`          | Help — all screen shortcuts + framework defaults |
| `Ctrl+P`     | Command palette — fuzzy search over screen shortcuts |
| `Ctrl+N`     | Notification history — scroll with `j`/`k`, `Esc` to close |
| `Ctrl+L`     | Log viewer — scroll with `j`/`k`/`g`/`G`, `Esc` to close |
| `Ctrl+Alt+P` | Performance overlay — FPS, frame time, stack depth, terminal size |

### Dialogs

```kotlin
navigator.showDialog(Dialog.Alert(title = "Info", message = "Done."))

navigator.showDialog(Dialog.Confirm(
    title = "Delete?",
    message = "This cannot be undone.",
    onConfirm = { doDelete() },
    onCancel  = { /* nothing */ },
))

navigator.showDialog(Dialog.Prompt(
    title = "Enter name",
    placeholder = "Name",
    onSubmit = { name -> save(name) },
))
```

### Toasts

```kotlin
navigator.notify("Saved",          Toast.SHORT, Toast.Type.Success)
navigator.notify("Low disk space", Toast.SHORT, Toast.Type.Warning)
navigator.notify("Load failed",    Toast.LONG,  Toast.Type.Error)
navigator.notify("FYI",            Toast.SHORT, Toast.Type.Info)   // default
```

Up to 3 toasts are stacked simultaneously. Each shows an animated countdown border. All toasts are accessible via `Ctrl+N` notification history.

---

## Singletons

### Logger

```kotlin
Logger.debug("Verbose detail")
Logger.info("User opened settings")
Logger.warn("Config missing, using defaults")
Logger.error("Connection refused")
```

Logs are kept in a 1000-entry in-memory ring buffer, rendered inside a log panel overlay (`Ctrl+L`).

### Preferences

Persistent key-value store backed by `~/.config/<appName>/prefs.properties`. Call `Preferences.init(appName)` before `runApp()`.

```kotlin
val count = Preferences.getInt("launch.count", default = 0)
Preferences.setInt("launch.count", count + 1)

Preferences.setString("last.file", "/path/to/file")
val path = Preferences.getString("last.file", default = "")

Preferences.setBoolean("dark.mode", true)
```

Preferences are saved automatically on app exit.

---

## Utilities

### UndoRedoStack\<T\>

Generic undo/redo stack (used internally by `textEditor`).

```kotlin
val history = UndoRedoStack(initial = emptyList<String>(), maxSize = 100)
history.push(newState)
history.undo()
history.redo()
history.reset(newInitial)
```

### ResponsiveLayout

Switches between two layouts based on terminal width.

```kotlin
val component = responsive(
    breakpoint = 100,
    narrow = { narrowLayout() },
    wide   = { wideLayout() },
)
```

---

## Build & run

```bash
# Build (macOS ARM64)
./gradlew linkDebugExecutableMacosArm64

# Run the demo app
./build/bin/macosArm64/debugExecutable/ftxui-kt-framework.kexe

# Build (Linux x64)
./gradlew linkDebugExecutableLinuxX64
```

---

## Conventions

- `Preferences.init(appName)` must be called before `runApp()`.
- `Ctrl+N` and `Ctrl+L` are reserved by the framework; do not register them on screens.
- `enableCtrlZ = true` is required in `runApp()` to use undo/redo in `textEditor`.
- Component focus is managed via native FTXUI focus APIs. Specify the active component that should receive input via the `activeWindow` property on the screen.
