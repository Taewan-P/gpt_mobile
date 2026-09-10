package dev.chungjungsoo.gptmobile.data.localruntime

import android.content.Context
import java.io.File
import java.nio.file.Files

/** Exposes one vendor dispatch library; LiteRT chooses the first dispatch library it finds. */
class NpuRuntimeLibraries(
    private val nativeLibraryDir: File,
    private val dispatchRoot: File
) {
    constructor(context: Context) : this(File(context.applicationInfo.nativeLibraryDir), File(context.noBackupFilesDir, "npu-dispatch"))

    fun isAvailable(deviceSocModel: String): Boolean = runCatching {
        val pack = resolve(deviceSocModel) ?: return false
        pack.second.all { (name, _) -> isElf(File(nativeLibraryDir, name), !name.contains("Skel")) }
    }.getOrDefault(false)

    @Synchronized
    fun prepare(deviceSocModel: String): String {
        val (vendor, libraries) = resolve(deviceSocModel) ?: error("Unsupported NPU device")
        check(isAvailable(deviceSocModel)) { "NPU runtime libraries are missing" }
        val directory = File(dispatchRoot, vendor)
        check(directory.isDirectory || directory.mkdirs()) { "Unable to prepare NPU runtime" }
        val expected = libraries.map { it.second }.toSet()
        directory.listFiles()?.filter { it.name !in expected }?.forEach { check(it.delete()) }
        libraries.forEach { (source, destination) ->
            val target = File(nativeLibraryDir, source).toPath()
            val link = File(directory, destination).toPath()
            if (!Files.isSymbolicLink(link) || Files.readSymbolicLink(link) != target) {
                Files.deleteIfExists(link)
                Files.createSymbolicLink(link, target)
            }
        }
        check(directory.listFiles().orEmpty().count { it.name.startsWith("libLiteRtDispatch") } == 1)
        return directory.absolutePath
    }

    private fun resolve(soc: String): Pair<String, List<Pair<String, String>>>? {
        val key = normalize(soc)
        val vendor = when (key) {
            "tensor g5", "tensor g6" -> "google_tensor"
            "mt6989", "mt6991", "mt6993" -> "mediatek"
            in QUALCOMM_HTP -> "qualcomm"
            else -> return null
        }
        val dispatch = when (vendor) {
            "google_tensor" -> PACKAGED_GOOGLE_TENSOR to LOADER_GOOGLE_TENSOR
            "mediatek" -> PACKAGED_MEDIATEK to LOADER_MEDIATEK
            else -> PACKAGED_QUALCOMM to LOADER_QUALCOMM
        }
        val dependencies = if (vendor == "qualcomm") {
            val version = QUALCOMM_HTP.getValue(key)
            listOf("libQnnHtp.so", "libQnnSystem.so", "libQnnHtpPrepare.so", "libQnnHtpV${version}Stub.so", "libQnnHtpV${version}Skel.so")
        } else {
            emptyList()
        }
        return key.replace(' ', '_') to (listOf(dispatch) + dependencies.map { it to it })
    }

    companion object {
        const val PACKAGED_GOOGLE_TENSOR = "libnpu_dispatch_google_tensor.so"
        const val PACKAGED_QUALCOMM = "libnpu_dispatch_qualcomm.so"
        const val PACKAGED_MEDIATEK = "libnpu_dispatch_mediatek.so"
        const val LOADER_GOOGLE_TENSOR = "libLiteRtDispatch_GoogleTensor.so"
        const val LOADER_QUALCOMM = "libLiteRtDispatch_Qualcomm.so"
        const val LOADER_MEDIATEK = "libLiteRtDispatch_MediaTek.so"
        private val QUALCOMM_HTP = mapOf("sm8550" to 73, "sm8650" to 75, "sm8750" to 79, "sm8850" to 81)

        internal fun normalize(value: String): String = value.trim().lowercase().replace('_', ' ').replace(Regex("\\s+"), " ")

        private fun isElf(file: File, arm64: Boolean): Boolean {
            if (!file.isFile || file.length() < 64L) return false
            val header = ByteArray(20)
            file.inputStream().use { if (it.read(header) != header.size) return false }
            if (!header.copyOfRange(0, 4).contentEquals(byteArrayOf(0x7f, 69, 76, 70))) return false
            return !arm64 || (header[4] == 2.toByte() && header[5] == 1.toByte() && header[18] == 0xb7.toByte() && header[19] == 0.toByte())
        }
    }
}
