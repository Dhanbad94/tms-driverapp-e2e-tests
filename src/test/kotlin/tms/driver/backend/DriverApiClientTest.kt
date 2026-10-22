package tms.driver.backend

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * Checks the Driver API client against a local stub that mirrors the contract of the Playwright suite
 * (no device, no real backend). The real staging API has not been called yet.
 */
@Tag("config")
@DisplayName("Driver API client (against a local stub)")
class DriverApiClientTest {
    private lateinit var stub: StubDriverApiServer

    @BeforeEach fun start() { stub = StubDriverApiServer() }
    @AfterEach fun stop() { stub.close() }

    private fun config(gateway: String? = stub.gatewayAuth, passcode: String = stub.goodPasscode) = DriverApiConfig(
        baseUrl = stub.baseUrl, gatewayAuthValue = gateway,
        dispatcherPhone = "9000000001", dispatcherIsd = "91", dispatcherPasscode = passcode,
        assigneeDriverId = 77, riderName = null,
    )

    private fun client() = DriverApiClient.login(config())

    @Test
    @DisplayName("A dispatcher with the right gateway credential and passcode can log in")
    fun logsIn() { client() }

    @Test
    @DisplayName("A wrong gateway credential is reported as 401 even though the HTTP status is 200")
    fun rejectsWrongGateway() {
        assertEquals(401, assertThrows(DriverApiException::class.java) { DriverApiClient.login(config(gateway = "Basic d3Jvbmc=")) }.code)
    }

    @Test
    @DisplayName("A missing gateway credential is reported as 401")
    fun rejectsMissingGateway() {
        assertEquals(401, assertThrows(DriverApiException::class.java) { DriverApiClient.login(config(gateway = null)) }.code)
    }

    @Test
    @DisplayName("A wrong passcode is reported as 400")
    fun rejectsWrongPasscode() {
        assertEquals(400, assertThrows(DriverApiException::class.java) { DriverApiClient.login(config(passcode = "0000")) }.code)
    }

    @Test
    @DisplayName("A ride waiting in the queue is found by its rider name")
    fun findsQueuedRideByName() {
        stub.enqueueRide(501, "PW_Rider_a1")
        val row = client().waitForRequestByName("PW_Rider_a1", Duration.ofSeconds(5), Duration.ofMillis(100))
        assertEquals(501L, row.id)
        assertEquals(1, row.status)
    }

    @Test
    @DisplayName("Waiting for a ride that never appears fails with a clear message")
    fun failsWhenRideNeverAppears() {
        val ex = assertThrows(DriverApiException::class.java) { client().waitForRequestByName("nobody", Duration.ofMillis(400), Duration.ofMillis(100)) }
        assertTrue(ex.message!!.contains("nobody"))
    }

    @Test
    @DisplayName("Assigning a queued ride to a driver succeeds and removes it from the queue")
    fun assignsARide() {
        stub.enqueueRide(502, "r")
        val api = client()
        val result = api.assign(502, 77)
        assertTrue(result.ok, "assign should succeed: $result")
        assertEquals(1, result.successCount)
        assertFalse(api.isInQueue(502), "the ride should have left the unassigned queue")
    }

    @Test
    @DisplayName("Assigning a ride that does not exist is reported as failed (code 400)")
    fun assignFailsForUnknownRide() {
        val result = client().assign(999999, 77)
        assertFalse(result.ok)
        assertEquals(400, result.code)
    }

    @Test
    @DisplayName("An assigned ride can be completed or cancelled with a readable confirmation message")
    fun completesAndCancels() {
        stub.enqueueRide(503, "a"); stub.enqueueRide(504, "b")
        val api = client()
        api.assign(503, 77); api.assign(504, 77)
        assertTrue(api.dispatchAction(503, DispatchAction.COMPLETE, "test").message.lowercase().contains("completed"))
        assertTrue(api.dispatchAction(504, DispatchAction.CANCEL, "test").message.lowercase().contains("cancel"))
    }

    @Test
    @DisplayName("A dispatch action on an unknown dispatch is rejected with code 400")
    fun dispatchActionValidation() {
        val api = client()
        assertEquals(400, api.dispatchAction(424242, DispatchAction.CANCEL, "x").code)
    }

    @Test
    @DisplayName("Proceeding after an assignment starts the trip (trip_started goes from 0 to 1)")
    fun proceedStartsTheTrip() {
        stub.enqueueRide(505, "c")
        val api = client()
        api.assign(505, 77)
        assertEquals(0, api.tripStarted())
        assertTrue(api.proceed().ok)
        assertEquals(1, api.tripStarted())
    }

    @Test
    @DisplayName("Cleanup never throws even when the ride is already gone")
    fun cleanupIsQuiet() { client().cancelQuietly(31337) }

    @Test
    @DisplayName("Production and preproduction hosts are refused, staging is accepted")
    fun refusesProductionHosts() {
        assertThrows(IllegalArgumentException::class.java) { DriverApiConfig.requireNonProductionHost("https://driverapp.trackmyshuttle.com/driver/api/v1") }
        assertThrows(IllegalArgumentException::class.java) { DriverApiConfig.requireNonProductionHost("https://driverapp-preprod.trackmyshuttle.com/driver/api/v1") }
        DriverApiConfig.requireNonProductionHost("https://driverapi-staging.trackmyshuttle.com/driver/api/v3")
    }

    @Test
    @DisplayName("Credentials never appear when the connection settings are logged")
    fun masksSecrets() {
        val text = DriverApiConfig("http://127.0.0.1:1/x", "Basic super-secret", "9000000001", "91", "9876", 1, null).toString()
        assertFalse(text.contains("super-secret") || text.contains("9876") || text.contains("9000000001"))
    }
}
