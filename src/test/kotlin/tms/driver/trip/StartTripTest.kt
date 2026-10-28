package tms.driver.trip

import io.qameta.allure.Allure
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import tms.driver.base.DriverExtension
import tms.driver.core.config.Credentials
import tms.driver.core.device.Adb
import tms.driver.core.driver.DriverSession
import tms.driver.core.report.Artifacts
import tms.driver.core.wait.Toasts
import tms.driver.fixtures.RideFixture
import tms.driver.fixtures.RideSpec
import tms.driver.fixtures.TripFixtureExtension
import tms.driver.flows.LoginFlow
import tms.driver.screens.home.OdHomeScreen
import tms.driver.screens.trip.ConfirmShuttleDialog
import tms.driver.screens.trip.GpsAlertDialog
import tms.driver.screens.trip.TripViewScreen
import java.time.Duration

/**
 * Phase T2: starting the trip and choosing a shuttle (scenarios START-001 to START-007, 009 to 012 and 015 of
 * docs/trip-lifecycle-test-plan.md). Each test assigns one ride through the APIs BEFORE the app login (an API login
 * signs the app out) and the fixture always cleans up afterwards, including a ride whose trip was started.
 * Starting a trip is server-side state: after the first start, Home says "Current Trip" / CONTINUE TRIP, even after the
 * app's data is cleared. Every ride sends an SMS to the rider phone in the template.
 * The rides use fixed stops ("Terminal 5E" to "Door 3 - Bus/Shuttle Ctr") so stop names can be asserted.
 */
@Tag("trip")
@Tag("device")
@EnabledIfEnvironmentVariable(named = "TMS_TRIP_API_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TMS_RIDER_API_URL", matches = ".+")
@ExtendWith(DriverExtension::class, TripFixtureExtension::class)
@DisplayName("Driver starts the trip and chooses a shuttle")
class StartTripTest {
    private lateinit var creds: Credentials
    private val pickup = "Terminal 5E"
    private val dropoff = "Door 3 - Bus/Shuttle Ctr"

    @BeforeEach
    fun requireCredentials() {
        val c = Credentials.fromEnvironment()
        assumeTrue(c != null, "TMS_DRIVER_PHONE / TMS_DRIVER_PASSCODE are not set, so this test is skipped")
        creds = c!!
    }

    private fun spec(party: Int, ada: Boolean, seed: Long) = RideSpec(partySize = party, ada = ada, seed = seed, pickupName = pickup, dropoffName = dropoff)

    private fun logInToHomeWithRide(): OdHomeScreen {
        LoginFlow(creds).loginToHome()
        return OdHomeScreen().also { assertTrue(it.waitForTrip(Duration.ofSeconds(60)), "A trip should be shown after logging in") }
    }

    @Test
    @DisplayName("[START-001 / 002 / 011 / 012 / 015] START TRIP asks to confirm the shuttle once, opens one trip view, and CONTINUE TRIP returns to it without asking again")
    fun confirmShuttleAndOpenTrip(rides: RideFixture) {
        step("Given a ride from $pickup to $dropoff is assigned to the QA driver and the driver is on Home")
        rides.assignedRide(spec(party = 5, ada = true, seed = 31))
        val home = logInToHomeWithRide()
        val shuttle = home.shuttleText()
        assertTrue(home.startButtonText().equals("start trip", ignoreCase = true))

        step("When the driver taps START TRIP twice in quick succession")
        home.doubleTapStartTrip()

        step("Then one 'Confirm Shuttle' dialog asks 'You are in <the driver's shuttle>.' with CONFIRM and SELECT DIFFERENT SHUTTLE")
        val dialog = ConfirmShuttleDialog().waitUntilShown()
        assertEquals(1, dialog.count(), "Only one dialog should open")
        assertEquals("Confirm Shuttle", dialog.titleText())
        assertEquals("You are in $shuttle.", dialog.messageText())
        assertEquals("CONFIRM", dialog.confirmText())
        assertEquals("SELECT DIFFERENT SHUTTLE", dialog.differentShuttleText())

        step("When the driver confirms, then the trip view opens at the pickup with an I've Arrived button and the two view tabs")
        dialog.confirm()
        val trip = TripViewScreen().waitUntilShown()
        assertEquals(pickup, trip.currentStopName())
        assertEquals("I've Arrived", trip.arrivedButtonText())
        assertEquals("LIST VIEW", trip.listTabText())
        assertEquals("MAP VIEW", trip.mapTabText())
        assertTrue(trip.estimatedTimeText().isNotBlank(), "An estimated time should be shown")

        step("When the driver goes Back once, then Home shows the trip as the 'Current Trip' with a CONTINUE TRIP button")
        DriverSession.driver.navigate().back()
        assertTrue(home.waitForTrip(Duration.ofSeconds(20)))
        assertEquals("Current Trip", home.summaryText())
        assertTrue(home.startButtonText().equals("continue trip", ignoreCase = true), "Button text was '${home.startButtonText()}'")

        step("And tapping CONTINUE TRIP opens the trip view again without asking to confirm the shuttle")
        home.tapStartTrip()
        assertTrue(TripViewScreen().isShown(Duration.ofSeconds(30)), "The trip view should open again")
        assertFalse(ConfirmShuttleDialog().isShown(Duration.ofSeconds(1)), "The shuttle should not be asked about a second time")
    }

