package nl.ncaj.ftxui.framework

import nl.ncaj.ftxui.BorderStyle
import nl.ncaj.ftxui.Color

// ---------------------------------------------------------------------------
// Layer 1 — Generic Design Tokens
//
// Semantic color roles that every component references by default.
// Extend this class to add app-specific tokens; use Theme.ext<MyTheme>()
// to access them from framework code.
// ---------------------------------------------------------------------------

open class ThemeColors(
    // -- Utility / status --
    val accent: Color = Color.Cyan,
    val error: Color = Color.Red,
    val success: Color = Color.Green,
    val warning: Color = Color.Yellow,
    val info: Color = Color.Cyan,

    // -- Layout / surface --
    val primary: Color = Color.Cyan,
    val onPrimary: Color = Color.Black,
    val surface: Color = Color.Default,
    val onSurface: Color = Color.Default,
    val background: Color = Color.Default,

    // -- Borders --
    val border: Color = Color.Default,
    val focusedBorder: Color = Color.Cyan,
    val borderStyle: BorderStyle = BorderStyle.Light,
    val focusedBorderStyle: BorderStyle = BorderStyle.Heavy,

    // -- Muted / disabled --
    val muted: Color = Color.GrayDark,
    val mutedForeground: Color = Color.GrayLight,

    // -- Scroll indicators --
    val scrollThumb: Color = Color.GrayLight,
    val scrollTrack: Color = Color.Default,

    // -- File / directory distinction --
    val directoryColor: Color = Color.Cyan,
    val fileColor: Color = Color.Default,
)

// ---------------------------------------------------------------------------
// Layer 2 — Component Style Blocks
//
// Each style class holds the colors, border styles, and text decorations
// relevant to its component.  Defaults resolve to Theme.current tokens
// at construction time (via `with { ... }` or the constructor defaults).
// ---------------------------------------------------------------------------

/**
 * Controls how items are rendered in [ListView] and related list-based views.
 */
data class ListStyle(
    val focusedItemForeground: Color? = null,
    val focusedItemBackground: Color? = null,
    val headerForeground: Color? = null,
    val scrollThumb: Color? = null,
    val searchHighlight: Color? = null,
)

/**
 * Controls how rows and headers are rendered in [TableView].
 */
data class TableStyle(
    val headerForeground: Color? = null,
    val focusedRowForeground: Color? = null,
    val focusedRowBackground: Color? = null,
    val sortIndicatorColor: Color? = null,
    val scrollThumb: Color? = null,
    val borderStyle: BorderStyle? = null,
)

/**
 * Controls how nodes are rendered in [TreeView].
 */
data class TreeStyle(
    val focusedNodeForeground: Color? = null,
    val focusedNodeBackground: Color? = null,
    val expandedIcon: String = "▾ ",
    val collapsedIcon: String = "▸ ",
    val leafIndent: String = "  ",
    val scrollThumb: Color? = null,
)

/**
 * Controls how the two panes and their titles are rendered in [SplitView].
 */
data class SplitStyle(
    val activeTitleForeground: Color? = null,
    val inactiveTitleForeground: Color? = null,
    val borderStyle: BorderStyle? = null,
    val activeBorderStyle: BorderStyle? = null,
)

/**
 * Controls how cells and their titles are rendered in [DashboardView].
 */
data class DashboardStyle(
    val focusedTitleForeground: Color? = null,
    val unfocusedTitleForeground: Color? = null,
    val borderStyle: BorderStyle? = null,
    val focusedBorderStyle: BorderStyle? = null,
)

/**
 * Controls the pager view's search highlight and status text.
 */
data class PagerStyle(
    val searchHighlight: Color? = null,
    val lineNumberColor: Color? = null,
    val scrollThumb: Color? = null,
)

/**
 * Controls the step progress indicator rendering.
 */
data class StepProgressStyle(
    val pendingColor: Color? = null,
    val runningColor: Color? = null,
    val doneColor: Color? = null,
    val failedColor: Color? = null,
    val skippedColor: Color? = null,
)

/**
 * Controls the tab bar rendering in [TabApp].
 */
data class TabBarStyle(
    val activeTabForeground: Color? = null,
    val inactiveTabForeground: Color? = null,
    val borderStyle: BorderStyle? = null,
    val borderColor: Color? = null,
)

/**
 * Controls the file picker rendering.
 */
data class FilePickerStyle(
    val directoryColor: Color? = null,
    val fileColor: Color? = null,
    val pathColor: Color? = null,
    val scrollThumb: Color? = null,
)

// ---------------------------------------------------------------------------
// Theme singleton
// ---------------------------------------------------------------------------

object Theme {
    var current: ThemeColors = ThemeColors()

    inline fun <reified T : ThemeColors> ext(): T =
        current as? T ?: error("Theme.current is not ${T::class.simpleName}")
}

// ---------------------------------------------------------------------------
// Resolved-style helpers
//
// Each component calls these at render time to resolve "null = use global".
// ---------------------------------------------------------------------------

/** Resolve a nullable style color to the given fallback (typically a Theme token). */
internal fun Color?.or(fallback: Color): Color = this ?: fallback

/** Resolve a nullable border style to the given fallback. */
internal fun BorderStyle?.or(fallback: BorderStyle): BorderStyle = this ?: fallback
