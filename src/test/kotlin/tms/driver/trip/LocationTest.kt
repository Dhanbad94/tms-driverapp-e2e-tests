package tms.driver.trip

import io.qameta.allure.Allure
import org.junit.jupiter.api.AfterEach
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
import org.slf4j.LoggerFactory
import tms.driver.backend.RideTestData
import tms.driver.base.DriverExtension
import tms.driver.core.config.Credentials
import tms.driver.core.device.Adb
import tms.driver.core.driver.DriverSession
import tms.driver.fixtures.RideFixture
import tms.driver.fixtures.RideSpec
import tms.driver.fixtures.TripFixtureExtension
import tms.driver.flows.LoginFlow
import tms.driver.screens.home.OdHomeScreen
import tms.driver.screens.trip.ArrivedScreen
import tms.driver.screens.trip.ConfirmShuttleDialog
import tms.driver.screens.trip.GpsAlertDialog
import tms.driver.screens.trip.TripViewScreen
import java.time.Duration

/**
 * Phase T8: location during a trip (TRIPV-008, TRIPV-009 and GPS-001, 003, 004 of docs/trip-lifecycle-test-plan.md).
 * The emulator's GPS is moved with the emulator console (`geo fix`); the app posts its position, the server works out the time to the
 * stop and the app arrives by itself when that is under a minute. The emulator is put back far from every stop after each test.
 * GPS-002 (how often positions are sent) cannot be read from the device; the arrival within seconds of a move only gives an upper bound.
 */
@Tag("trip")
@Tag("device")
@Tag("location")
@EnabledIfEnvironmentVariable(named = "TMS_TRIP_API_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TMS_RIDER_API_URL", matches = ".+")
@ExtendWith(DriverExtension::class, TripFixtureExtension::class)
@DisplayName("Driver location during a trip")
class LocationTest {
    private lateinit var creds: Credentials
    private val adb = Adb()
    private val pickup = "Terminal 5E"
    private val dropoff = "Door 3 - Bus/Shuttle Ctr"

    /** Mountain View, the emulator's default: thousands of kilometres from the Chicago test stops. */
    private val farAway = 37.4220 to -122.0841

    @BeforeEach
    fun prepare() {
        val c = Credentials.fromEnvironment()
        assumeTrue(c != null, "TMS_DRIVER_PHONE / TMS_DRIVER_PASSCODE are not set, so this test is skipped")
        creds = c!!
        adb.setEmulatorLocation(farAway.first, farAway.second)
        adb.setLocationEnabled(true)
    }

    @AfterEach
    fun putEverythingBack() {
        adb.setLocationEnabled(true)
        adb.setEmulatorLocation(farAway.first, farAway.second)
    }

    private fun startTrip(rides: RideFixture, seed: Long): TripViewScreen {
        rides.assignedRide(RideSpec(partySize = 2, ada = false, note = "Hello", seed = seed, pickupName = pickup, dropoffName = dropoff))
        LoginFlow(creds).loginToHome()
        val home = OdHomeScreen()
        assertTrue(home.waitForTrip(Duration.ofSeconds(60)), "A trip should be shown after logging in")
        home.tapStartTrip()
        ConfirmShuttleDialog().waitUntilShown().confirm()
        return TripViewScreen().waitUntilShown().also { it.waitForEstimatedTimes() }
    }

    @Test
    @DisplayName("[GPS-001] While a trip runs, the app keeps a foreground location service with its notification")
    fun locationServiceRuns(rides: RideFixture) {
        step("Given a trip has started")
        startTrip(rides, 201)

        step("Then the app's location service is a foreground service on the location notification channel")
        val services = adb.run("shell", "dumpsys", "activity", "services", DriverSession.config.appPackage).output
        val block = services.substringAfter("service.gpsLocation.LocationService", "").take(1500)
        observe("location-service-block", block.lines().filter { it.contains("isForeground") || it.contains("foregroundNoti") }.joinToString(" | ").take(400))
        assertTrue(block.contains("isForeground=true"), "LocationService should be a foreground service")
        assertTrue(block.contains("TMS_my_location"), "It should post its notification on the TMS_my_location channel")
    }