    @Test
    @DisplayName("[START-004 / 005 / 006 / 007] Choosing a different shuttle lists the ride's needs and warns about an unsuitable shuttle")
    fun differentShuttleWarnings(rides: RideFixture) {
        step("Given a ride for 12 guests who need ADA is assigned to the QA driver and the driver is on Home")
        rides.assignedRide(spec(party = 12, ada = true, seed = 32))
        val home = logInToHomeWithRide()
        val ownShuttle = home.shuttleText()

        step("When the driver taps START TRIP and then SELECT DIFFERENT SHUTTLE")
        home.tapStartTrip()
        val picker = ConfirmShuttleDialog().waitUntilShown().selectDifferentShuttle()

        step("Then the picker lists what the ride needs, both shuttles, and the driver's own shuttle is already selected")
        assertEquals("Select Shuttle", picker.titleText())
        assertEquals("Select a shuttle with at least", picker.needsText())
        assertEquals("12 Seats", picker.seatsNeededText())
        assertEquals("ADA", picker.seatTypeNeededText())
        assertEquals("START TRIP", picker.applyText())
        assertTrue(picker.shuttleNames().containsAll(listOf(ownShuttle, "Automated Shuttle-2")), "Shuttles listed: ${picker.shuttleNames()}")
        assertEquals(ownShuttle, picker.selectedShuttleName())

        step("When the driver taps Automated Shuttle-2, then an Alerts sheet warns: it belongs to another driver, is too small and has no ADA")
        val alerts = picker.tapShuttle("Automated Shuttle-2").waitUntilShown()
        assertEquals("Alerts", alerts.titleText())
        // The app lists the bullets in its own order, so the order is not part of the assertion.
        assertEquals(setOf("Belongs to another driver", "Shuttle too small", "Requires ADA"), alerts.alerts().toSet())
        assertEquals("SELECT ANYWAY", alerts.selectAnywayText())
        assertEquals("PICK ANOTHER", alerts.pickAnotherText())

        step("And PICK ANOTHER closes the sheet and does not leave Shuttle-2 selected")
        alerts.pickAnother()
        assertFalse(alerts.isShown(Duration.ofSeconds(3)), "The alerts sheet should close")
        assertNotEquals("Automated Shuttle-2", picker.selectedShuttleName())

        step("And choosing it again and tapping SELECT ANYWAY keeps Shuttle-2 selected")
        val again = picker.tapShuttle("Automated Shuttle-2").waitUntilShown()
        again.selectAnyway()
        assertFalse(again.isShown(Duration.ofSeconds(3)), "The alerts sheet should close")
        assertEquals("Automated Shuttle-2", picker.selectedShuttleName())

        step("When the driver leaves without confirming, then Home still shows the driver's own shuttle and START TRIP")
        repeat(4) { if (!home.isStartButtonShown()) { DriverSession.driver.navigate().back(); Thread.sleep(1_500) } }
        assertTrue(home.waitForTrip(Duration.ofSeconds(20)))
        assertEquals(ownShuttle, home.shuttleText(), "Leaving without confirming must not change the shuttle")
        assertTrue(home.startButtonText().equals("start trip", ignoreCase = true), "The trip must not have started")
    }

