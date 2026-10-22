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
import tms.driver.backend.DriverApiClient
import tms.driver.backend.DriverApiConfig
import tms.driver.base.DriverExtension
import tms.driver.core.config.Credentials
import tms.driver.core.driver.DriverSession
import tms.driver.flows.LoginFlow
import tms.driver.screens.home.OdHomeScreen
import tms.driver.screens.shell.HomeShell
import java.time.Duration

/**
 * NOT YET EXECUTED against a real backend. Skipped unless the Driver API settings are present
 * (see DriverApiConfig): TMS_TRIP_API_URL, TMS_DISPATCHER_PHONE / _PASSCODE, TMS_TRIP_DRIVER_ID, TMS_TRIP_RIDER_NAME.
 *
 * Flow: a ride for TMS_TRIP_RIDER_NAME is already waiting in the queue (created beforehand through the rider
 * app or its API). A separate dispatcher account assigns it to the QA driver through the Driver API, and the
 * app is checked to show it. The ride is cancelled afterwards so nothing stays active.
 * The dispatcher must not be the driver signed in on the device, because logging the same driver in
 * elsewhere would probably log the app out. The home-screen ids used here are unverified (see OdHomeScreen).
 */
@Tag("trip")
@Tag("device")
@EnabledIfEnvironmentVariable(named = "TMS_TRIP_API_URL", matches = ".+")
@ExtendWith(DriverExtension::class)
@DisplayName("Driver trip lifecycle (ride assigned through the Driver API)")
class TripLifecycleTest {
    private lateinit var creds: Credentials
    private lateinit var config: DriverApiConfig

    @BeforeEach
    fun prepare() {
        val c = Credentials.fromEnvironment()
        assumeTrue(c != null, "TMS_DRIVER_PHONE / TMS_DRIVER_PASSCODE are not set, so this test is skipped")
        creds = c!!
        val cfg = DriverApiConfig.fromEnvironment()
        assumeTrue(cfg != null, "TMS_DISPATCHER_PHONE / TMS_DISPATCHER_PASSCODE are not set, so this test is skipped")
        config = cfg!!
        assumeTrue(config.assigneeDriverId != null, "TMS_TRIP_DRIVER_ID is not set, so this test is skipped")
        assumeTrue(config.riderName != null, "TMS_TRIP_RIDER_NAME is not set, so this test is skipped")
    }

    @Test
    @DisplayName("[TRIP-001] A ride assigned to the driver through the API appears on the driver's home screen")
    fun assignedRideAppearsOnHome() {
        step("Given the QA driver is logged in and the home screen shows no trips")
        LoginFlow(creds).loginToHome()
        val home = OdHomeScreen()
        assumeTrue(home.isEmptyStateShown(), "The QA driver already has a trip; clear it before running this test")

        step("And a dispatcher account is logged in to the Driver API and finds the waiting ride")
        val dispatcher = DriverApiClient.login(config)
        val ride = dispatcher.waitForRequestByName(config.riderName!!)
        try {
            step("When the dispatcher assigns the ride to the QA driver")
            val result = dispatcher.assign(ride.id, config.assigneeDriverId!!)
            assertTrue(result.ok, "Assigning should succeed (code=${result.code}, message='${result.message}')")
            Allure.addAttachment("assigned-ride", "text/plain", "dispatch=${ride.id}")

            step("Then the app shows the trip without the 'no trips' message")
            var shown = home.waitForTrip(Duration.ofSeconds(60))
            if (!shown) {
                // The dashboard also reloads when the app is reopened.
                val appPackage = DriverSession.config.appPackage
                DriverSession.driver.terminateApp(appPackage)
                DriverSession.driver.activateApp(appPackage)
                HomeShell().waitUntilShown()
                shown = home.waitForTrip(Duration.ofSeconds(30))
            }
            assertTrue(shown, "A trip summary or start button should be shown after the ride is assigned")
        } finally {
            step("And the ride is cancelled so nothing stays active")
            dispatcher.cancelQuietly(ride.id, "E2E teardown")
        }
    }

    private fun step(description: String) {
        Allure.step(description)
        LoggerFactory.getLogger("steps").info("STEP: {}", description)
    }
}
