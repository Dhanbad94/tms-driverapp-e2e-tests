package tms.driver.trip

import io.qameta.allure.Allure
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import tms.driver.base.DriverExtension
import tms.driver.core.config.Credentials
import tms.driver.core.driver.DriverSession
import tms.driver.flows.LoginFlow
import tms.driver.screens.Ids
import tms.driver.screens.home.OdHomeScreen
import tms.driver.screens.requests.RideRequestsScreen
import tms.driver.screens.shell.HomeShell
import tms.driver.screens.shell.HomeShell.Tab
import java.time.Duration

/**
 * Phase T11: places that exist in the app's code but that a driver cannot reach (NOTIF-001, NOTIF-002 and LOG-001 of
 * docs/additional-coverage-plan.md). The Notifications tab is hidden and the Dispatch Log has no control that opens it. If the
 * product owner decides they should be reachable, these tests are the ones that will fail and be turned into real scenarios.
 */
@Tag("navigation")
@Tag("trip")
@Tag("device")
@ExtendWith(DriverExtension::class)
@DisplayName("Driver navigation: what is and is not reachable")
class NavigationTest {
    private lateinit var creds: Credentials

    @BeforeEach
    fun requireCredentials() {
        val c = Credentials.fromEnvironment()
        assumeTrue(c != null, "TMS_DRIVER_PHONE / TMS_DRIVER_PASSCODE are not set, so this test is skipped")
        creds = c!!
    }

    private fun visibleTexts(): List<String> =
        Regex("""<[^>]*\btext="([^"]+)"[^>]*>""").findAll(DriverSession.driver.pageSource).map { it.groupValues[1] }.toList()

    private fun assertNoTrace(screen: String) {
        val texts = visibleTexts()
        assertFalse(texts.any { it.contains("notification", ignoreCase = true) }, "$screen shows a Notifications text: $texts")
        assertFalse(texts.any { it.contains("dispatch", ignoreCase = true) && it.contains("log", ignoreCase = true) }, "$screen shows a Dispatch Log text: $texts")
    }

    @Test
    @DisplayName("[NOTIF-001] The bottom bar offers exactly Home, Profile and Help; there is no Notifications tab anywhere")
    fun bottomBar() {
        step("Given the driver is on Home")
        LoginFlow(creds).loginToHome()
        val home = OdHomeScreen()
        assertTrue(home.waitForEmptyState(Duration.ofSeconds(40)))
        val shell = HomeShell()

        step("Then the bottom bar has exactly the Home, Profile and Help tabs")
        assertEquals(listOf(Tab.HOME, Tab.PROFILE, Tab.HELP), shell.presentTabs())
        assertTrue(DriverSession.driver.findElements(Ids.by("cl_nav_notifi")).none { runCatching { it.isDisplayed }.getOrDefault(false) }, "A Notifications tab must not be visible")

        step("And the same three tabs are there on Profile and on Help")
        shell.open(Tab.PROFILE); assertEquals(listOf(Tab.HOME, Tab.PROFILE, Tab.HELP), shell.presentTabs())
        shell.open(Tab.HELP); assertEquals(listOf(Tab.HOME, Tab.PROFILE, Tab.HELP), shell.presentTabs())
    }

    @Test
    @DisplayName("[NOTIF-002 / LOG-001] Nothing on Home, Ride Requests, Profile or Help leads to Notifications or a Dispatch Log")
    fun noWayToNotificationsOrDispatchLog() {
        step("Given the driver is on Home")
        LoginFlow(creds).loginToHome()
        assertTrue(OdHomeScreen().waitForEmptyState(Duration.ofSeconds(40)))
        val shell = HomeShell()

        step("Then Home mentions neither Notifications nor a Dispatch Log")
        assertNoTrace("Home")

        step("And the Requests button opens Ride Requests (not Notifications), which mentions neither, and its toolbar icon goes back to Home")
        val requests = RideRequestsScreen().also { it.openFromHome() }
        assertEquals("Ride Requests", requests.titleText())
        assertNoTrace("Ride Requests")
        DriverSession.driver.findElement(Ids.by("iv_menu")).click()
        assertTrue(OdHomeScreen().waitForEmptyState(Duration.ofSeconds(20)), "The toolbar icon should lead back to Home, not to a menu")

        step("And Profile and Help mention neither")
        shell.open(Tab.PROFILE); assertNoTrace("Profile")
        shell.open(Tab.HELP); assertNoTrace("Help")
    }

    private fun step(description: String) {
        Allure.step(description)
        LoggerFactory.getLogger("steps").info("STEP: {}", description)
    }
}
