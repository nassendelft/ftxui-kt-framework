package nl.ncaj.ftxui.framework

sealed class Dialog {
    data class Alert(
        val title: String,
        val message: String,
        val onDismiss: () -> Unit = {},
    ) : Dialog()

    data class Confirm(
        val title: String,
        val message: String,
        val onConfirm: () -> Unit,
        val onCancel: () -> Unit = {},
    ) : Dialog()

    data class Prompt(
        val title: String,
        val placeholder: String = "",
        val maxLength: Int = 40,
        val onSubmit: (String) -> Unit,
        val onCancel: () -> Unit = {},
    ) : Dialog()
}