    @Test
    @DisplayName("[START-009] With the device's location switched off the trip does not start and the driver is sent to Location settings")
    fun locationOff(rides: RideFixture) {
        step("Given a ride is assigned to the QA driver and the driver is on Home")
        rides.assignedRide(spec(party = 5, ada = false, seed = 33))
        val home = logInToHomeWithRide()
        val adb = Adb()
        val pkg = DriverSession.config.appPackage
        val shots = Artifacts(DriverSession.driver, DriverSession.config.artifactsDir.resolve("StartTripTest.locationOff"))

        try {
            step("When the device's location is switched off and the driver taps START TRIP")
            assertTrue(adb.setLocationEnabled(false).ok, "Could not switch location off through adb")
            Thread.sleep(2_000)
            home.tapStartTrip()

            step("Then 'Provide GPS Access.' explains that location data is needed")
            val gps = GpsAlertDialog().waitUntilShown()
            assertEquals("Provide GPS Access.", gps.titleText())
            assertEquals("Location Data is required so that driver can see your location on map.", gps.messageText())

            step("And OK opens the device's Location settings (confirmed live), and the trip has not started")
            gps.ok()
            val driver = DriverSession.driver
            val deadline = System.nanoTime() + Duration.ofSeconds(8).toNanos()
            while (driver.currentPackage != "com.android.settings" && System.nanoTime() < deadline) Thread.sleep(400)
            assertEquals("com.android.settings", driver.currentPackage, "OK should take the driver to the device's Location settings")
            shots.screenshot("location-settings-after-ok")

            step("When the driver returns to the app, then the ride is still on Home with START TRIP and no trip view")
            adb.setLocationEnabled(true)
            driver.navigate().back()
            assertEquals(pkg, driver.currentPackage, "Back should return to the app")
            assertTrue(home.waitForTrip(Duration.ofSeconds(30)))
            assertTrue(home.startButtonText().equals("start trip", ignoreCase = true), "The trip must not have started")
        } finally {
            adb.setLocationEnabled(true)
        }

    }

    @Test
    @DisplayName("[START-010] Starting the trip while offline does not open the trip, and starting again once back online does")
    fun startWhileOffline(rides: RideFixture) {
        step("Given a ride is assigned to the QA driver and the driver is on Home")
        rides.assignedRide(spec(party = 5, ada = false, seed = 34))
        val home = logInToHomeWithRide()
        val adb = Adb()

        try {
            step("When the device goes offline and the driver taps START TRIP and confirms the shuttle")
            assertTrue(adb.setOffline(true), "Could not switch the device offline")
            Thread.sleep(3_000)
            home.tapStartTrip()
            val dialog = ConfirmShuttleDialog().waitUntilShown()
            Toasts.mark(DriverSession.driver)
            dialog.confirm()

            step("Then the trip does not open and Home still offers START TRIP")
            assertFalse(TripViewScreen().isShown(Duration.ofSeconds(8)), "The trip must not open while offline")
            Allure.addAttachment("message-shown-offline", "text/plain", Toasts.read(DriverSession.driver, Duration.ofSeconds(3)) ?: "(no message captured)")
            assertTrue(home.waitForTrip(Duration.ofSeconds(20)))
            assertTrue(home.startButtonText().equals("start trip", ignoreCase = true), "Button text was '${home.startButtonText()}'")
        } finally {
            adb.setOffline(false)
        }

        step("When the network is back and the driver taps START TRIP again, then the trip opens")
        Thread.sleep(6_000)
        home.tapStartTrip()
        val again = ConfirmShuttleDialog()
        if (again.isShown(Duration.ofSeconds(5))) again.confirm()
        assertTrue(TripViewScreen().isShown(Duration.ofSeconds(40)), "The trip view should open once the network is back")
    }

    private fun step(description: String) {
        Allure.step(description)
        LoggerFactory.getLogger("steps").info("STEP: {}", description)
    }
}
