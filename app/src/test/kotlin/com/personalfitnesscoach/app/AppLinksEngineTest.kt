package com.personalfitnesscoach.app

import com.personalfitnesscoach.engine.registry.Registry
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** JVM unit test: the app module sees the approved registry through :engine, the version in rules/ (research updates move it, D-106). */
class AppLinksEngineTest {
    @Test fun appUsesApprovedRegistry() {
        val json = listOf(File("../rules/rule_registry_v1.0.json"), File("rules/rule_registry_v1.0.json")).first { it.exists() }.readText()
        val version = Regex("\"registry_version\"\\s*:\\s*\"([^\"]+)\"").find(json)!!.groupValues[1]
        assertEquals(version, Registry.VERSION)
        assertEquals(152, Registry.RULE_COUNT)
    }
}
