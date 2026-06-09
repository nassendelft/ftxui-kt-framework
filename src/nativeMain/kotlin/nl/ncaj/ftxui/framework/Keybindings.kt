package nl.ncaj.ftxui.framework

import nl.ncaj.ftxui.FtxUIEvent
import nl.ncaj.ftxui.Key

fun FtxUIEvent.matches(keys: List<String>, chars: List<String> = emptyList()): Boolean {
    for (k in keys) {
        if (this.isKey(k)) return true
    }
    if (this is FtxUIEvent.Character) {
        for (c in chars) {
            if (this.character == c) return true
        }
    }
    return false
}

data class ListKeybindings(
    val moveUpKeys: List<String> = listOf(Key.ArrowUp),
    val moveUpChars: List<String> = listOf("k"),
    val moveDownKeys: List<String> = listOf(Key.ArrowDown),
    val moveDownChars: List<String> = listOf("j"),
    val pageUpKeys: List<String> = listOf(Key.PageUp, Key.CtrlU),
    val pageUpChars: List<String> = emptyList(),
    val pageDownKeys: List<String> = listOf(Key.PageDown, Key.CtrlD),
    val pageDownChars: List<String> = emptyList(),
    val homeKeys: List<String> = listOf(Key.Home),
    val homeChars: List<String> = listOf("g"),
    val endKeys: List<String> = listOf(Key.End),
    val endChars: List<String> = listOf("G"),
    val selectKeys: List<String> = listOf(Key.Return),
    val selectChars: List<String> = emptyList(),
    val searchKeys: List<String> = emptyList(),
    val searchChars: List<String> = listOf("/")
)

data class PagerKeybindings(
    val scrollUpKeys: List<String> = listOf(Key.ArrowUp),
    val scrollUpChars: List<String> = listOf("k"),
    val scrollDownKeys: List<String> = listOf(Key.ArrowDown),
    val scrollDownChars: List<String> = listOf("j"),
    val pageUpKeys: List<String> = listOf(Key.PageUp, Key.CtrlU),
    val pageUpChars: List<String> = emptyList(),
    val pageDownKeys: List<String> = listOf(Key.PageDown, Key.CtrlD),
    val pageDownChars: List<String> = emptyList(),
    val homeKeys: List<String> = listOf(Key.Home),
    val homeChars: List<String> = listOf("g"),
    val endKeys: List<String> = listOf(Key.End),
    val endChars: List<String> = listOf("G"),
    val searchKeys: List<String> = emptyList(),
    val searchChars: List<String> = listOf("/"),
    val nextMatchKeys: List<String> = emptyList(),
    val nextMatchChars: List<String> = listOf("n"),
    val prevMatchKeys: List<String> = emptyList(),
    val prevMatchChars: List<String> = listOf("N")
)

data class TableKeybindings(
    val moveUpKeys: List<String> = listOf(Key.ArrowUp),
    val moveUpChars: List<String> = listOf("k"),
    val moveDownKeys: List<String> = listOf(Key.ArrowDown),
    val moveDownChars: List<String> = listOf("j"),
    val pageUpKeys: List<String> = listOf(Key.PageUp, Key.CtrlU),
    val pageUpChars: List<String> = emptyList(),
    val pageDownKeys: List<String> = listOf(Key.PageDown, Key.CtrlD),
    val pageDownChars: List<String> = emptyList(),
    val homeKeys: List<String> = listOf(Key.Home),
    val homeChars: List<String> = listOf("g"),
    val endKeys: List<String> = listOf(Key.End),
    val endChars: List<String> = listOf("G"),
    val selectKeys: List<String> = listOf(Key.Return),
    val selectChars: List<String> = emptyList(),
    val sortKeys: List<String> = emptyList(),
    val sortChars: List<String> = listOf("s")
)

data class TreeKeybindings(
    val moveUpKeys: List<String> = listOf(Key.ArrowUp),
    val moveUpChars: List<String> = listOf("k"),
    val moveDownKeys: List<String> = listOf(Key.ArrowDown),
    val moveDownChars: List<String> = listOf("j"),
    val expandKeys: List<String> = listOf(Key.ArrowRight),
    val expandChars: List<String> = listOf("l"),
    val collapseKeys: List<String> = listOf(Key.ArrowLeft),
    val collapseChars: List<String> = listOf("h"),
    val selectKeys: List<String> = listOf(Key.Return),
    val selectChars: List<String> = emptyList()
)

data class StepProgressKeybindings(
    val moveUpKeys: List<String> = listOf(Key.ArrowUp),
    val moveUpChars: List<String> = listOf("k"),
    val moveDownKeys: List<String> = listOf(Key.ArrowDown),
    val moveDownChars: List<String> = listOf("j"),
    val expandKeys: List<String> = listOf(Key.ArrowRight),
    val expandChars: List<String> = listOf("l"),
    val collapseKeys: List<String> = listOf(Key.ArrowLeft),
    val collapseChars: List<String> = listOf("h")
)

data class FilePickerKeybindings(
    val moveUpKeys: List<String> = listOf(Key.ArrowUp),
    val moveUpChars: List<String> = listOf("k"),
    val moveDownKeys: List<String> = listOf(Key.ArrowDown),
    val moveDownChars: List<String> = listOf("j"),
    val pageUpKeys: List<String> = listOf(Key.PageUp, Key.CtrlU),
    val pageUpChars: List<String> = emptyList(),
    val pageDownKeys: List<String> = listOf(Key.PageDown, Key.CtrlD),
    val pageDownChars: List<String> = emptyList(),
    val homeKeys: List<String> = listOf(Key.Home),
    val homeChars: List<String> = listOf("g"),
    val endKeys: List<String> = listOf(Key.End),
    val endChars: List<String> = listOf("G"),
    val selectKeys: List<String> = listOf(Key.Return),
    val selectChars: List<String> = emptyList(),
    val goUpKeys: List<String> = listOf(Key.Backspace),
    val goUpChars: List<String> = listOf("h"),
    val searchKeys: List<String> = emptyList(),
    val searchChars: List<String> = listOf("/"),
    val toggleHiddenKeys: List<String> = emptyList(),
    val toggleHiddenChars: List<String> = listOf(".")
)

data class DashboardKeybindings(
    val focusNextKeys: List<String> = listOf(Key.Tab),
    val focusNextChars: List<String> = emptyList(),
    val focusPrevKeys: List<String> = listOf(Key.TabReverse),
    val focusPrevChars: List<String> = emptyList()
)

data class SplitKeybindings(
    val focusNextKeys: List<String> = listOf(Key.Tab),
    val focusNextChars: List<String> = emptyList(),
    val focusPrevKeys: List<String> = listOf(Key.TabReverse),
    val focusPrevChars: List<String> = emptyList()
)

data class TextEditorKeybindings(
    val undoKeys: List<String> = listOf(Key.CtrlZ),
    val undoChars: List<String> = emptyList(),
    val redoKeys: List<String> = listOf(Key.CtrlY),
    val redoChars: List<String> = emptyList()
)
