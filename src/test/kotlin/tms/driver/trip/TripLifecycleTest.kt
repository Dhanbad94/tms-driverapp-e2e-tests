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
import org.slf4j.LoggerFactory
import tms.driver.backend.DriverApiClient
import tms.driver.backend.DispatchAction
import tms.driver.backend.DriverApiConfig
import tms.driver.backend.RideTestData
import tms.driver.backend.RiderApiClient
import tms.driver.backend.RiderApiConfig
import tms.driver.base.DriverExtension
import tms.driver.core.config.Credentials
import tms.driver.flows.LoginFlow
import tms.driver.screens.home.OdHomeScreen
import java.time.Duration

/**
 * Skipped unless these environment variables are set:
 *  - Rider API: TMS_RIDER_API_URL plus TMS_RIDER_BEARER or TMS_RIDER_GATEWAY_AUTH (and optionally TMS_RIDER_ORG)
 *  - Driver API: TMS_TRIP_API_URL, TMS_TRIP_AUTH_VALUE and an API account (TMS_DISPATCHER_PHONE / _PASSCODE, or
 *    TMS_DISPATCHER_USE_QA_DRIVER=true to use the QA driver itself)
 *  - the QA driver on the device: TMS_DRIVER_PHONE / TMS_DRIVER_PASSCODE
 *
 * Flow, in an order that matters: a ride with a unique rider name and a random pickup/drop-off pair is created
 * through the Rider API; the Driver API finds it in the queue and assigns it to the QA driver; ONLY THEN is the
 * app logged in, and the home screen is checked. Confirmed on staging: logging the same driver in through the API
 * signs the app out, so every API call for the QA driver is made before the app login (and cleanup, which has to
 * log in again, is last). The ride is cancelled at the end so nothing stays active.
 * Creating the ride sends SMS messages to the rider phone in the template (override with TMS_RIDER_PHONE).
 */
@Tag("trip")
@Tag("device")
@EnabledIfEnvironmentVariable(named = "TMS_TRIP_API_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TMS_RIDER_API_URL", matches = ".+")
@ExtendWith(DriverExtension::class)
@DisplayName("Driver trip lifecycle (ride created through the Rider API, assigned through the Driver API)")
class TripLifecycleTest {
    private lateinit var creds: Credentials
    private lateinit var driverConfig: DriverApiConfig
    private lateinit var riderConfig: RiderApiConfig

    @BeforeEach
    fun prepare() {
        val c = Credentials.fromEnvironment()
        assumeTrue(c != null, "TMS_DRIVER_PHONE / TMS_DRIVER_PASSCODE are not set, so this test is skipped")
        creds = c!!
        val d = DriverApiConfig.fromEnvironment()
        assumeTrue(d != null, "No Driver API account is configured (TMS_DISPATCHER_* or TMS_DISPATCHER_USE_QA_DRIVER), so this test is skipped")
        driverConfig = d!!
        val r = RiderApiConfig.fromEnvironment()
        assumeTrue(r != null, "TMS_RIDER_BEARER or TMS_RIDER_GATEWAY_AUTH is not set, so this test is skipped")
        riderConfig = r!!
    }

    @Test
    @DisplayName("[TRIP-001] A ride created through the Rider API and assigned through the Driver API appears on the driver's home screen")
    fun createdAndAssignedRideAppearsOnHome() {
        step("Given the Driver API shows the QA driver has no dispatches yet")
        val dispatcher = DriverApiClient.login(driverConfig)
        assumeTrue(dispatcher.activeDispatchCount() == 0, "The QA driver already has a dispatch; clear it before running this test")

        step("When a ride is created through the Rider API with a unique rider name and a random pickup and drop-off")
        val name = RideTestData.uniqueRiderName()
        val request = RideTestData.newRide(name)
        RiderApiClient(riderConfig).createRide(request)
        Allure.addAttachment("ride", "text/plain", "rider=$name pickup='${request.pickup.name}' dropoff='${request.dropoff.name}'")

        step("And the ride is found in the driver queue and assigned to the QA driver")
        val queued = dispatcher.waitForRequestByName(name)
        var rideId: Long = queued.id
        try {
            val assignee = driverConfig.assigneeDriverId ?: dispatcher.driverId()
            val assign = dispatcher.assign(queued.id, assignee)
            assertTrue(assign.ok, "Assigning should succeed (code=${assign.code}, message='${assign.message}')")

            step("And the QA driver then logs in to the app")
            LoginFlow(creds).loginToHome()
            val home = OdHomeScreen()
            assertTrue(home.waitForTrip(Duration.ofSeconds(60)), "A trip should be shown on the home screen after logging in")

            step("Then the home screen shows one dispatch with the ride's guests, ADA flag and two stops, and a Start Trip button")
            assertEquals("You have 1 dispatch", home.summaryText())
            assertEquals("${request.totalRiders} Guests", home.guestsText())
            assertEquals(request.ada, home.isAdaShown(), "ADA should be shown exactly when the ride is an ADA ride")
            assertEquals("2 Stops", home.stopsText())
            assertTrue(home.startButtonText().equals("start trip", ignoreCase = true), "Button text was '${home.startButtonText()}'")
        } finally {
            step("And the ride is cancelled so nothing stays active (this signs the app out)")
            val cancelled = runCatching { dispatcher.dispatchAction(rideId, DispatchAction.CANCEL, "E2E teardown") }.getOrNull()
            Allure.addAttachment("cleanup", "text/plain", "first token accepted: ${cancelled?.ok == true}")
            if (cancelled?.ok != true) DriverApiClient.login(driverConfig).cancelQuietly(rideId, "E2E teardown")
        }
    }

    private fun step(description: String) {
        Allure.step(description)
        LoggerFactory.getLogger("steps").info("STEP: {}", description)
    }
}