    @Test
    @DisplayName("[TRIPV-008 / GPS-004] When the device's location is moved next to the pickup, the app arrives by itself within seconds")
    fun arrivesByItself(rides: RideFixture) {
        step("Given a trip has started and the device is far from $pickup")
        val trip = startTrip(rides, 202)
        val stop = RideTestData.stops().first { it.name == pickup }
        assertTrue(trip.isShown(Duration.ofSeconds(3)))

        step("When the device's location is moved next to $pickup, then the 'Arrived At' screen opens without a tap")
        assertTrue(adb.setEmulatorLocation(stop.latitude, stop.longitude).ok, "Could not move the emulator's location")
        val start = System.nanoTime()
        val arrived = ArrivedScreen()
        assertTrue(arrived.isShown(Duration.ofSeconds(60)), "The arrived screen should open by itself")
        val seconds = Duration.ofNanos(System.nanoTime() - start).seconds
        observe("seconds-until-auto-arrival", seconds.toString())
        assertEquals(pickup, arrived.stopName())
        assertEquals("Arrived At", arrived.subtitleText())
        assertTrue(seconds <= 30, "Arrival should follow the move within seconds, but took ${seconds}s")
    }

    @Test
    @DisplayName("[TRIPV-008b] With the device still next to the pickup, going Back from the arrived screen brings the arrived screen up again (observed behaviour)")
    fun arrivesAgainAfterBack(rides: RideFixture) {
        step("Given the app has arrived by itself at $pickup")
        val trip = startTrip(rides, 205)
        val stop = RideTestData.stops().first { it.name == pickup }
        adb.setEmulatorLocation(stop.latitude, stop.longitude)
        val arrived = ArrivedScreen()
        assertTrue(arrived.isShown(Duration.ofSeconds(60)), "The arrived screen should open by itself")

        step("When the driver goes Back to the trip view while the device is still at the stop, then the arrived screen comes up again")
        arrived.tapBack()
        val again = ArrivedScreen().isShown(Duration.ofSeconds(40))
        observe("arrives-again-after-back", again.toString())
        assertTrue(again, "Observed on staging: the app arrives again; if this starts failing the behaviour was changed")
    }

    @Test
    @DisplayName("[TRIPV-009] While the device stays far from the pickup, the app does not arrive by itself")
    fun doesNotArriveFarAway(rides: RideFixture) {
        step("Given a trip has started and the device is far from $pickup")
        val trip = startTrip(rides, 203)

        step("When 45 seconds pass without moving, then the driver is still on the trip view and the estimate is still hours")
        Thread.sleep(45_000)
        assertTrue(trip.isShown(Duration.ofSeconds(3)), "The trip view should still be shown")
        assertFalse(ArrivedScreen().isShown(Duration.ofSeconds(1)), "The arrived screen must not open")
        val etas = trip.estimatedTimes()
        observe("eta-far-away", etas.joinToString(" | "))
        assertTrue(etas.first().contains("hr"), "The estimate should still be hours away: $etas")
    }

    @Test
    @DisplayName("[GPS-003] Switching the device's location off during a trip makes the app ask for GPS access")
    fun locationOffDuringTrip(rides: RideFixture) {
        step("Given a trip has started")
        val trip = startTrip(rides, 204)

        step("When the device's location services are switched off")
        assertTrue(adb.setLocationEnabled(false).ok, "Could not switch location off through adb")
        try {
            step("Then, within a minute, the app shows 'Provide GPS Access.' (or keeps the trip view; recorded)")
            val gps = GpsAlertDialog()
            val shown = gps.isShown(Duration.ofSeconds(60))
            observe("gps-dialog-during-trip", shown.toString())
            if (shown) {
                assertEquals("Provide GPS Access.", gps.titleText())
                assertEquals("Location Data is required so that driver can see your location on map.", gps.messageText())
            }
            assertTrue(shown, "The app should ask for GPS access during the trip")
        } finally {
            adb.setLocationEnabled(true)
        }
        runCatching { DriverSession.driver.activateApp(DriverSession.config.appPackage) }
        assertTrue(trip.isShown(Duration.ofSeconds(20)) || GpsAlertDialog().isShown(Duration.ofSeconds(2)))
    }

    private fun observe(name: String, value: String) {
        Allure.addAttachment(name, value)
        LoggerFactory.getLogger("observed").info("OBSERVED {} = {}", name, value)
    }

    private fun step(description: String) {
        Allure.step(description)
        LoggerFactory.getLogger("steps").info("STEP: {}", description)
    }
}
