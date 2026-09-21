package dev.chungjungsoo.gptmobile.data.localruntime

import dev.chungjungsoo.gptmobile.data.catalog.ModelCatalogParser
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAcceleratorsTest {

    @Test
    fun `NPU is eligible when the model lists it and the device has a SOC variant`() {
        assertTrue(
            LocalAccelerators.isNpuEligible(
                supported = listOf("cpu", "gpu", "npu"),
                socToModelFiles = mapOf("SM8650" to VARIANT),
                deviceSocModel = "SM8650"
            )
        )
    }

    @Test
    fun `NPU eligibility matches SOC keys case insensitively`() {
        assertTrue(
            LocalAccelerators.isNpuEligible(
                supported = listOf("npu"),
                socToModelFiles = mapOf("SM8650" to VARIANT),
                deviceSocModel = "sm8650"
            )
        )
    }

    @Test
    fun `NPU is not eligible when the catalog does not list it`() {
        assertFalse(
            LocalAccelerators.isNpuEligible(
                supported = listOf("cpu", "gpu"),
                socToModelFiles = mapOf("SM8650" to VARIANT),
                deviceSocModel = "SM8650"
            )
        )
    }

    @Test
    fun `NPU is not eligible when the device has no matching SOC variant`() {
        assertFalse(
            LocalAccelerators.isNpuEligible(
                supported = listOf("cpu", "npu"),
                socToModelFiles = mapOf("SM8650" to VARIANT),
                deviceSocModel = "SM8750"
            )
        )
    }

    @Test
    fun `NPU is not eligible when the entry has no SOC variants`() {
        assertFalse(
            LocalAccelerators.isNpuEligible(
                supported = listOf("npu", "cpu"),
                socToModelFiles = emptyMap<String, Any>(),
                deviceSocModel = "SM8650"
            )
        )
    }

    @Test
    fun `selectable includes NPU only when the device qualifies`() {
        assertEquals(
            listOf(LocalAccelerators.CPU, LocalAccelerators.GPU, LocalAccelerators.NPU),
            LocalAccelerators.selectable(
                supported = listOf("npu", "cpu", "gpu"),
                socToModelFiles = mapOf("SM8650" to VARIANT),
                deviceSocModel = "SM8650"
            )
        )
        assertEquals(
            listOf(LocalAccelerators.CPU, LocalAccelerators.GPU),
            LocalAccelerators.selectable(
                supported = listOf("npu", "cpu", "gpu"),
                socToModelFiles = mapOf("SM8650" to VARIANT),
                deviceSocModel = "tensor"
            )
        )
    }

    @Test
    fun `selectable keeps CPU and GPU when NPU is listed but the device does not qualify`() {
        assertEquals(
            listOf(LocalAccelerators.GPU),
            LocalAccelerators.selectable(
                supported = listOf("gpu", "npu"),
                socToModelFiles = emptyMap<String, Any>(),
                deviceSocModel = "SM8650"
            )
        )
    }

    @Test
    fun `default accelerator prefers GPU and never picks ineligible NPU`() {
        assertEquals(
            LocalAccelerators.GPU,
            LocalAccelerators.defaultFrom(
                supported = listOf("cpu", "gpu", "npu"),
                socToModelFiles = mapOf("SM8650" to VARIANT),
                deviceSocModel = "SM8650"
            )
        )
        assertEquals(
            LocalAccelerators.NPU,
            LocalAccelerators.defaultFrom(
                supported = listOf("npu", "cpu"),
                socToModelFiles = mapOf("SM8650" to VARIANT),
                deviceSocModel = "SM8650"
            )
        )
        assertEquals(
            LocalAccelerators.CPU,
            LocalAccelerators.defaultFrom(
                supported = listOf("npu", "cpu"),
                socToModelFiles = emptyMap<String, Any>(),
                deviceSocModel = "SM8650"
            )
        )
    }

    @Test
    fun `choices always lists CPU GPU and NPU`() {
        val choices = LocalAccelerators.choices(
            supported = listOf("gpu"),
            socToModelFiles = emptyMap<String, Any>(),
            deviceSocModel = "Tensor G4"
        )

        assertEquals(
            listOf(LocalAccelerators.CPU, LocalAccelerators.GPU, LocalAccelerators.NPU),
            choices.map { it.accelerator }
        )
        assertFalse(choices.single { it.accelerator == LocalAccelerators.CPU }.enabled)
        assertTrue(choices.single { it.accelerator == LocalAccelerators.GPU }.enabled)
        assertEquals(
            AcceleratorUnavailableReason.MODEL_HAS_NO_BUILD,
            choices.single { it.accelerator == LocalAccelerators.CPU }.unavailableReason
        )
        assertEquals(
            AcceleratorUnavailableReason.MODEL_HAS_NO_BUILD,
            choices.single { it.accelerator == LocalAccelerators.NPU }.unavailableReason
        )
    }

    @Test
    fun `NPU is disabled for device reason when variants omit this SOC`() {
        val choices = LocalAccelerators.choices(
            supported = listOf("cpu", "gpu", "npu"),
            socToModelFiles = mapOf("SM8650" to VARIANT),
            deviceSocModel = "Tensor G4"
        )

        val npu = choices.single { it.accelerator == LocalAccelerators.NPU }
        assertFalse(npu.enabled)
        assertEquals(AcceleratorUnavailableReason.DEVICE_NOT_SUPPORTED, npu.unavailableReason)
    }

    @Test
    fun `NPU is enabled when the model and device SOC qualify`() {
        val choices = LocalAccelerators.choices(
            supported = listOf("cpu", "gpu", "npu"),
            socToModelFiles = mapOf("Tensor G4" to VARIANT),
            deviceSocModel = "Tensor G4"
        )

        val npu = choices.single { it.accelerator == LocalAccelerators.NPU }
        assertTrue(npu.enabled)
        assertEquals(null, npu.unavailableReason)
    }

    @Test
    fun `hosted Gemma3-1B reports device unsupported on Tensor G4 and enabled on SM8750`() {
        val gemma3 = hostedEntry("gemma3-1b-it")

        val tensorG4 = LocalAccelerators.choices(
            supported = gemma3.supportedAccelerators,
            socToModelFiles = gemma3.socToModelFiles,
            deviceSocModel = "Tensor G4"
        ).single { it.accelerator == LocalAccelerators.NPU }
        assertFalse(tensorG4.enabled)
        assertEquals(AcceleratorUnavailableReason.DEVICE_NOT_SUPPORTED, tensorG4.unavailableReason)

        val sm8750 = LocalAccelerators.choices(
            supported = gemma3.supportedAccelerators,
            socToModelFiles = gemma3.socToModelFiles,
            deviceSocModel = "SM8750"
        ).single { it.accelerator == LocalAccelerators.NPU }
        assertTrue(sm8750.enabled)
        assertEquals(null, sm8750.unavailableReason)
    }

    @Test
    fun `hosted Gemma-3n still reports model has no NPU build`() {
        val gemma3n = hostedEntry("gemma-3n-e2b-it")

        val npu = LocalAccelerators.choices(
            supported = gemma3n.supportedAccelerators,
            socToModelFiles = gemma3n.socToModelFiles,
            deviceSocModel = "SM8750"
        ).single { it.accelerator == LocalAccelerators.NPU }

        assertFalse(npu.enabled)
        assertEquals(AcceleratorUnavailableReason.MODEL_HAS_NO_BUILD, npu.unavailableReason)
    }

    @Test
    fun `sampler config is skipped on NPU`() {
        assertFalse(LocalAccelerators.shouldApplySampler(LocalAccelerators.NPU))
        assertTrue(LocalAccelerators.shouldApplySampler(LocalAccelerators.CPU))
        assertTrue(LocalAccelerators.shouldApplySampler(LocalAccelerators.GPU))
    }

    private fun hostedEntry(id: String) = ModelCatalogParser.parse(readCatalogFile()).models.single { it.id == id }

    private fun readCatalogFile(): String {
        val file = listOf(
            File("../model_catalog.json"),
            File("model_catalog.json")
        ).firstOrNull { it.exists() }
        checkNotNull(file) { "Missing hosted catalog file" }
        return file.readText()
    }

    private companion object {
        val VARIANT = Any()
    }
}
