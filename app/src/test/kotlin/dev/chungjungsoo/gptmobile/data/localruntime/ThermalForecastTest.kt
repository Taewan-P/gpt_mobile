package dev.chungjungsoo.gptmobile.data.localruntime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermalForecastTest {
    @Test
    fun `forecast throttles before current thermal status becomes severe`() {
        val state = DeviceHardwareState(thermalHeadroom = 1.05f)
        assertEquals(DeviceThermalState.NORMAL, state.thermalState)
        assertTrue(state.isThrottlingRequired)
        assertEquals(1024, DeviceHardwareGovernor.computeThrottlingPolicy(state, true).maxTokensClamp)
    }

    @Test
    fun `moderate prediction requires a valid device threshold`() {
        assertFalse(DeviceHardwareState(thermalHeadroom = .9f).isModeratePressure)
        assertTrue(DeviceHardwareState(thermalHeadroom = .9f, moderateThermalThreshold = .8f).isModeratePressure)
        assertFalse(DeviceHardwareState(thermalHeadroom = Float.NaN, moderateThermalThreshold = .8f).isModeratePressure)
        assertFalse(DeviceHardwareState(thermalHeadroom = .9f, moderateThermalThreshold = -1f).isModeratePressure)
    }

    @Test
    fun `unsupported forecasts keep actual thermal and battery protections`() {
        assertFalse(DeviceHardwareState(thermalHeadroom = Float.NaN).isThrottlingRequired)
        assertTrue(DeviceHardwareState(thermalState = DeviceThermalState.SEVERE, thermalHeadroom = Float.NaN).isThrottlingRequired)
        assertTrue(DeviceHardwareState(isCharging = false, batteryPct = 10, thermalHeadroom = Float.NaN).isThrottlingRequired)
    }

    @Test
    fun `sampling is cached for ten seconds and refreshes after failure`() {
        val sampler = ThermalForecastSampler()
        var reads = 0
        val query = {
            reads++
            ThermalForecast(.5f)
        }
        assertEquals(.5f, sampler.read(0, query).headroom)
        sampler.read(9_999, query)
        assertEquals(1, reads)
        sampler.read(10_000, query)
        assertEquals(2, reads)
        assertNull(sampler.read(20_000) { error("Unavailable") }.headroom)
        assertNull(sampler.read(21_000, query).headroom)
        assertEquals(.5f, sampler.read(30_000, query).headroom)
        assertEquals(3, reads)
    }

    @Test
    fun `invalid sensor values are discarded`() {
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f)) {
            assertNull(ThermalForecast(value, value).sanitized().headroom)
            assertNull(ThermalForecast(value, value).sanitized().moderateThreshold)
        }
        assertEquals(1.2f, ThermalForecast(1.2f).sanitized().headroom)
    }

    private val cool = AdaptiveThrottlingPolicy(8L)
    private val warm = AdaptiveThrottlingPolicy(33L, .75f, 2048)
    private val hot = AdaptiveThrottlingPolicy(250L, .5f, 1024, true)

    @Test
    fun `pressure tightens immediately and requires sustained recovery`() {
        val policy = AdaptivePolicyStabilizer()
        assertEquals(cool, policy.update(cool, 0))
        assertEquals(hot, policy.update(hot, 1))
        assertEquals(hot, policy.update(cool, 2))
        assertEquals(hot, policy.update(cool, 30_001))
        assertEquals(cool, policy.update(cool, 30_002))
    }

    @Test
    fun `pressure spike resets recovery and does not reload on every fluctuation`() {
        val policy = AdaptivePolicyStabilizer()
        policy.update(hot, 0)
        policy.update(cool, 1)
        assertEquals(hot, policy.update(hot, 25_000))
        assertEquals(hot, policy.update(warm, 30_000))
        assertEquals(hot, policy.update(warm, 59_999))
        assertEquals(warm, policy.update(warm, 60_000))
    }

    @Test
    fun `low memory restriction survives a simultaneous thermal recovery`() {
        val policy = AdaptivePolicyStabilizer()
        policy.update(warm, 0)
        val memoryPressure = cool.copy(maxTokensClamp = 1024)
        assertEquals(warm.copy(maxTokensClamp = 1024), policy.update(memoryPressure, 1))
        policy.update(memoryPressure, 2)
        assertEquals(memoryPressure, policy.update(memoryPressure, 30_002))
    }
}
