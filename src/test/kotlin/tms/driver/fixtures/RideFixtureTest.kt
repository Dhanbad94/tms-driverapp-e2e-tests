package tms.driver.fixtures

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import tms.driver.backend.DriverApiConfig
import tms.driver.backend.RiderApiConfig
import tms.driver.backend.StubDriverApiServer
import tms.driver.backend.StubRiderApiServer

/** The ride fixture and its cleanup guarantee, proved against local stubs (no device, no SMS, no real backend). */
@Tag("config")
@DisplayName("Ride fixture and cleanup guarantee (against local stubs)")
class RideFixtureTest {
    private val driverStub = StubDriverApiServer()
    private var nextId = 7000L
    private val riderStub = StubRiderApiServer(onRideCreated = { name -> driverStub.enqueueRide(nextId++, name) })

    @AfterEach fun stop() { driverStub.close(); riderStub.close() }

    private fun fixture() = RideFixture(
        DriverApiConfig(driverStub.baseUrl, driverStub.gatewayAuth, "9000000001", "91", driverStub.goodPasscode, 77),
        RiderApiConfig(riderStub.baseUrl, null, riderStub.gatewayAuth),
    )

    @Test
    @DisplayName("An assigned ride is created with the requested party size and ADA flag, found in the queue and assigned")
    fun createsAndAssignsARide() {
        val f = fixture().begin()
        val ride = f.assignedRide(RideSpec(partySize = 2, ada = false, seed = 1))
        assertEquals(2, ride.request.totalRiders)
        assertEquals(false, ride.request.ada)
        assertTrue(ride.assigned)
        assertEquals(1, driverStub.assignedCount())
        f.finish()
    }

    @Test
    @DisplayName("Cleanup cancels every ride the test created and leaves the driver with no active ride")
    fun cleanupRemovesEverything() {
        val f = fixture().begin()
        f.assignedRide(RideSpec(seed = 2)); f.assignedRide(RideSpec(seed = 3))
        assertEquals(2, driverStub.assignedCount())
        f.finish()
        assertEquals(0, driverStub.assignedCount())
    }

    @Test
    @DisplayName("Cleanup also removes a ride that was created but never assigned")
    fun cleanupRemovesQueuedRides() {
        val f = fixture().begin()
        f.queuedRide(RideSpec(seed = 4))
        assertEquals(1, driverStub.queuedCount())
        f.finish()
        assertEquals(0, driverStub.queuedCount())
    }

    @Test
    @DisplayName("Rides left on the driver by an earlier run are cleared before the test starts")
    fun clearsLeftoversAtTheStart() {
        driverStub.enqueueRide(9100, "leftover")
        val api = tms.driver.backend.DriverApiClient.login(DriverApiConfig(driverStub.baseUrl, driverStub.gatewayAuth, "9000000001", "91", driverStub.goodPasscode, 77))
        api.assign(9100, 77)
        assertEquals(1, driverStub.assignedCount())
        fixture().begin()
        assertEquals(0, driverStub.assignedCount())
    }

    @Test
    @DisplayName("If a ride cannot be cancelled, cleanup fails loudly instead of leaving it behind quietly")
    fun failsLoudlyWhenCleanupCannotWork() {
        val f = fixture().begin()
        f.assignedRide(RideSpec(seed = 5))
        driverStub.refuseCancel = true
        val ex = assertThrows(FixtureCleanupException::class.java) { f.finish() }
        assertTrue(ex.message!!.contains("still has"), ex.message)
    }

    @Test
    @DisplayName("A ride that was picked up cannot be cancelled, so cleanup finishes the trip instead")
    fun cleanupFinishesAPickedUpRide() {
        val f = fixture().begin()
        f.assignedRide(RideSpec(seed = 8))
        driverStub.markPickedUp()
        f.finish()
        assertEquals(0, driverStub.assignedCount())
    }

    @Test
    @DisplayName("A picked-up ride left over from an earlier run is cleared before the test starts")
    fun clearsAPickedUpLeftover() {
        driverStub.enqueueRide(9200, "leftover")
        val api = tms.driver.backend.DriverApiClient.login(DriverApiConfig(driverStub.baseUrl, driverStub.gatewayAuth, "9000000001", "91", driverStub.goodPasscode, 77))
        api.assign(9200, 77)
        driverStub.markPickedUp()
        fixture().begin()
        assertEquals(0, driverStub.assignedCount())
    }

    @Test
    @DisplayName("Every ride gets a different rider name")
    fun namesAreUnique() {
        val f = fixture().begin()
        val a = f.queuedRide(RideSpec(seed = 6)); Thread.sleep(3); val b = f.queuedRide(RideSpec(seed = 7))
        assertTrue(a.riderName != b.riderName)
        f.finish()
    }
}
