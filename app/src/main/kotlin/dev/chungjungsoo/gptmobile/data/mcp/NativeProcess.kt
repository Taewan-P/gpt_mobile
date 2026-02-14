package dev.chungjungsoo.gptmobile.data.mcp

import android.util.Log

object NativeProcess {
    private const val TAG = "NativeProcess"

    init {
        try {
            System.loadLibrary("mcp_process")
            Log.i(TAG, "Native library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library", e)
        }
    }

    @JvmStatic
    external fun createSubprocess(
        cmd: String,
        cwd: String,
        args: Array<String>?,
        envVars: Array<String>?
    ): IntArray?

    @JvmStatic
    external fun writeBytes(fd: Int, data: ByteArray, offset: Int, length: Int): Int

    @JvmStatic
    external fun readBytes(fd: Int, buffer: ByteArray, offset: Int, length: Int): Int

    @JvmStatic
    external fun waitFor(pid: Int): Int

    @JvmStatic
    external fun closeFd(fd: Int)

    @JvmStatic
    external fun sendSignal(pid: Int, signal: Int)

    @JvmStatic
    external fun isProcessAlive(pid: Int): Boolean

    fun write(fd: Int, data: ByteArray): Int = writeBytes(fd, data, 0, data.size)

    fun read(fd: Int, buffer: ByteArray): Int = readBytes(fd, buffer, 0, buffer.size)

    const val SIGTERM = 15
    const val SIGKILL = 9
}

data class ProcessHandle(
    val pid: Int,
    val stdinFd: Int,
    val stdoutFd: Int
) {
    fun isAlive(): Boolean = NativeProcess.isProcessAlive(pid)

    fun terminate() {
        NativeProcess.sendSignal(pid, NativeProcess.SIGTERM)
    }

    fun kill() {
        NativeProcess.sendSignal(pid, NativeProcess.SIGKILL)
    }

    fun close() {
        NativeProcess.closeFd(stdinFd)
        NativeProcess.closeFd(stdoutFd)
    }

    fun waitFor(): Int = NativeProcess.waitFor(pid)
}
