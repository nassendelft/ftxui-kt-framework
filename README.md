# ftxui-kt-framework

An opinionated TUI application framework for Kotlin Multiplatform Native, built on top of [ftxui-kt](https://github.com/nassendelft/ftxui-kt). It provides a Screen / ViewModel / Window architecture, a navigation stack, built-in windows, and framework-level overlays (help, toasts, dialogs, logging, preferences) so you can focus on writing application logic rather than plumbing.

**Targets:** macOS ARM64, Linux x64  
**Kotlin:** 2.3.21

---

## Architecture

The framework follows a strict unidirectional data flow:

```
ViewModel (StateFlow)
    ↓  state
  Screen (layout + shortcuts)
    ↓  state slice
  Window (dumb renderer)
    ↑  callbacks / events
  ViewModel
```

### Screen

A `Screen<S, E>` is a full-screen container. Subclass it to define layout and keyboard shortcuts.

```kotlin
class MyScreen : Screen<MyState, MyEvent>() {
    override val viewModel = MyViewModel()

    override val globalShortcuts = listOf(
        Shortcut(Key.CtrlS, "^S  Save", description = "Save changes") {
            viewModel.onEvent(MyEvent.Save)
        },
    )

    private val list = object : ListWindow<MyItem>(...) {
        override fun getVisibleHeight() = Terminal.size().dimy - STATUS_BAR_HEIGHT
    }

    override val activeWindow get() = list

    override fun buildContent(state: MyState): Component =
        list.render(ListState(state.items.map { ListEntry.Item(it) }))
}
```

`handleInput` dispatches in priority order: active window → screen shortcuts → Esc/Backspace (pop).

### ViewModel

A `ViewModel<S, E>` owns state and processes events.

```kotlin
class MyViewModel : ViewModel<MyState, MyEvent>() {
    private val _state = MutableStateFlow(MyState())
    override val state: StateFlow<MyState> = _state

    override fun onEvent(event: MyEvent) {
        when (event) {
            MyEvent.Save -> _state.value = _state.value.copy(saved = true)
        }
    }
}
```

### Window

A `Window<S>` is a dumb renderer. It holds only volatile scroll/focus state — it never owns application state. Call `render(state)` to get a `Component`, and override `onInput` to consume keys before screen shortcuts.

### Navigator

`Navigator` is injected into `handleInput`. Use it to navigate and show UI:

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

### Tabbed app

```kotlin
fun main() {
    Preferences.init("my-app")
    runTabApp(
        tabs = listOf(
            Tab("Files")   { FilesScreen() },
            Tab("Search")  { SearchScreen() },
            Tab("Settings"){ SettingsScreen() },
        ),
        confirmOnQuit = true,
    )
}
```

Tab navigation: `Alt+H` / `Alt+L`. Each tab has its own independent navigation stack and overlays.

---

## Built-in Windows

### ListWindow\<T\>

Scrollable list with optional headers, fuzzy search, and vim-style navigation.

```kotlin
val list = object : ListWindow<Fruit>(
    renderItem = { fruit, focused ->
        if (focused) text(fruit.name).inverted() else text(fruit.name)
    },
    renderHeader = { fruit -> hbox(text("── ${fruit.name} ──").bold(), filler()) },
    toSearchString = { it.name },
) {
    override fun getVisibleHeight() = Terminal.size().dimy - STATUS_BAR_HEIGHT
}

// Render:
list.render(ListState(buildList {
    add(ListEntry.Header(Fruit("Fruits", "")))
    fruits.forEach { add(ListEntry.Item(it) { navigator.push(DetailScreen(it)) }) }
}))
```

Keys: `j`/`k` navigate, `g`/`G` top/bottom, `Ctrl+U`/`D` half-page, `/` fuzzy search.

Call `listWindow.updateState(newState)` from a coroutine to update the list reactively without rebuilding the screen.

### TableWindow\<T\>

Sortable table with customisable column renderers.

```kotlin
val table = object : TableWindow<FruitRow>(
    columns = listOf(
        TableColumn("Name", extract = { it.name }),
        TableColumn("Category", extract = { it.category }),
    ),
    onEnter = { row -> navigator.push(DetailScreen(row)) },
) {
    override fun getVisibleHeight() = Terminal.size().dimy - STATUS_BAR_HEIGHT
}
```

Keys: `j`/`k` navigate, `s` cycles column sort (▲ / ▼ / off), `Enter` fires `onEnter`.

### PagerWindow

Read-only scrollable text with incremental search.

```kotlin
val pager = object : PagerWindow() {
    override fun getVisibleHeight() = Terminal.size().dimy - STATUS_BAR_HEIGHT
}
// state:
pager.render(PagerState(lines = myLines, showLineNumbers = true))
```

Keys: `j`/`k`/`g`/`G` scroll, `/` search, `n`/`N` next/prev match.

### TreeWindow\<T\>

Hierarchical tree with expand/collapse.

```kotlin
val tree = object : TreeWindow<String>(
    renderNode = { label, _, focused, _, _ ->
        if (focused) text(label).inverted() else text(label)
    }
) {
    override fun getVisibleHeight() = Terminal.size().dimy - STATUS_BAR_HEIGHT
}
// state (attach callbacks before passing):
tree.render(TreeState(roots))
```

`TreeNode<T>` holds `children`, `isExpanded`, and an optional `onToggle` callback. Toggle expand/collapse by emitting an event from `onToggle` to the ViewModel, updating the tree, and re-rendering.

### SplitWindow\<L, R\>

Two windows side by side; Tab/Shift+Tab switches focus; inactive panel is dimmed.

```kotlin
val split = SplitWindow(leftWindow, rightWindow, leftTitle = "Left", rightTitle = "Right")
split.render(leftState to rightState)
```

### TextEditorWindow

Multiline editor with built-in undo/redo. Requires `enableCtrlZ = true` in `runApp()`.

```kotlin
val editor = object : TextEditorWindow() {
    override fun getVisibleHeight() = Terminal.size().dimy - STATUS_BAR_HEIGHT
}
// read content:
val lines: List<String> = editor.lines
```

Keys: arrow keys, `Ctrl+Z`/`Y` undo/redo, line numbers shown automatically.

### FilePickerWindow

POSIX filesystem browser.

```kotlin
FilePickerWindow(
    initialPath = Path("/home/user"),
    onFileSelected = { path -> navigator.pop(); handleFile(path) },
    showHiddenInitially = false,
    filter = { it.name.endsWith(".kt") },
)
```

Keys: `j`/`k` navigate, `Enter` enter directory or select file, `Backspace`/`h` go up, `/` filter, `.` toggle hidden files.

### DashboardWindow

Grid of independent cells, each with its own render lambda.

```kotlin
DashboardWindow(
    columns = 2,
    cells = listOf(
        DashboardCell("CPU", render = { cpuGauge() }),
        DashboardCell("Memory", render = { memChart() }),
        DashboardCell("Logs", render = { logList() }, onInput = { event -> false }),
    ),
)
```

Keys: `Tab`/`Shift+Tab` cycle cell focus.

### PaginatedListWindow\<T\>

Lazily loads pages of data via a suspend function.

```kotlin
val paginated = PaginatedListWindow<MyItem>(
    pageSize = 50,
    loadThreshold = 10,
    loadPage = { page -> fetchPage(page) },
    renderItem = { item, focused -> if (focused) text(item.name).inverted() else text(item.name) },
    toSearchString = { it.name },
)
```

A loading row appears at the bottom while the next page is fetching. Fuzzy search works over all loaded items. Call `paginated.setRequestFrame { app.requestAnimationFrame() }` if you need frame updates during loading.

### StepProgressWindow

Renders a pipeline with named steps, spinner ticks, and expandable output.

```kotlin
// ViewModel drives the spinner and updates steps:
val state = StepProgressState(
    steps = listOf(
        Step("Build",  StepStatus.Done, output = buildLog),
        Step("Test",   StepStatus.Running, output = emptyList()),
        Step("Deploy", StepStatus.Pending),
    ),
    spinnerTick = tick,
)
stepProgressWindow.render(state)
```

Keys: arrow keys navigate steps, `Space`/arrows expand step output.

---

## Async loading

`AsyncViewModel` and `AsyncScreen` handle loading / success / error without boilerplate.

```kotlin
class MyViewModel : AsyncViewModel<MyData, MyEvent>() {
    init { load() }

    override fun onEvent(event: MyEvent) {
        if (event is MyEvent.Retry) load()
    }

    private fun load() = launchLoad {
        delay(500)          // suspend: fetch from network, disk, etc.
        fetchData()
    }
}

class MyScreen : AsyncScreen<MyData, MyEvent>() {
    override val viewModel = MyViewModel()

    override fun buildLoaded(data: MyData): Component =
        myWindow.render(data.toWindowState())
}
```

A spinner is shown automatically during loading; override `buildLoading` or `buildError` to customise.

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

Logs are kept in a 1000-entry in-memory ring buffer and viewable via `Ctrl+L`.

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

Generic undo/redo stack (used internally by `TextEditorWindow`).

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

### Theme

```kotlin
Theme.current                   // active ThemeColors
Theme.current.error             // Color for error text
```

Extend `ThemeColors` and assign `Theme.current` to provide a custom theme.

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
- `enableCtrlZ = true` is required in `runApp()` to use undo/redo in `TextEditorWindow`.
- Window volatile fields manage scroll/focus state in-place; they are not recomposed per frame.
- `DashboardCell` uses a render lambda rather than a typed `Window<S>` to avoid type erasure across heterogeneous cells.
- `PaginatedListWindow` creates its own `CoroutineScope`; no external scope is needed.
- `TabApp` uses `Alt+H`/`L` for tab navigation — `Ctrl+Tab` is avoided because it is intercepted by most terminals.
