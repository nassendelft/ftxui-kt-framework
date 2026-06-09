package nl.ncaj.ftxui.framework

import nl.ncaj.ftxui.*

interface InputReceiver {
    fun onInput(event: FtxUIEvent): Boolean
}
