package tms.driver.smoke

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import tms.driver.base.DriverExtension
import tms.driver.core.driver.DriverSession
import tms.driver.flows.LaunchFlow

/**
 * Phase A smoke test (scenario AUTH-001): a cold start with no session shows the Login screen.
 * Needs: Appium server, a device/emulator, and the app installed (or TMS_APK_PATH set).
 */
@Tag("smoke")
@Tag("device")
@ExtendWith(DriverExtension::class)
@DisplayName("App launch")
class LaunchSmokeTest {

    @Test
    @DisplayName("[AUTH-001] A first launch with no saved session opens the Login screen")
    fun `AUTH-001 cold start with no session shows Login`() {
        io.qameta.allure.Allure.step("Given the app is installed with no saved data")
        io.qameta.allure.Allure.step("When the app is opened")
        val login = LaunchFlow().launchToLogin()

        io.qameta.allure.Allure.step("Then the Driver app is in the foreground and the Login screen is showing")

        assertEquals(
            DriverSession.config.appPackage,
            DriverSession.driver.currentPackage,
            "app under test should be in the foreground",
        )
        assertTrue(login.isShown(), "Login screen markers should be visible")
    }
}
