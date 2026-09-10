package dev.chungjungsoo.gptmobile.data.localruntime

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NpuRuntimeLibrariesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `isAvailable is false for unknown SoC`() {
        val helper = helper()
        assertFalse(helper.isAvailable(""))
        assertFalse(helper.isAvailable("SM9999"))
    }

    @Test
    fun `isAvailable is false until matching ELF files exist`() {
        val native = tmp.newFolder("lib")
        val helper = NpuRuntimeLibraries(native, tmp.newFolder("dispatch"))
        assertFalse(helper.isAvailable("SM8650"))
        writeElf(File(native, NpuRuntimeLibraries.PACKAGED_QUALCOMM))
        writeElf(File(native, "libQnnHtp.so"))
        writeElf(File(native, "libQnnSystem.so"))
        writeElf(File(native, "libQnnHtpPrepare.so"))
        writeElf(File(native, "libQnnHtpV75Skel.so"))
        assertFalse(helper.isAvailable("SM8650"))
        writeElf(File(native, "libQnnHtpV75Stub.so"))
        assertTrue(helper.isAvailable("sm8650"))
    }

    @Test
    fun `prepare exposes only Qualcomm dispatch for SM8650`() {
        val native = tmp.newFolder("lib")
        val dispatch = tmp.newFolder("unused")
        writeElf(File(native, NpuRuntimeLibraries.PACKAGED_QUALCOMM))
        writeElf(File(native, NpuRuntimeLibraries.PACKAGED_GOOGLE_TENSOR))
        writeElf(File(native, NpuRuntimeLibraries.PACKAGED_MEDIATEK))
        writeElf(File(native, "libQnnHtp.so"))
        writeElf(File(native, "libQnnSystem.so"))
        writeElf(File(native, "libQnnHtpPrepare.so"))
        writeElf(File(native, "libQnnHtpV73Skel.so"))
        writeElf(File(native, "libQnnHtpV73Stub.so"))
        writeElf(File(native, "libQnnHtpV75Skel.so"))
        writeElf(File(native, "libQnnHtpV75Stub.so"))
        writeElf(File(native, "libQnnHtpV79Skel.so"))
        writeElf(File(native, "libQnnHtpV79Stub.so"))
        val helper = NpuRuntimeLibraries(native, dispatch)
        val dir = File(helper.prepare("SM8650"))
        assertEquals(
            setOf(
                NpuRuntimeLibraries.LOADER_QUALCOMM,
                "libQnnHtp.so",
                "libQnnSystem.so",
                "libQnnHtpPrepare.so",
                "libQnnHtpV75Skel.so",
                "libQnnHtpV75Stub.so"
            ),
            dir.list()?.toSet()
        )
        assertEquals(
            listOf(NpuRuntimeLibraries.LOADER_QUALCOMM),
            dir.list()?.filter { it.startsWith("libLiteRtDispatch") }
        )
        val link = File(dir, NpuRuntimeLibraries.LOADER_QUALCOMM)
        assertTrue(Files.isSymbolicLink(link.toPath()))
        assertEquals(
            File(native, NpuRuntimeLibraries.PACKAGED_QUALCOMM).toPath(),
            Files.readSymbolicLink(link.toPath())
        )
        assertFalse(File(dir, "libQnnHtpV73Skel.so").exists())
        assertFalse(File(dir, "libQnnHtpV79Skel.so").exists())
        assertFalse(File(dir, NpuRuntimeLibraries.LOADER_GOOGLE_TENSOR).exists())
    }

    @Test
    fun `prepare maps catalog SoCs to exact HTP versions`() {
        val native = tmp.newFolder("lib")
        writeElf(File(native, NpuRuntimeLibraries.PACKAGED_QUALCOMM))
        writeElf(File(native, "libQnnHtp.so"))
        writeElf(File(native, "libQnnSystem.so"))
        writeElf(File(native, "libQnnHtpPrepare.so"))
        listOf("73", "75", "79", "81").forEach { version ->
            writeElf(File(native, "libQnnHtpV" + version + "Skel.so"))
            writeElf(File(native, "libQnnHtpV" + version + "Stub.so"))
        }
        val helper = NpuRuntimeLibraries(native, tmp.newFolder("dispatch"))
        assertTrue(File(helper.prepare("SM8550"), "libQnnHtpV73Skel.so").exists())
        assertTrue(File(helper.prepare("SM8750"), "libQnnHtpV79Skel.so").exists())
        assertTrue(File(helper.prepare("SM8850"), "libQnnHtpV81Skel.so").exists())
        assertFalse(File(helper.prepare("SM8850"), "libQnnHtpV79Skel.so").exists())
    }

    @Test
    fun `prepare exposes only Google Tensor dispatch`() {
        val native = tmp.newFolder("lib")
        writeElf(File(native, NpuRuntimeLibraries.PACKAGED_GOOGLE_TENSOR))
        writeElf(File(native, NpuRuntimeLibraries.PACKAGED_QUALCOMM))
        val helper = NpuRuntimeLibraries(native, tmp.newFolder("dispatch"))
        val dir = File(helper.prepare("Tensor G5"))
        assertEquals(setOf(NpuRuntimeLibraries.LOADER_GOOGLE_TENSOR), dir.list()?.toSet())
        assertTrue(helper.isAvailable("tensor_g5"))
    }

    @Test
    fun `prepare exposes MediaTek dispatch when parent library is present`() {
        val native = tmp.newFolder("lib")
        writeElf(File(native, NpuRuntimeLibraries.PACKAGED_MEDIATEK))
        val helper = NpuRuntimeLibraries(native, tmp.newFolder("dispatch"))
        assertTrue(helper.isAvailable("MT6991"))
        val dir = File(helper.prepare("MT6989"))
        assertEquals(setOf(NpuRuntimeLibraries.LOADER_MEDIATEK), dir.list()?.toSet())
    }

    @Test(expected = IllegalStateException::class)
    fun `prepare fails when MediaTek library is missing`() {
        helper().prepare("MT6993")
    }

    private fun helper(): NpuRuntimeLibraries = NpuRuntimeLibraries(tmp.newFolder("lib"), tmp.newFolder("dispatch"))

    private fun writeElf(file: File) {
        val bytes = ByteArray(64)
        byteArrayOf(0x7F, 69, 76, 70, 2, 1).copyInto(bytes)
        bytes[18] = 0xb7.toByte()
        file.writeBytes(bytes)
    }
}
