package tms.driver.trip

import io.qameta.allure.Allure
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.extension.ExtendWith
import org.openqa.selenium.ScreenOrientation
import org.slf4j.LoggerFactory
import tms.driver.backend.DriverApiClient
import tms.driver.backend.DriverApiConfig
import tms.driver.base.DriverExtension
import tms.driver.core.config.Credentials
import tms.driver.core.device.Adb
import tms.driver.core.driver.DriverSession
import tms.driver.fixtures.RideFixture
import tms.driver.fixtures.RideSpec
import tms.driver.fixtures.TripFixtureExtension
import tms.driver.flows.LoginFlow
import tms.driver.screens.auth.LoginScreen
import tms.driver.screens.home.OdHomeScreen
import tms.driver.screens.shell.ConnectivityBanner
import tms.driver.screens.shell.HomeShell
import tms.driver.screens.trip.ConfirmShuttleDialog
import tms.driver.screens.trip.TripViewScreen
import java.time.Duration

/**
 * Phase T7, part 3: the app's own lifecycle with a ride (STATE-001 to 004, 006 and HOME-009 of docs/trip-lifecycle-test-plan.md):
 * closing and reopening the app, sending it to the background, losing the network, rotating the device, signing out with a ride,
 * and a ride assigned by another driver while Home is open.
 */
@Tag("trip")
@Tag("device")
@EnabledIfEnvironmentVariable(named = "TMS_TRIP_API_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TMS_RIDER_API_URL", matches = ".+")
@ExtendWith(DriverExtension::class, TripFixtureExtension::class)
@DisplayName("Driver app lifecycle with a ride")
class StateLifecycleTest {
    private lateinit var creds: Credentials
    private val pickup = "Terminal 5E"
    private val dropoff = "Door 3 - Bus/Shuttle Ctr"

    @BeforeEach
    fun requireCredentials() {
        val c = Credentials.fromEnvironment()
        assumeTrue(c != null, "TMS_DRIVER_PHONE / TMS_DRIVER_PASSCODE are not set, so this test is skipped")
        creds = c!!
    }

    private fun spec(seed: Long) = RideSpec(partySize = 5, ada = true, note = "Hello", seed = seed, pickupName = pickup, dropoffName = dropoff)

    private fun startTripInApp(): TripViewScreen {
        LoginFlow(creds).loginToHome()
        val home = OdHomeScreen()
        assertTrue(home.waitForTrip(Duration.ofSeconds(60)), "A trip should be shown after logging in")
        home.tapStartTrip()
        ConfirmShuttleDialog().waitUntilShown().confirm()
        return TripViewScreen().waitUntilShown().also { it.waitForEstimatedTimes() }
    }

    @Test
    @DisplayName("[STATE-001] Closing the app during a trip and opening it again resumes the trip")
    fun restartResumesTrip(rides: RideFixture) {
        step("Given a trip has started and the driver is on the trip view")
        rides.assignedRide(spec(161))
        startTripInApp()
        val appPackage = DriverSession.config.appPackage

        step("When the app is closed and opened again, then Home offers CONTINUE TRIP (or the trip view is already back)")
        DriverSession.driver.terminateApp(appPackage)
        DriverSession.driver.activateApp(appPackage)
        val home = OdHomeScreen()
        val trip = TripViewScreen()
        val end = System.nanoTime() + Duration.ofSeconds(60).toNanos()
        var where = "nothing yet"
        while (System.nanoTime() < end) {
            if (trip.isShown(Duration.ofSeconds(1))) { where = "trip view"; break }
            if (home.waitForTrip(Duration.ofSeconds(1))) { where = "home"; break }
        }
        observe("screen-after-restart", where)
        if (where == "home") {
            assertEquals("Current Trip", home.summaryText())
            assertTrue(home.startButtonText().equals("continue trip", ignoreCase = true), "Button was '${home.startButtonText()}'")
            home.tapStartTrip()
            if (!trip.isShown(Duration.ofSeconds(8))) ConfirmShuttleDialog().waitUntilShown().confirm()
        }

        step("Then the trip view shows the same two stops")
        trip.waitUntilShown().waitForEstimatedTimes()
        assertEquals(listOf(pickup, dropoff), trip.stopNames())
    }

    @Test
    @DisplayName("[STATE-002] Sending the app to the background and back keeps the trip view as it was")
    fun backgroundKeepsScreen(rides: RideFixture) {
        step("Given a trip has started and the driver is on the trip view")
        rides.assignedRide(spec(162))
        val trip = startTripInApp()

        step("When the app goes to the background for 8 seconds and returns, then the same stops are still shown")
        DriverSession.driver.runAppInBackground(Duration.ofSeconds(8))
        assertTrue(trip.isShown(Duration.ofSeconds(20)), "The trip view should still be there")
        assertEquals(listOf(pickup, dropoff), trip.stopNames())
    }

