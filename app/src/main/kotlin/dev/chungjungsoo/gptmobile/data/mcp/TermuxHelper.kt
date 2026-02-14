package dev.chungjungsoo.gptmobile.data.mcp

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import java.io.File

object TermuxHelper {
    private const val TAG = "TermuxHelper"

    const val TERMUX_PACKAGE = "com.termux"
    const val TERMUX_FILES_DIR = "/data/data/com.termux/files"
    const val TERMUX_PREFIX = "$TERMUX_FILES_DIR/usr"
    const val TERMUX_HOME = "$TERMUX_FILES_DIR/home"
    const val TERMUX_BIN = "$TERMUX_PREFIX/bin"
    const val TERMUX_LIB = "$TERMUX_PREFIX/lib"

    data class TermuxStatus(
        val isInstalled: Boolean,
        val hasNodeJs: Boolean,
        val hasNpx: Boolean,
        val hasPython: Boolean,
        val hasPython3: Boolean,
        val hasUv: Boolean,
        val hasUvx: Boolean,
        val nodePath: String? = null,
        val npxPath: String? = null,
        val pythonPath: String? = null,
        val python3Path: String? = null,
        val uvPath: String? = null,
        val uvxPath: String? = null,
        val errorMessage: String? = null
    )

    fun checkTermuxStatus(context: Context): TermuxStatus {
        val isInstalled = isTermuxInstalled(context)
        if (!isInstalled) {
            return TermuxStatus(
                isInstalled = false,
                hasNodeJs = false,
                hasNpx = false,
                hasPython = false,
                hasPython3 = false,
                hasUv = false,
                hasUvx = false,
                errorMessage = "Termux is not installed"
            )
        }

        val binDir = File(TERMUX_BIN)
        if (!binDir.exists() || !binDir.canRead()) {
            return TermuxStatus(
                isInstalled = true,
                hasNodeJs = false,
                hasNpx = false,
                hasPython = false,
                hasPython3 = false,
                hasUv = false,
                hasUvx = false,
                errorMessage = "Cannot access Termux files. Please run Termux at least once."
            )
        }

        val nodePath = findExecutable("node")
        val npxPath = findExecutable("npx")
        val pythonPath = findExecutable("python")
        val python3Path = findExecutable("python3")
        val uvPath = findExecutable("uv")
        val uvxPath = findExecutable("uvx")

        return TermuxStatus(
            isInstalled = true,
            hasNodeJs = nodePath != null,
            hasNpx = npxPath != null,
            hasPython = pythonPath != null,
            hasPython3 = python3Path != null,
            hasUv = uvPath != null,
            hasUvx = uvxPath != null,
            nodePath = nodePath,
            npxPath = npxPath,
            pythonPath = pythonPath,
            python3Path = python3Path,
            uvPath = uvPath,
            uvxPath = uvxPath
        )
    }

    fun isTermuxInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun findExecutable(name: String): String? {
        val path = "$TERMUX_BIN/$name"
        val file = File(path)
        return if (file.exists() && file.canExecute()) {
            path
        } else {
            null
        }
    }

    fun getTermuxEnvironment(): Map<String, String> {
        return mapOf(
            "HOME" to TERMUX_HOME,
            "PREFIX" to TERMUX_PREFIX,
            "TMPDIR" to "$TERMUX_PREFIX/tmp",
            "PATH" to "$TERMUX_BIN:/system/bin:/system/xbin",
            "LD_LIBRARY_PATH" to TERMUX_LIB,
            "LANG" to "en_US.UTF-8",
            "TERM" to "xterm-256color",
            "COLORTERM" to "truecolor"
        )
    }

    fun resolveCommand(command: String, status: TermuxStatus): ResolvedCommand? {
        val lowerCommand = command.lowercase().trim()

        return when {
            lowerCommand == "npx" || lowerCommand.startsWith("npx ") -> {
                if (status.hasNpx) {
                    ResolvedCommand(
                        executable = status.npxPath!!,
                        useTermuxEnv = true
                    )
                } else {
                    null
                }
            }
            lowerCommand == "node" || lowerCommand.startsWith("node ") -> {
                if (status.hasNodeJs) {
                    ResolvedCommand(
                        executable = status.nodePath!!,
                        useTermuxEnv = true
                    )
                } else {
                    null
                }
            }
            lowerCommand == "python" || lowerCommand.startsWith("python ") -> {
                if (status.hasPython) {
                    ResolvedCommand(
                        executable = status.pythonPath!!,
                        useTermuxEnv = true
                    )
                } else {
                    null
                }
            }
            lowerCommand == "python3" || lowerCommand.startsWith("python3 ") -> {
                if (status.hasPython3) {
                    ResolvedCommand(
                        executable = status.python3Path!!,
                        useTermuxEnv = true
                    )
                } else {
                    null
                }
            }
            lowerCommand == "uv" || lowerCommand.startsWith("uv ") -> {
                if (status.hasUv) {
                    ResolvedCommand(
                        executable = status.uvPath!!,
                        useTermuxEnv = true
                    )
                } else {
                    null
                }
            }
            lowerCommand == "uvx" || lowerCommand.startsWith("uvx ") -> {
                if (status.hasUvx) {
                    ResolvedCommand(
                        executable = status.uvxPath!!,
                        useTermuxEnv = true
                    )
                } else {
                    null
                }
            }
            command.startsWith("/") -> {
                val file = File(command)
                if (file.exists() && file.canExecute()) {
                    ResolvedCommand(
                        executable = command,
                        useTermuxEnv = command.startsWith(TERMUX_PREFIX)
                    )
                } else {
                    null
                }
            }
            else -> {
                val termuxPath = findExecutable(command)
                if (termuxPath != null) {
                    ResolvedCommand(
                        executable = termuxPath,
                        useTermuxEnv = true
                    )
                } else {
                    ResolvedCommand(
                        executable = command,
                        useTermuxEnv = false
                    )
                }
            }
        }
    }

    fun getMissingDependencyMessage(command: String, status: TermuxStatus): String {
        val lowerCommand = command.lowercase().trim()

        if (!status.isInstalled) {
            return "Termux is required for local MCP servers. Please install Termux from F-Droid or GitHub."
        }

        return when {
            (lowerCommand == "npx" || lowerCommand.startsWith("npx ")) && !status.hasNpx -> {
                "Node.js is not installed in Termux. Open Termux and run: pkg install nodejs"
            }
            (lowerCommand == "node" || lowerCommand.startsWith("node ")) && !status.hasNodeJs -> {
                "Node.js is not installed in Termux. Open Termux and run: pkg install nodejs"
            }
            (lowerCommand == "python" || lowerCommand.startsWith("python ")) && !status.hasPython -> {
                "Python is not installed in Termux. Open Termux and run: pkg install python"
            }
            (lowerCommand == "python3" || lowerCommand.startsWith("python3 ")) && !status.hasPython3 -> {
                "Python 3 is not installed in Termux. Open Termux and run: pkg install python"
            }
            (lowerCommand == "uv" || lowerCommand.startsWith("uv ")) && !status.hasUv -> {
                "uv is not installed in Termux. Open Termux and run: pip install uv"
            }
            (lowerCommand == "uvx" || lowerCommand.startsWith("uvx ")) && !status.hasUvx -> {
                "uvx is not installed in Termux. Open Termux and run: pip install uv"
            }
            else -> {
                "Command '$command' not found. Make sure it's installed in Termux."
            }
        }
    }

    data class ResolvedCommand(
        val executable: String,
        val useTermuxEnv: Boolean
    )
}
