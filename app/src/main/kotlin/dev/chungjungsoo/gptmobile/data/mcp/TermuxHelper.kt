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
        Log.d(TAG, "=== TermuxHelper.checkTermuxStatus START ===")
        
        val isInstalled = isTermuxInstalled(context)
        Log.d(TAG, "isTermuxInstalled=$isInstalled")
        
        if (!isInstalled) {
            Log.w(TAG, "Termux NOT installed - returning error state")
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

        Log.i(TAG, "Termux IS installed, checking bin directory...")
        val binDir = File(TERMUX_BIN)
        Log.d(TAG, "BIN dir exists=${binDir.exists()}, canRead=${binDir.canRead()}, path=$TERMUX_BIN")
        
        if (!binDir.exists() || !binDir.canRead()) {
            Log.w(TAG, "Cannot access Termux bin directory")
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

        Log.i(TAG, "=== TermuxStatus Result ===")
        Log.i(TAG, "nodePath=$nodePath, npxPath=$npxPath")
        Log.i(TAG, "pythonPath=$pythonPath, python3Path=$python3Path")
        Log.i(TAG, "uvPath=$uvPath, uvxPath=$uvxPath")
        
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
        Log.d(TAG, "isTermuxInstalled check #1: trying package manager...")
        
        // First try: check package manager for Termux package
        try {
            context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
            Log.d(TAG, "isTermuxInstalled: found via package manager!")
            return true
        } catch (e: PackageManager.NameNotFoundException) {
            Log.d(TAG, "isTermuxInstalled: NOT found via package manager")
        }
        
        // Fallback: check if Termux files directory exists
        // This can detect Termux even if package detection fails due to permissions
        Log.d(TAG, "isTermuxInstalled check #2: trying files directory...")
        val termuxFiles = File(TERMUX_FILES_DIR)
        val filesDirExists = termuxFiles.exists() && termuxFiles.isDirectory
        Log.d(TAG, "isTermuxInstalled: files dir exists=$filesDirExists, path=$TERMUX_FILES_DIR")
        
        if (filesDirExists) {
            val usrDir = File(TERMUX_PREFIX)
            Log.d(TAG, "isTermuxInstalled: usr dir exists=${usrDir.exists()}")
        }
        
        return filesDirExists
    }

    fun findExecutable(name: String): String? {
        val path = "$TERMUX_BIN/$name"
        val file = File(path)
        val exists = file.exists()
        val canExecute = if (exists) file.canExecute() else false
        Log.d(TAG, "findExecutable($name): path=$path, exists=$exists, canExecute=$canExecute")
        
        return if (exists && canExecute) {
            Log.i(TAG, "Found executable: $path")
            path
        } else {
            Log.w(TAG, "NOT found or not executable: $path")
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
        Log.d(TAG, "=== resolveCommand START ===")
        Log.d(TAG, "command='$command'")
        Log.d(TAG, "status.isInstalled=${status.isInstalled}, status.hasNpx=${status.hasNpx}, status.hasNodeJs=${status.hasNodeJs}")
        Log.d(TAG, "status.errorMessage=${status.errorMessage}")
        
        val lowerCommand = command.lowercase().trim()
        Log.d(TAG, "lowerCommand='$lowerCommand'")

        return when {
            lowerCommand == "npx" || lowerCommand.startsWith("npx ") -> {
                Log.d(TAG, "Checking for npx... status.hasNpx=${status.hasNpx}, npxPath=${status.npxPath}")
                if (status.hasNpx) {
                    Log.i(TAG, "Resolved to npx: ${status.npxPath}")
                    ResolvedCommand(
                        executable = status.npxPath!!,
                        useTermuxEnv = true
                    )
                } else {
                    Log.w(TAG, "npx not found in Termux")
                    null
                }
            }
            lowerCommand == "node" || lowerCommand.startsWith("node ") -> {
                Log.d(TAG, "Checking for node... status.hasNodeJs=${status.hasNodeJs}, nodePath=${status.nodePath}")
                if (status.hasNodeJs) {
                    Log.i(TAG, "Resolved to node: ${status.nodePath}")
                    ResolvedCommand(
                        executable = status.nodePath!!,
                        useTermuxEnv = true
                    )
                } else {
                    Log.w(TAG, "node not found in Termux")
                    null
                }
            }
            lowerCommand == "python" || lowerCommand.startsWith("python ") -> {
                Log.d(TAG, "Checking for python... status.hasPython=${status.hasPython}, pythonPath=${status.pythonPath}")
                if (status.hasPython) {
                    Log.i(TAG, "Resolved to python: ${status.pythonPath}")
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