    @Test
    @DisplayName("[STATE-004] Going offline during a trip shows the offline banner, and the trip view is intact when the network returns")
    fun offlineDuringTrip(rides: RideFixture) {
        step("Given a trip has started and the driver is on the trip view")
        rides.assignedRide(spec(163))
        val trip = startTripInApp()
        val adb = Adb()
        val banner = ConnectivityBanner()
        try {
            step("When the network is switched off, then the offline banner appears and the trip view stays")
            assertTrue(adb.setOffline(true), "Could not switch the network off")
            observe("banner-offline", banner.waitUntilShown(Duration.ofSeconds(40)))
            assertTrue(trip.isShown(Duration.ofSeconds(5)))
        } finally {
            adb.setOffline(false)
        }
        step("When the network returns, then the banner goes and the same stops are listed")
        observe("banner-back-online", banner.waitForBackOnline(Duration.ofSeconds(60)).orEmpty())
        assertTrue(banner.waitUntilHidden(Duration.ofSeconds(40)), "The banner should disappear")
        assertEquals(listOf(pickup, dropoff), trip.stopNames())
    }

    @Test
    @DisplayName("[STATE-006] Turning the device sideways does not turn the app: it stays in portrait")
    fun staysPortrait(rides: RideFixture) {
        step("Given a trip has started and the driver is on the trip view")
        rides.assignedRide(spec(164))
        val trip = startTripInApp()
        val driver = DriverSession.driver
        try {
            step("When the device is rotated to landscape, then the app is still taller than wide")
            runCatching { driver.rotate(ScreenOrientation.LANDSCAPE) }
            Thread.sleep(2_500)
            val size = driver.manage().window().size
            observe("window-size-after-rotate", "${size.width}x${size.height}")
            assertTrue(size.height > size.width, "The app should stay in portrait, but the window is ${size.width}x${size.height}")
            assertTrue(trip.isShown(Duration.ofSeconds(5)))
        } finally {
            runCatching { driver.rotate(ScreenOrientation.PORTRAIT) }
        }
    }

    @Test
    @DisplayName("[STATE-003] Signing out while a ride is assigned: the ride stays assigned and is back on Home after signing in again")
    fun signOutWithRide(rides: RideFixture) {
        step("Given a ride is assigned and the driver is on Home")
        rides.assignedRide(spec(165))
        LoginFlow(creds).loginToHome()
        val home = OdHomeScreen()
        assertTrue(home.waitForTrip(Duration.ofSeconds(60)))

        step("When the driver opens Profile and signs out, then the app shows what it does")
        val dialog = HomeShell().openProfile().tapSignOut()
        observe("sign-out-dialog", "${dialog.titleText()} / ${dialog.messageText()}")
        dialog.confirm()
        val login = LoginScreen()
        val signedOut = login.isShown(Duration.ofSeconds(25))
        observe("signed-out", signedOut.toString())

        step("Then, after signing in again, Home still shows the ride")
        if (signedOut) LoginFlow(creds).loginToHome()
        assertTrue(home.waitForTrip(Duration.ofSeconds(60)), "The ride should still be assigned to the driver")
        assertEquals("You have 1 dispatch", home.summaryText())
    }

    @Test
    @DisplayName("[HOME-009] A ride that another driver assigns while the driver is on Home appears there by itself")
    fun rideAssignedWhileOnHome(rides: RideFixture) {
        val secondConfig = DriverApiConfig.forSecondDriverFromEnvironment()
        assumeTrue(secondConfig != null, "TMS_DRIVER2_PHONE / TMS_DRIVER2_PASSCODE are not set, so this test is skipped")
        step("Given a ride is waiting in the queue and the driver is on Home with no trip")
        val ride = rides.queuedRide(spec(166))
        val qaId = rides.qaDriverId()
        val operator = DriverApiClient.login(secondConfig!!)
        LoginFlow(creds).loginToHome()
        val home = OdHomeScreen()
        assertTrue(home.waitForEmptyState(Duration.ofSeconds(40)))

        step("When the second driver assigns the ride to the QA driver, then Home shows 'You have 1 dispatch' by itself")
        val result = operator.assign(ride.dispatchId, qaId)
        assertTrue(result.ok, "The assignment should work: $result")
        val start = System.nanoTime()
        assertTrue(home.waitForTrip(Duration.ofSeconds(60)), "The ride should appear on Home")
        observe("seconds-until-card", Duration.ofNanos(System.nanoTime() - start).seconds.toString())
        assertEquals("You have 1 dispatch", home.summaryText())
        assertEquals("5 Guests", home.guestsText())
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
