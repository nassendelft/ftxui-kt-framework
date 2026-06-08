package nl.ncaj.ftxui.framework

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.posix.S_IFDIR
import platform.posix.S_IFMT
import platform.posix.mkdir
import platform.posix.stat

@OptIn(ExperimentalForeignApi::class)
internal actual fun statPath(path: String): PathInfo? = memScoped {
    val st = alloc<stat>()
    if (stat(path, st.ptr) != 0) return null
    val isDir = st.st_mode.convert<UInt>().toInt() and S_IFMT == S_IFDIR
    PathInfo(isDir, st.st_size)
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun makeDirectory(path: String) {
    mkdir(path, 493u.convert()) // 0755
}
