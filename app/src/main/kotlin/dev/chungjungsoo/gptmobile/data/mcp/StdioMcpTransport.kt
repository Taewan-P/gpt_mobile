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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
    private val outputChannel = Channel<String>(Channel.UNLIMITED)

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
        Log.i(TAG, "Starting STDIO transport: $command ${args.joinToString(" ")}")

        val termuxStatus = context?.let { TermuxHelper.checkTermuxStatus(it) }
        val resolvedCommand = termuxStatus?.let { TermuxHelper.resolveCommand(command, it) }

        val actualCommand: String
        val actualEnv: Map<String, String>
        val actualWorkingDir: String

        if (resolvedCommand != null) {
            actualCommand = resolvedCommand.executable
            actualEnv = if (resolvedCommand.useTermuxEnv) {
                TermuxHelper.getTermuxEnvironment() + env
            } else {
                env
            }
            actualWorkingDir = workingDir ?: if (resolvedCommand.useTermuxEnv) {
                TermuxHelper.TERMUX_HOME
            } else {
                "/data/local/tmp"
            }
            Log.i(TAG, "Resolved command via Termux: $actualCommand (useTermuxEnv=${resolvedCommand.useTermuxEnv})")
        } else {
            if (termuxStatus != null && !command.startsWith("/")) {
                val errorMsg = TermuxHelper.getMissingDependencyMessage(command, termuxStatus)
                Log.e(TAG, errorMsg)
                throw RuntimeException(errorMsg)
            }
            actualCommand = command
            actualEnv = env
            actualWorkingDir = workingDir ?: "/data/local/tmp"
        }

        val envArray = actualEnv.map { "${it.key}=${it.value}" }.toTypedArray()
        val argsArray = (listOf(actualCommand) + args).toTypedArray()

        Log.i(TAG, "Spawning process: $actualCommand")
        Log.i(TAG, "Args: ${args.joinToString(", ")}")
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
                _onClose?.invoke()
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

        outputChannel.close()
        _onClose?.invoke()
    }

    companion object {
        private const val TAG = "StdioMcpTransport"
    }
}
