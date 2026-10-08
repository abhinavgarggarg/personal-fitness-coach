package com.personalfitnesscoach.app

import com.personalfitnesscoach.engine.registry.Registry
import org.junit.Assert.assertEquals
import org.junit.Test

/** JVM unit test: the app module sees the approved registry through :engine. */
class AppLinksEngineTest {
    @Test fun appUsesApprovedRegistry() {
        assertEquals("1.0.1", Registry.VERSION)
        assertEquals(140, Registry.RULE_COUNT)
    }
}
