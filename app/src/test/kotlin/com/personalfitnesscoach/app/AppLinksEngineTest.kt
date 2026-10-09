package com.personalfitnesscoach.app

import com.personalfitnesscoach.engine.registry.Registry
import org.junit.Assert.assertEquals
import org.junit.Test

/** JVM unit test: the app module sees the approved registry through :engine. */
class AppLinksEngineTest {
    @Test fun appUsesApprovedRegistry() {
        assertEquals("1.1.1", Registry.VERSION)
        assertEquals(152, Registry.RULE_COUNT)
    }
}
