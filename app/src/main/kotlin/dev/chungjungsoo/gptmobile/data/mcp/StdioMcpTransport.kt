package dev.chungjungsoo.gptmobile.data.mcp

import android.content.Context
import android.util.Log
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import io.modelcontextprotocol.kotlin.sdk.shared.TransportSendOptions
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

class StdioMcpTransport(
    private val command: String,
    private val args: List<String> = emptyList(),
    private val env: Map<String, String> = emptyMap(),
    private val workingDir: String? = null,
    private val context: Context? = null
) : Transport {

    private var processHandle: ProcessHandle? = null
    private var readerJob: Job? = null
    private var scope: CoroutineScope? = null
    private var nodejsExtracted = false
    private var closeNotified = false

    private fun ensureNodejsExtracted(appFilesDir: String) {
        if (nodejsExtracted || context == null) return
        
        val nodejsDir = java.io.File(appFilesDir, "nodejs")
        
        // Check if already extracted with correct version
        val versionFile = java.io.File(nodejsDir, ".version")
        val expectedVersion = "5"  // Increment when bundled libs change
        
        if (versionFile.exists() && versionFile.readText() == expectedVersion) {
            Log.d(TAG, "Bundled nodejs already extracted (version $expectedVersion)")
            nodejsExtracted = true
            return
        }
        
        try {
            Log.d(TAG, "Extracting bundled nodejs from assets to $nodejsDir...")
            nodejsDir.mkdirs()
            
            // Copy assets/nodejs/* to filesDir/nodejs/
            copyAssetFolder("nodejs", nodejsDir)
            
            // Write version file
            java.io.File(nodejsDir, ".version").writeText(expectedVersion)
            
            nodejsExtracted = true
            Log.i(TAG, "Extracted bundled nodejs successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract bundled nodejs: ${e.message}")
        }
    }
    
    private fun copyAssetFolder(assetPath: String, destDir: java.io.File) {
        try {
            val list = context?.assets?.list(assetPath)
            if (list == null || list.isEmpty()) return
            
            list.forEach { name ->
                val assetFullPath = "$assetPath/$name"
                val destFile = java.io.File(destDir, name)
                
                // Check if it's a directory by trying to list its contents
                val isDir = context?.assets?.list(assetFullPath)?.isNotEmpty() == true
                
                if (isDir) {
                    destFile.mkdirs()
                    copyAssetFolder(assetFullPath, destFile)
                } else {
                    // It's a file, copy it
                    destFile.parentFile?.mkdirs()
                    context?.assets?.open(assetFullPath)?.use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error copying asset folder $assetPath: ${e.message}")
        }
    }

    /**
     * Plan C: Install npm package and find its JS entry point.
     * Returns the path to the main JS file that should be run with node.
     */
    private fun installAndResolvePackage(
        packageName: String,
        nodePath: String,
        npmJsPath: String,
        installDir: String,
        envVars: Map<String, String>
    ): String? {
        Log.i(TAG, "Plan C: Installing package '$packageName' to $installDir")
        
        val nodeModulesDir = java.io.File(installDir, "node_modules")
        nodeModulesDir.mkdirs()
        
        // Run: node npm.js install <package> --prefix <installDir>
        val envArray = envVars.map { "${it.key}=${it.value}" }.toTypedArray()
        val installArgs = arrayOf(
            "--",
            npmJsPath,
            "install",
            packageName,
            "--prefix",
            installDir
        )
        
        Log.i(TAG, "Running npm install: $nodePath ${installArgs.joinToString(" ")}")
        
        val result = NativeProcess.createSubprocess(
            cmd = nodePath,
            cwd = installDir,
            args = installArgs,
            envVars = envArray
        )
        
        if (result == null) {
            Log.e(TAG, "Failed to start npm install process")
            return null
        }
        
        val pid = result[0]
        val stdinFd = result[1]
        val stdoutFd = result[2]
        
        // Read output for debugging
        val buffer = ByteArray(4096)
        val output = StringBuilder()
        try {
            while (true) {
                val bytesRead = NativeProcess.read(stdoutFd, buffer)
                if (bytesRead <= 0) break
                output.append(String(buffer, 0, bytesRead, Charsets.UTF_8))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error reading npm install output: ${e.message}")
        }
        
        Log.d(TAG, "npm install output: $output")
        
        // Close file descriptors
        NativeProcess.closeFd(stdinFd)
        NativeProcess.closeFd(stdoutFd)

        val exitCode = NativeProcess.waitFor(pid)
        if (exitCode != 0) {
            Log.e(TAG, "npm install failed with exitCode=$exitCode")
            return null
        }
        
        // Now find the package's bin entry
        return resolvePackageBinEntry(packageName, nodeModulesDir)
    }
    
    /**
     * Resolve the JS entry point from a package's package.json bin field.
     */
    private fun resolvePackageBinEntry(packageName: String, nodeModulesDir: java.io.File): String? {
        // Handle scoped packages like @upstash/context7-mcp
        val packageDir = java.io.File(nodeModulesDir, packageName)
        val packageJsonFile = java.io.File(packageDir, "package.json")
        
        if (!packageJsonFile.exists()) {
            Log.e(TAG, "package.json not found at: ${packageJsonFile.absolutePath}")
            return null
        }
        
        try {
            val packageJson = packageJsonFile.readText()
            val jsonObj = Json.parseToJsonElement(packageJson).jsonObject
            
            // Try to find bin entry
            val binEntry = jsonObj["bin"]
            if (binEntry != null) {
                val binPath = when {
                    binEntry is JsonPrimitive -> {
                        // bin is a string: "bin": "./dist/index.js"
                        binEntry.content
                    }
                    binEntry is JsonObject -> {
                        // bin is an object: "bin": { "context7-mcp": "./dist/index.js" }
                        // Get the first entry or try to match package name
                        val shortName = packageName.substringAfterLast("/")
                        (binEntry[shortName] ?: binEntry.values.firstOrNull())
                            ?.let { (it as? JsonPrimitive)?.content }
                    }
                    else -> null
                }
                
                if (binPath != null) {
                    val resolvedPath = java.io.File(packageDir, binPath).canonicalPath
                    Log.i(TAG, "Resolved bin entry: $resolvedPath")
                    return resolvedPath
                }
            }
            
            // Fallback: try main field
            val mainEntry = jsonObj["main"]?.let { 
                (it as? JsonPrimitive)?.content 
            }
            if (mainEntry != null) {
                val resolvedPath = java.io.File(packageDir, mainEntry).canonicalPath
                Log.i(TAG, "Resolved main entry: $resolvedPath")
                return resolvedPath
            }
            
            Log.e(TAG, "No bin or main entry found in package.json")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse package.json: ${e.message}", e)
            return null
        }
    }

    private var _onClose: (() -> Unit)? = null
    private var _onError: ((Throwable) -> Unit)? = null
    private var _onMessage: (suspend (JSONRPCMessage) -> Unit)? = null

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    override fun onClose(block: () -> Unit) {
        _onClose = block
    }

    override fun onError(block: (Throwable) -> Unit) {
        _onError = block
    }

    override fun onMessage(block: suspend (JSONRPCMessage) -> Unit) {
        _onMessage = block
    }

    override suspend fun start() {
        Log.i(TAG, "=== StdioMcpTransport.start START ===")
        Log.i(TAG, "STDIO transport: command='$command' args='${args.joinToString(", ")}'")
        closeNotified = false

        val (baseCommand, inlineArgs) = splitCommandWithInlineArgs(command)
        val providedArgs = inlineArgs + args

        // Setup bundled nodejs directory if needed
        val appFilesDir = context?.filesDir?.absolutePath ?: "/data/data/dev.chungjungsoo.gptmobile/files"
        ensureNodejsExtracted(appFilesDir)
        
        val termuxStatus = context?.let { 
            Log.d(TAG, "Checking Termux status...")
            TermuxHelper.checkTermuxStatus(it).also { status ->
                Log.d(TAG, "TermuxStatus: isInstalled=${status.isInstalled}, error=${status.errorMessage}")
            }
        }
        Log.d(TAG, "termuxStatus is null?=${termuxStatus == null}")
        
        val resolvedCommand = termuxStatus?.let { 
            TermuxHelper.resolveCommand(baseCommand, it).also { resolved ->
                Log.d(TAG, "resolvedCommand is null?=${resolved == null}")
            }
        }

        Log.d(TAG, "resolvedCommand=$resolvedCommand")

        val actualCommand: String
        val actualEnv: Map<String, String>
        val actualWorkingDir: String
        val finalArgs: List<String>

        // Get the bundled node from native library directory
        val bundledNodePath = context?.applicationInfo?.nativeLibraryDir?.let { "$it/libnode.so" }
        Log.d(TAG, "Bundled node path: $bundledNodePath")
        
        // Use bundled node for npm/npx commands
        val useBundledNode = bundledNodePath != null && (
            baseCommand == "npx" ||
                baseCommand == "node" ||
                baseCommand == "npm" ||
                baseCommand == "npx.exe" ||
                baseCommand == "npm.exe"
        )
        
        if (useBundledNode) {
            Log.i(TAG, "Using BUNDLED node from: $bundledNodePath")

            val subCommand = baseCommand
            val allArgs = providedArgs
            
            // Use the bundled node to run corepack-based npm/npx
            actualCommand = bundledNodePath!!
            
            // The nodejs was extracted to filesDir/nodejs/
            // Use corepack's npm.js or npx.js from the extracted assets
            val nodejsDir = "$appFilesDir/nodejs"
            val corepackDir = "$nodejsDir/lib/node_modules/corepack"
            val npmJsPath = "$corepackDir/dist/npm.js"
            
            // Set up environment for bundled node
            val nativeLibDir = context?.applicationInfo?.nativeLibraryDir ?: ""
            val mcpInstallDir = "$appFilesDir/mcp-packages"
            java.io.File(mcpInstallDir).mkdirs()
            
            actualEnv = mapOf(
                "HOME" to appFilesDir,
                "TMPDIR" to "$appFilesDir/tmp",
                "TERM" to "xterm-256color",
                "PATH" to "$nodejsDir/shims:$nodejsDir/bin:$appFilesDir/npm-global/bin:/system/bin:/system/xbin",
                "NODE_PATH" to "$mcpInstallDir/node_modules:$corepackDir",
                "LD_LIBRARY_PATH" to "$nativeLibDir:$appFilesDir/nodejs/lib",
                "COREPACK_ENABLE_DOWNLOAD_PROMPT" to "0",
                "COREPACK_AUTO_INSTALL" to "false",
                "npm_config_cache" to "$appFilesDir/npm-cache",
                "npm_config_globalconfig" to "$appFilesDir/npm-global/npmrc",
                "npm_config_prefix" to "$appFilesDir/npm-global"
            ) + env
            
            actualWorkingDir = appFilesDir
            
            // Plan C: For npx commands, install package first then run JS directly
            if (subCommand == "npx" || subCommand == "npx.exe") {
                // Extract package name from args (skip -y flag)
                val filteredArgs = allArgs.filter { it != "-y" }
                val packageName = filteredArgs.firstOrNull { !it.startsWith("-") }
                
                if (packageName != null) {
                    Log.i(TAG, "Plan C: Converting npx to npm install + node for package: $packageName")
                    
                    // Get remaining args (after package name) to pass to the MCP server
                    val packageIndex = filteredArgs.indexOf(packageName)
                    val serverArgs = if (packageIndex >= 0 && packageIndex < filteredArgs.size - 1) {
                        filteredArgs.drop(packageIndex + 1)
                    } else {
                        emptyList()
                    }
                    
                    // Check if package is already installed
                    val nodeModulesDir = java.io.File(mcpInstallDir, "node_modules")
                    var entryPoint = resolvePackageBinEntry(packageName, nodeModulesDir)
                    
                    if (entryPoint == null || !java.io.File(entryPoint).exists()) {
                        // Install the package
                        entryPoint = installAndResolvePackage(
                            packageName = packageName,
                            nodePath = bundledNodePath,
                            npmJsPath = npmJsPath,
                            installDir = mcpInstallDir,
                            envVars = actualEnv
                        )
                    }
                    
                    if (entryPoint != null && java.io.File(entryPoint).exists()) {
                        // Run node directly on the JS entry point
                        finalArgs = listOf("--", entryPoint) + serverArgs
                        Log.i(TAG, "Plan C: Running node directly on: $entryPoint")
                        Log.i(TAG, "Plan C: Server args: $serverArgs")
                    } else {
                        Log.e(TAG, "Plan C: Failed to resolve entry point for $packageName")
                        throw RuntimeException("Failed to install or resolve MCP package: $packageName")
                    }
                } else {
                    Log.e(TAG, "Plan C: No package name found in npx args: $allArgs")
                    throw RuntimeException("No package name found in npx command")
                }
            } else if (subCommand == "npm" || subCommand == "npm.exe") {
                // For npm commands, use npm.js directly
                finalArgs = listOf("--", npmJsPath) + allArgs
            } else {
                // For node commands, run the script directly
                finalArgs = listOf("--") + allArgs
            }
            
            Log.i(TAG, "Executing BUNDLED node: $actualCommand")
            Log.i(TAG, "Args: ${finalArgs.joinToString(" ")}")
            Log.i(TAG, "Working dir: $actualWorkingDir")
        }
        else if (resolvedCommand != null) {
            Log.i(TAG, "Resolved command: ${resolvedCommand.executable}, useTermuxEnv=${resolvedCommand.useTermuxEnv}")

            actualCommand = resolvedCommand.executable
            finalArgs = providedArgs
            
            actualEnv = if (resolvedCommand.useTermuxEnv) {
                TermuxHelper.getTermuxEnvironment() + env
            } else {
                env
            }
            // Use /data/local/tmp as working dir since Termux home may not exist yet
            actualWorkingDir = workingDir ?: "/data/local/tmp"
            
            Log.i(TAG, "Resolved command via Termux: $actualCommand (useTermuxEnv=${resolvedCommand.useTermuxEnv}), workingDir=$actualWorkingDir")
        } else {
            Log.w(TAG, "resolvedCommand is NULL!")
            Log.d(TAG, "termuxStatus=$termuxStatus, command='$command', startsWith('/'=${command.startsWith("/")}")

            val termuxOnlyCommands = setOf("python", "python3", "uv", "uvx")
            val needsTermux = baseCommand in termuxOnlyCommands
            if (needsTermux && termuxStatus != null && !termuxStatus.isInstalled) {
                val errorMsg = TermuxHelper.getMissingDependencyMessage(baseCommand, termuxStatus)
                Log.e(TAG, "ERROR: $errorMsg")
                throw RuntimeException(errorMsg)
            }
            Log.i(TAG, "Using raw command without Termux resolution")
            actualCommand = baseCommand
            actualEnv = env
            actualWorkingDir = workingDir ?: "/data/local/tmp"
            finalArgs = providedArgs
        }

        val envArray = actualEnv.map { "${it.key}=${it.value}" }.toTypedArray()
        val argsArray = finalArgs.toTypedArray()

        Log.i(TAG, "Spawning process: $actualCommand")
        Log.i(TAG, "Args: ${finalArgs.joinToString(", ")}")
        Log.i(TAG, "Working dir: $actualWorkingDir")
        Log.d(TAG, "Environment: ${actualEnv.keys.joinToString(", ")}")

        val result = NativeProcess.createSubprocess(
            cmd = actualCommand,
            cwd = actualWorkingDir,
            args = argsArray,
            envVars = envArray
        ) ?: throw RuntimeException("Failed to create subprocess for: $actualCommand")

        processHandle = ProcessHandle(
            pid = result[0],
            stdinFd = result[1],
            stdoutFd = result[2]
        )

        Log.i(TAG, "Process started with PID: ${processHandle?.pid}")

        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        readerJob = scope?.launch {
            val buffer = ByteArray(8192)
            val lineBuffer = StringBuilder()

            try {
                while (isActive && processHandle?.isAlive() == true) {
                    val bytesRead = NativeProcess.read(processHandle!!.stdoutFd, buffer)
                    if (bytesRead <= 0) {
                        Log.i(TAG, "EOF or error reading from process (bytesRead=$bytesRead)")
                        break
                    }

                    val text = String(buffer, 0, bytesRead, Charsets.UTF_8)
                    lineBuffer.append(text)

                    while (true) {
                        val newlineIndex = lineBuffer.indexOf('\n')
                        if (newlineIndex < 0) break

                        val line = lineBuffer.substring(0, newlineIndex).trim()
                        lineBuffer.delete(0, newlineIndex + 1)

                        if (line.isNotBlank()) {
                            // Plan A: Filter non-JSON lines (npm warnings, etc.)
                            if (!line.startsWith("{")) {
                                Log.d(TAG, "Skipping non-JSON line: $line")
                                continue
                            }
                            try {
                                val message = json.decodeFromString<JSONRPCMessage>(line)
                                _onMessage?.invoke(message)
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to parse JSON-RPC message: $line", e)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in reader coroutine", e)
                _onError?.invoke(e)
            } finally {
                Log.i(TAG, "Reader coroutine finished")
                notifyCloseOnce()
            }
        }
    }

    override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) {
        val handle = processHandle ?: throw IllegalStateException("Transport not started")

        if (!handle.isAlive()) {
            throw IllegalStateException("Process is not alive")
        }

        val jsonString = json.encodeToString(message) + "\n"
        val bytes = jsonString.toByteArray(Charsets.UTF_8)

        val written = NativeProcess.write(handle.stdinFd, bytes)
        if (written != bytes.size) {
            throw RuntimeException("Failed to write all bytes (wrote $written of ${bytes.size})")
        }

        Log.d(TAG, "Sent message: ${jsonString.take(200)}...")
    }

    override suspend fun close() {
        Log.i(TAG, "Closing STDIO transport")

        readerJob?.cancel()
        readerJob = null

        processHandle?.let { handle ->
            handle.terminate()
            handle.close()
        }
        processHandle = null

        scope?.cancel()
        scope = null

        notifyCloseOnce()
    }

    private fun splitCommandWithInlineArgs(rawCommand: String): Pair<String, List<String>> {
        val parts = rawCommand.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
        return if (parts.isEmpty()) {
            rawCommand to emptyList()
        } else {
            parts.first() to parts.drop(1)
        }
    }

    @Synchronized
    private fun notifyCloseOnce() {
        if (closeNotified) {
            return
        }
        closeNotified = true
        _onClose?.invoke()
    }

    companion object {
        private const val TAG = "StdioMcpTransport"
    }
}
