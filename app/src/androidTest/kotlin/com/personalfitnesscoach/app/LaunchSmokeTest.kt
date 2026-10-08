package com.personalfitnesscoach.app

import android.content.pm.PackageManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.personalfitnesscoach.engine.registry.Registry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs on every emulator in the device matrix (.github/workflows/devices.yml): the app starts,
 * shows its first screen, reaches the engine, and holds no network permission on any Android version.
 */
@RunWith(AndroidJUnit4::class)
class LaunchSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun appStartsAndShowsTheFirstScreen() {
        compose.onNodeWithText("Personal Fitness Coach").assertIsDisplayed()
        compose.onNodeWithText("rules ${Registry.VERSION}", substring = true).assertIsDisplayed()
    }

    @Test fun appHasNoInternetPermission() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(PackageManager.PERMISSION_DENIED, ctx.checkSelfPermission(android.Manifest.permission.INTERNET))
    }
}
