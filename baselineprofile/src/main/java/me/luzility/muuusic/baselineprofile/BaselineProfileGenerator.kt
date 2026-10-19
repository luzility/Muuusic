package me.luzility.muuusic.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Gradle uninstalls the app after connected tests; to keep it on a personal phone run:
//   ./gradlew :app:generateReleaseBaselineProfile -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = PACKAGE,
        includeInStartupProfile = true,
        // The default (up to 15 rounds until stable) took over 12 minutes; a few rounds are enough.
        maxIterations = 5,
        stableIterations = 2,
    ) {
        pressHome()
        startActivityAndWait()
        device.dismissPermissionDialog()

        device.waitFor("song_item")
        device.flingTag("home_list")

        device.clickTag("nav_search")
        device.waitFor("search_field").text = "tarkan"
        device.clickTag("search_suggestion")
        device.waitFor("song_item")

        device.clickTag("nav_library")

        device.clickTag("nav_home")
        device.clickTag("song_item")
        device.clickTag("player_sheet")
        device.clickTag("lyrics_button")
        Thread.sleep(LYRICS_SETTLE_MS)
        device.clickTag("lyrics_button")
        device.pressBack()
        device.waitForIdle()
    }

    // Lists and sheets animate in, so wait rather than assume it's on screen.
    private fun UiDevice.waitFor(tag: String): UiObject2 =
        checkNotNull(wait(Until.findObject(By.res(tag)), TIMEOUT_MS)) { "Timed out waiting for $tag" }

    private fun UiDevice.clickTag(tag: String) {
        waitFor(tag).click()
        waitForIdle()
    }

    private fun UiDevice.flingTag(tag: String) {
        val list = waitFor(tag)
        list.fling(Direction.DOWN)
        list.fling(Direction.UP)
    }

    // Notification permission prompt shows on first launch.
    private fun UiDevice.dismissPermissionDialog() {
        wait(Until.findObject(By.res("com.android.permissioncontroller:id/permission_allow_button")), 3_000)?.click()
    }

    private companion object {
        const val PACKAGE = "me.luzility.muuusic"
        const val TIMEOUT_MS = 20_000L
        const val LYRICS_SETTLE_MS = 4_000L
    }
}
