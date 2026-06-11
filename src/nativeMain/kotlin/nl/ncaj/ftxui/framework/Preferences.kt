package nl.ncaj.ftxui.framework

import kotlinx.cinterop.*
import platform.posix.*

// `mode_t` resolves to integer types of different bit widths on macOS vs Linux,
// so creating the directory is implemented per-target with a width-safe signature.
internal expect fun makeDirectory(path: String)

class Preferences internal constructor(appName: String) {
    private val filePath: String
    private val data: MutableMap<String, String> = mutableMapOf()

    init {
        val sanitizedAppName = appName.replace(" ", "-").replace(Regex("[^a-zA-Z0-9_-]"), "")
        val dir = getPreferencesDirectory(sanitizedAppName)
        makeDirectory(dir) // 0755
        filePath = "$dir/prefs.properties"
        load()
    }

    fun getString(key: String, default: String = ""): String = data[key] ?: default
    fun setString(key: String, value: String) { data[key] = value }

    fun getInt(key: String, default: Int = 0): Int = data[key]?.toIntOrNull() ?: default
    fun setInt(key: String, value: Int) { data[key] = value.toString() }

    fun getBoolean(key: String, default: Boolean = false): Boolean =
        when (data[key]) { "true" -> true; "false" -> false; else -> default }
    fun setBoolean(key: String, value: Boolean) { data[key] = value.toString() }

    fun save() {
        if (filePath.isEmpty()) return
        val content = data.entries.joinToString("\n") { "${it.key}=${it.value}" }
        writeFileText(filePath, content)
    }

    private fun load() {
        val text = readFileText(filePath) ?: return
        for (line in text.lines()) {
            val eq = line.indexOf('=')
            if (eq < 1) continue
            data[line.substring(0, eq)] = line.substring(eq + 1)
        }
    }

    private fun readFileText(path: String): String? {
        val file = fopen(path, "r") ?: return null
        val sb = StringBuilder()
        memScoped {
            val buffer = allocArray<ByteVar>(4096)
            while (fgets(buffer, 4096, file) != null) {
                sb.append(buffer.toKString())
            }
        }
        fclose(file)
        return sb.toString()
    }

    private fun writeFileText(path: String, content: String) {
        val file = fopen(path, "w") ?: return
        fputs(content, file)
        fclose(file)
    }

    private fun getPreferencesDirectory(appName: String): String {
        // 1. Check for explicit XDG environment variable (preferred on Linux)
        val xdgConfig = getenv("XDG_CONFIG_HOME")?.toKString()
        if (!xdgConfig.isNullOrBlank()) {
            return "$xdgConfig/$appName"
        }

        // 2. Fall back to standard $HOME variable (works on both Linux and macOS)
        val home = getenv("HOME")?.toKString()
            ?: throw IllegalStateException("Environment variable HOME is not set.")

        // Both OS platforms will now cleanly use ~/.config/your_app/
        return "$home/.config/$appName"
    }
}
