package com.personalfitnesscoach.app.ui

import com.personalfitnesscoach.app.flow.AppController
import com.personalfitnesscoach.app.flow.Platform
import com.personalfitnesscoach.app.flow.StepReadingData
import com.personalfitnesscoach.data.core.PfcData
import com.personalfitnesscoach.data.core.store.InMemoryRowStore
import com.personalfitnesscoach.data.core.time.FixedClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Collections
import java.util.concurrent.Executors

/** Part 5 re-check N4: what the phone hands back (a file picker's result) is never dropped by the double-tap guard. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ActionsTest {
    private class FakePlatform : Platform {
        override val stepCounterAvailable = false
        override suspend fun readSteps(): StepReadingData? = null
        override fun cancelAlerts() = Unit
    }

    private val controller = AppController({ PfcData(InMemoryRowStore(), FixedClock(0), "test") }, FakePlatform(), "test")

    @Test fun `a file picker result arriving while another action runs is kept, a second tap is not`() = runBlocking {
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val a = Actions(CoroutineScope(SupervisorJob()), controller, dispatcher, doubleTapMs = Actions.DOUBLE_TAP_MS)
        val ran = Collections.synchronizedList(mutableListOf<String>())
        val gate = CompletableDeferred<Unit>()
        // The app comes back to the foreground (its refresh is slow), the picker's result arrives meanwhile, the user taps twice.
        a.fromSystem { gate.await(); ran += "foreground refresh" }
        a.fromSystem { ran += "file picker result" }
        a.run { ran += "tap" }
        a.run { ran += "double tap" }
        gate.complete(Unit)
        withTimeout(5_000) { while (ran.size < 3) delay(10) }
        delay(200)
        // Nothing from the phone is dropped; the second tap is. (The order of real actions is kept by the controller's own lock.)
        assertEquals(setOf("foreground refresh", "file picker result", "tap"), ran.toSet())
        assertEquals(3, ran.size)
        // Right after an action finished, a system result still runs (the double-tap window is for taps only).
        a.fromSystem { ran += "permission result" }
        withTimeout(5_000) { while (ran.size < 4) delay(10) }
        assertEquals("permission result", ran.last())
        dispatcher.close()
    }
}
