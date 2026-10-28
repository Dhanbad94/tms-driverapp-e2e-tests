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
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.slf4j.LoggerFactory
import tms.driver.base.DriverExtension
import tms.driver.core.config.Credentials
import tms.driver.core.driver.DriverSession
import tms.driver.fixtures.RideFixture
import tms.driver.fixtures.RideSpec
import tms.driver.fixtures.TripFixtureExtension
import tms.driver.flows.LoginFlow
import tms.driver.screens.home.OdHomeScreen
import tms.driver.screens.shell.CommonDialog
import tms.driver.screens.shell.HomeShell
import java.time.Duration

/**
 * Phase T1: the driver's Home screen with and without an assigned ride (scenarios HOME-001 to HOME-012 of
 * docs/trip-lifecycle-test-plan.md). Rides are created through the Rider API and assigned through the Driver API
 * BEFORE the app login, because an API login signs the app out; cleanup is guaranteed by [TripFixtureExtension].
 * Every ride sends an SMS to the rider phone in the template.
 *
 * Texts asserted here were all seen on the staging build: "You have 1 dispatch", "1 Guest" / "5 Guests",
 * "2 Stops", START TRIP, "Action Required" (see docs/trip-lifecycle-test-plan.md).
 */
@Tag("trip")
@Tag("device")
@EnabledIfEnvironmentVariable(named = "TMS_TRIP_API_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TMS_RIDER_API_URL", matches = ".+")
@ExtendWith(DriverExtension::class, TripFixtureExtension::class)
@DisplayName("Driver Home screen with an assigned ride")
class HomeWithDispatchTest {
    private lateinit var creds: Credentials

    @BeforeEach
    fun requireCredentials() {
        val c = Credentials.fromEnvironment()
        assumeTrue(c != null, "TMS_DRIVER_PHONE / TMS_DRIVER_PASSCODE are not set, so this test is skipped")
        creds = c!!
    }

    /** What Home should show for one ride. */
    data class Variant(val label: String, val spec: RideSpec, val guestsText: String, val adaShown: Boolean) {
        override fun toString() = label
    }

    companion object {
        @JvmStatic
        fun variants() = listOf(
            Variant("a single rider who does not need ADA", RideSpec(partySize = 1, ada = false, note = null, seed = 11), "1 Guest", false),
            Variant("a party of 10 that needs ADA", RideSpec(partySize = 10, ada = true, note = "Hello", seed = 12), "10 Guests", true),
        )
    }

    @Test
    @DisplayName("[HOME-005] With no ride assigned the Home screen says there are no trips")
    fun emptyStateWithNoRide(rides: RideFixture) {
        step("Given the QA driver has no rides assigned")
        // The fixture has already cleared any leftover ride before this test started.

        step("When the driver logs in to the app")
        LoginFlow(creds).loginToHome()
        val home = OdHomeScreen()

        step("Then Home shows the 'You have No Trips Assigned' message with its picture and no ride card")
        assertTrue(home.waitForEmptyState(Duration.ofSeconds(40)), "The empty state should be shown")
        assertEquals("You have No Trips Assigned", home.emptyTitleText())
        assertTrue(home.isEmptyImageShown(), "The empty-state picture should be shown")
        assertFalse(home.hasDispatchSummary(), "There should be no ride summary")
        assertFalse(home.isStartButtonShown(), "There should be no START TRIP button without a ride")
    }

    @ParameterizedTest(name = "[HOME-002 / HOME-003] Home summarises {0}")
    @MethodSource("variants")
    @DisplayName("Home summarises the ride's guest count and ADA flag")
    fun summaryFollowsTheRide(variant: Variant, rides: RideFixture) {
        step("Given a ride for ${variant.label} is assigned to the QA driver")
        val ride = rides.assignedRide(variant.spec)

        step("When the driver logs in to the app")
        LoginFlow(creds).loginToHome()
        val home = OdHomeScreen()
        assertTrue(home.waitForTrip(Duration.ofSeconds(60)), "A trip should be shown after logging in")

        step("Then Home shows one dispatch with '${variant.guestsText}', two stops and START TRIP")
        assertEquals("You have 1 dispatch", home.summaryText())
        assertEquals(variant.guestsText, home.guestsText())
        assertEquals("2 Stops", home.stopsText())
        assertTrue(home.startButtonText().equals("start trip", ignoreCase = true), "Button text was '${home.startButtonText()}'")

        step("And the ADA row is ${if (variant.adaShown) "shown" else "not shown"}")
        assertEquals(variant.adaShown, home.isAdaShown(), "ADA row for ride '${ride.riderName}'")
    }

