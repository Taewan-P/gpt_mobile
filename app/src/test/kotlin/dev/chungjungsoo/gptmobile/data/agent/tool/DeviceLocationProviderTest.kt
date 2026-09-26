package dev.chungjungsoo.gptmobile.data.agent.tool

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.Executor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class DeviceLocationProviderTest {
    private val manager = mockk<LocationManager>()
    private val context = mockk<Context>()
    private val listeners = mutableMapOf<String, LocationListener>()
    private val removed = mutableSetOf<LocationListener>()
    private var fine = true
    private var coarse = true
    private val provider = DeviceLocationProvider(context)

    @Before fun setup() {
        every { context.checkPermission(any(), any(), any()) } answers {
            val granted = if (firstArg<String>() == Manifest.permission.ACCESS_FINE_LOCATION) fine else coarse
            if (granted) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
        }
        every { context.getSystemService(Context.LOCATION_SERVICE) } returns manager
        every { context.mainExecutor } returns Executor { it.run() }
        every { manager.isLocationEnabled } returns true
        every { manager.isProviderEnabled(any()) } returns true
        every { manager.getLastKnownLocation(any()) } returns null
        every { manager.requestLocationUpdates(any<String>(), any<LocationRequest>(), any<Executor>(), any<LocationListener>()) } answers {
            assertTrue(secondArg<LocationRequest>().durationMillis <= 30_000)
            listeners[firstArg()] = arg(3)
        }
        every { manager.removeUpdates(any<LocationListener>()) } answers {
            removed.add(firstArg())
            Unit
        }
    }

    @Test fun `cold GPS can deliver after five seconds without another app and all subscriptions stop`() = runTest {
        val result = async { provider.getCurrentLocation() }
        runCurrent()
        assertEquals(setOf("gps", "network", "fused"), listeners.keys)
        advanceTimeBy(6000)
        assertFalse(result.isCompleted)
        listeners.getValue("gps").onLocationChanged(fix("gps"))
        runCurrent()
        assertEquals("gps", result.await()!!.provider)
        assertEquals(listeners.values.toSet(), removed)
    }

    @Test fun `network succeeds when GPS is enabled but cannot obtain a fix`() = runTest {
        val result = async { provider.getCurrentLocation() }
        runCurrent()
        listeners.getValue("network").onLocationChanged(fix("network"))
        runCurrent()
        assertEquals("network", result.await()!!.provider)
        assertEquals(listeners.values.toSet(), removed)
    }

    @Test fun `approximate permission avoids GPS and accepts a fresh fused fix`() = runTest {
        fine = false
        val result = async { provider.getCurrentLocation() }
        runCurrent()
        assertEquals(setOf("network", "fused"), listeners.keys)
        listeners.getValue("fused").onLocationChanged(fix("fused"))
        runCurrent()
        assertEquals("fused", result.await()!!.provider)
    }

    @Test fun `stale cached and callback positions are rejected until a new fix arrives`() = runTest {
        every { manager.getLastKnownLocation(any()) } returns fix("gps", 180_000)
        val result = async { provider.getCurrentLocation() }
        runCurrent()
        listeners.getValue("gps").onLocationChanged(fix("gps", 180_000))
        runCurrent()
        assertFalse(result.isCompleted)
        listeners.getValue("gps").onLocationChanged(fix("gps"))
        runCurrent()
        assertTrue(result.await() != null)
    }

    @Test fun `timeout and caller cancellation remove every active subscription`() = runTest {
        val timed = async { provider.getCurrentLocation(1000) }
        runCurrent()
        advanceTimeBy(1001)
        runCurrent()
        assertNull(timed.await())
        assertEquals(listeners.values.toSet(), removed)
        listeners.clear()
        removed.clear()
        val canceled = async { provider.getCurrentLocation() }
        runCurrent()
        canceled.cancelAndJoin()
        assertEquals(listeners.values.toSet(), removed)
    }

    @Test fun `permission denial and disabled location never register listeners`() = runTest {
        fine = false
        coarse = false
        assertNull(provider.getCurrentLocation())
        fine = true
        every { manager.isLocationEnabled } returns false
        assertNull(provider.getCurrentLocation())
        assertTrue(listeners.isEmpty())
    }

    private fun fix(source: String, age: Long = 0) = Location(source).apply {
        latitude = 43.0
        longitude = -79.0
        accuracy = 12f
        time = System.currentTimeMillis() - age
    }
}
