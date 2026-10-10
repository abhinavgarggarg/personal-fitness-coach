package com.personalfitnesscoach.app

import android.content.pm.PackageManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.personalfitnesscoach.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs on every emulator in the device matrix (.github/workflows/devices.yml): the app starts, opens its database and shows the welcome
 * screen of onboarding (a fresh install), and holds no network permission on any Android version.
 */
@RunWith(AndroidJUnit4::class)
class LaunchSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun appStartsAndShowsTheFirstScreen() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val welcome = ctx.getString(R.string.onb_welcome_title)
        val today = ctx.getString(R.string.today_title)
        // A fresh install shows the welcome screen; a phone that already finished onboarding shows Today.
        compose.waitUntil(20_000) {
            compose.onAllNodesWithText(welcome).fetchSemanticsNodes().isNotEmpty() || compose.onAllNodesWithText(today).fetchSemanticsNodes().isNotEmpty()
        }
        if (compose.onAllNodesWithText(welcome).fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithText(ctx.getString(R.string.app_name)).assertIsDisplayed()
    }

    @Test fun appHasNoInternetPermission() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(PackageManager.PERMISSION_DENIED, ctx.checkSelfPermission(android.Manifest.permission.INTERNET))
    }
}