    @Test
    @DisplayName("[HOME-001 / HOME-006 / HOME-007 / HOME-008 / HOME-010 / HOME-012] A driver with one ride sees it, can refresh and restart, cannot go off duty, and sees it vanish when cancelled")
    fun oneRideJourney(rides: RideFixture) {
        step("Given a ride for 5 guests with ADA is assigned to the QA driver")
        val ride = rides.assignedRide(RideSpec(partySize = 5, ada = true, note = "Hello", seed = 13))

        step("When the driver logs in to the app")
        LoginFlow(creds).loginToHome()
        val home = OdHomeScreen()
        val shell = HomeShell()
        assertTrue(home.waitForTrip(Duration.ofSeconds(60)), "A trip should be shown after logging in")

        step("Then Home shows the summary: 1 dispatch, 5 Guests, ADA, 2 Stops, START TRIP, organisation and shuttle")
        assertEquals("You have 1 dispatch", home.summaryText())
        assertEquals("5 Guests", home.guestsText())
        assertTrue(home.isAdaShown(), "ADA row for an ADA ride")
        assertEquals("2 Stops", home.stopsText())
        assertTrue(home.startButtonText().equals("start trip", ignoreCase = true))
        assertTrue(home.organisationText().isNotBlank() && home.shuttleText().isNotBlank(), "organisation and shuttle chips")

        step("And the Requests button shows how many rides are waiting, with the 'new ride requests' banner when there are some")
        val waiting = home.requestsBadgeCount()
        Allure.addAttachment("requests-badge", "text/plain", waiting?.toString() ?: "(not shown)")
        if (waiting != null && waiting > 0) assertEquals("You have new ride requests.", home.newRequestsBannerText())

        step("When the driver pulls down to refresh, then the same single ride is still shown")
        home.pullToRefresh()
        assertTrue(home.waitForTrip(Duration.ofSeconds(30)))
        assertEquals("You have 1 dispatch", home.summaryText())

        step("When the driver tries to go off duty with a ride assigned, then 'Action Required' blocks it")
        val dutyBefore = shell.dutyState()
        assumeTrue(dutyBefore == "ON", "The QA driver must be on duty for this check")
        shell.toggleDuty()
        val dialog = CommonDialog().waitUntilShown()
        assertEquals("Action Required", dialog.titleText())
        assertEquals(
            "An active dispatch is in progress. Please complete your current trip before going off duty",
            dialog.messageText().trimEnd('.'),
        )
        dialog.cancel() // the OKAY button
        assertEquals("ON", shell.dutyState(), "Duty must stay ON")

        step("When the app is closed and opened again, then the ride is still there")
        val appPackage = DriverSession.config.appPackage
        DriverSession.driver.terminateApp(appPackage)
        DriverSession.driver.activateApp(appPackage)
        shell.waitUntilShown()
        assertTrue(home.waitForTrip(Duration.ofSeconds(60)))
        assertEquals("You have 1 dispatch", home.summaryText())

        step("When the ride is cancelled through the API (which signs the app out) and the driver logs in again")
        rides.cancel(ride, "E2E: ride cancelled while the driver is signed out")
        LoginFlow(creds).loginToHome()

        step("Then Home shows no trips again")
        assertTrue(home.waitForEmptyState(Duration.ofSeconds(40)), "The empty state should return")
        assertFalse(home.isStartButtonShown())
    }

    private fun step(description: String) {
        Allure.step(description)
        LoggerFactory.getLogger("steps").info("STEP: {}", description)
    }
}
