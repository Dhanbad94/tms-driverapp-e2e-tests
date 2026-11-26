package tms.driver.trip

import io.qameta.allure.Allure
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
import tms.driver.fixtures.RideFixture
import tms.driver.fixtures.RideSpec
import tms.driver.fixtures.TripFixtureExtension
import tms.driver.flows.LoginFlow
import tms.driver.screens.home.OdHomeScreen
import tms.driver.screens.shell.HomeShell
import tms.driver.screens.trip.ConfirmShuttleDialog
import tms.driver.screens.trip.TripViewScreen
import java.time.Duration

/**
 * Phase T17 (PERF-001 of docs/next-features-plan.md): time budgets agreed on 2026-11-23 for the emulator: passcode submitted to Home 10 s,
 * START TRIP confirmed to the trip view 10 s, I've Arrived to the Arrived screen 5 s. The measured seconds are attached to the report.
 */
@Tag("trip")
@Tag("performance")
@Tag("device")
@EnabledIfEnvironmentVariable(named = "TMS_TRIP_API_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TMS_RIDER_API_URL", matches = ".+")
@ExtendWith(DriverExtension::class, TripFixtureExtension::class)
@DisplayName("Driver app response times")
class PerformanceTest {
    private lateinit var creds: Credentials

    @BeforeEach
    fun requireCredentials() {
        val c = Credentials.fromEnvironment()
        assumeTrue(c != null, "TMS_DRIVER_PHONE / TMS_DRIVER_PASSCODE are not set, so this test is skipped")
        creds = c!!
    }

    private inline fun <T> timed(name: String, block: () -> T): Pair<T, Double> {
        val start = System.nanoTime()
        val result = block()
        val seconds = (System.nanoTime() - start) / 1e9
        Allure.addAttachment("seconds-$name", "text/plain", "%.1f".format(seconds))
        LoggerFactory.getLogger("observed").info("OBSERVED seconds-{} = {}", name, "%.1f".format(seconds))
        return result to seconds
    }

    @Tag("smoke")
    @Test
    @DisplayName("[PERF-001] Login reaches Home within 10 s, START TRIP reaches the trip view within 10 s, and I've Arrived reaches the Arrived screen within 5 s")
    fun responseTimes(rides: RideFixture) {
        step("Given a ride is assigned to the driver")
        rides.assignedRide(RideSpec(partySize = 2, ada = false, note = "Timing", seed = 321, pickupName = "Terminal 5E", dropoffName = "Door 3 - Bus/Shuttle Ctr"))

        step("When the driver submits the passcode, then Home is shown within 10 seconds")
        val login = LoginFlow(creds)
        val prompt = login.toPasscodeScreen().enterPasscode(creds.passcode)
        val (_, loginSeconds) = timed("login-to-home") { prompt.submit(); HomeShell().waitUntilShown() }
        val home = OdHomeScreen()
        assertTrue(home.waitForTrip(Duration.ofSeconds(60)), "A trip should be shown")

        step("When the driver starts the trip and confirms the shuttle, then the trip view is shown within 10 seconds")
        home.tapStartTrip()
        val dialog = ConfirmShuttleDialog().waitUntilShown()
        val (trip, startSeconds) = timed("start-trip-to-trip-view") { dialog.confirm(); TripViewScreen().waitUntilShown() }
        trip.waitForEstimatedTimes()

        step("When the driver taps I've Arrived, then the Arrived screen is shown within 5 seconds")
        val (_, arriveSeconds) = timed("arrived-screen") { trip.tapArrived() }

        assertTrue(loginSeconds <= 10, "Login to Home took %.1f s (limit 10 s)".format(loginSeconds))
        assertTrue(startSeconds <= 10, "START TRIP to the trip view took %.1f s (limit 10 s)".format(startSeconds))
        assertTrue(arriveSeconds <= 5, "I've Arrived to the Arrived screen took %.1f s (limit 5 s)".format(arriveSeconds))
    }

    private fun step(description: String) {
        Allure.step(description)
        LoggerFactory.getLogger("steps").info("STEP: {}", description)
    }
}
