package tms.driver.fixtures

import tms.driver.backend.CreatedRide
import tms.driver.backend.DriverApiClient
import tms.driver.backend.DriverApiConfig
import tms.driver.backend.RideRequest
import tms.driver.backend.RideTestData
import tms.driver.backend.RiderApiClient
import tms.driver.backend.RiderApiConfig
import java.time.Duration
import kotlin.random.Random

/** What a test needs from a ride. Anything not given keeps the template's value. */
data class RideSpec(
    val partySize: Int = 5,
    val ada: Boolean = true,
    val note: String? = "Hello",
    /** Seed for the random pickup and drop-off pair; it is logged so a failure can be reproduced. */
    val seed: Long = System.nanoTime(),
    /** Use these named stops from stop-list.json instead of a random pair (for tests that assert stop names). */
    val pickupName: String? = null,
    val dropoffName: String? = null,
    val flightNo: String? = null,
    val roomNo: String? = null,
    /** Start of the rider's name (a unique number is appended), for tests about long or special-character names. */
    val namePrefix: String? = null,
)

/** A ride this fixture created. [dispatchId] is known once the ride has been found in the driver queue. */
data class TestRide(val riderName: String, val request: RideRequest, val dispatchId: Long, val assigned: Boolean, val rideCode: String? = null)

class FixtureCleanupException(message: String) : AssertionError(message)

/**
 * Creates rides through the Rider API, assigns them through the Driver API and always cleans up.
 *
 * Rules it enforces (all confirmed on staging):
 *  - API work for the QA driver happens BEFORE the app login, because an API login signs the app out;
 *  - every ride gets a unique rider name, so it can be found in the shared queue;
 *  - [finish] cancels every ride it created, then checks the QA driver is left with no active ride, and FAILS the test
 *    if it is not, because a leftover dispatch would break every later test.
 */
class RideFixture(
    private val driverConfig: DriverApiConfig,
    private val riderConfig: RiderApiConfig,
) {
    private val tracked = mutableListOf<TestRide>()
    private lateinit var dispatcher: DriverApiClient
    private val rider = RiderApiClient(riderConfig)

    val createdRides: List<TestRide> get() = tracked.toList()

    /** Logs in to the Driver API and removes any ride left on the QA driver by an earlier run (recipe S0). */
    fun begin(): RideFixture {
        dispatcher = DriverApiClient.login(driverConfig)
        clearActiveRides(dispatcher)
        verifyNoActiveRides("before the test, after clearing leftovers")
        return this
    }

    /** Creates a ride and assigns it to the QA driver (recipe S1). */
    fun assignedRide(spec: RideSpec = RideSpec()): TestRide {
        val ride = create(spec)
        ensureDispatcherSession()
        val result = dispatcher.assign(ride.dispatchId, driverConfig.assigneeDriverId ?: dispatcher.driverId())
        check(result.ok) { "Assigning ride '${ride.riderName}' failed (code=${result.code}, message='${result.message}')" }
        return ride.copy(assigned = true).also { tracked[tracked.indexOfFirst { t -> t.dispatchId == ride.dispatchId }] = it }
    }

    /**
     * Starts the QA driver's trip through the API (`/proceed` is the first call that starts an on-demand trip). Do it BEFORE the app
     * login; the app then opens with "Current Trip" / CONTINUE TRIP.
     */
    fun startTripByApi() {
        ensureDispatcherSession()
        val result = dispatcher.proceed()
        check(result.ok) { "Starting the trip through the API failed (code=${result.code}, message='${result.message}')" }
    }

    /**
     * The RIDER cancels the ride (the rider app's cancel). This does not touch the Driver API, so the app session stays valid, and it
     * can be called while the app is open. Fails the test if the ride has no ride code or the server refuses.
     */
    fun riderCancels(ride: TestRide, reason: String = "Change in travel plans") {
        val code = checkNotNull(ride.rideCode) { "The ride '${ride.riderName}' has no ride code, so the rider cannot cancel it" }
        val result = rider.cancelRide(code, reason)
        check(result.ok) { "The rider's cancel of '${ride.riderName}' failed (code=${result.code}, message='${result.message}')" }
    }

    /**
     * Creates a ride through the Rider API ONLY, so it can be done while the app is open (any Driver API login would sign the app out).
     * The ride is not tracked: the test must remove it itself with [riderCancels], and it has no dispatch id yet.
     */
    fun liveRide(spec: RideSpec = RideSpec()): TestRide {
        val name = spec.namePrefix?.let { RideTestData.uniqueRiderName(it) } ?: RideTestData.uniqueRiderName()
        val base = RideTestData.newRide(name, Random(spec.seed), spec.pickupName, spec.dropoffName)
        val request = base.copy(totalRiders = spec.partySize, ada = spec.ada, note = spec.note, flightNo = spec.flightNo, roomNo = spec.roomNo)
        val created: CreatedRide = rider.createRide(request)
        println("FIXTURE live ride '$name' ride_code=${created.rideCode} seed=${spec.seed}")
        return TestRide(name, request, -1, assigned = false, rideCode = created.rideCode)
    }

    /** The QA driver's numeric id (from its API login token), for tests that let ANOTHER driver assign a ride to it. */
    fun qaDriverId(): Long { ensureDispatcherSession(); return driverConfig.assigneeDriverId ?: dispatcher.driverId() }

    /** Creates a ride and leaves it waiting, unassigned, in the queue (recipe S2). */
    fun queuedRide(spec: RideSpec = RideSpec()): TestRide = create(spec)

    /** Cancels one ride now (for tests that remove a ride on purpose). Uses a fresh login, which signs the app out. */
    fun cancel(ride: TestRide, reason: String = "E2E test") {
        val client = DriverApiClient.login(driverConfig)
        val result = client.cancelRide(ride.dispatchId, reason)
        check(result.ok) { "Cancelling '${ride.riderName}' failed (code=${result.code}, message='${result.message}')" }
    }

    /** Always call at the end of a test. Cancels what is left and fails if the QA driver is not clean afterwards. */
    fun finish() {
        val client = runCatching { DriverApiClient.login(driverConfig) }.getOrElse {
            throw FixtureCleanupException("Cleanup could not log in to the Driver API: ${it.message}")
        }
        dispatcher = client
        tracked.forEach { runCatching { client.cancelRide(it.dispatchId, "E2E teardown") } }
        // Anything still active on the QA driver (a ride we lost track of, or one that was picked up) is cleared too.
        clearActiveRides(client)
        verifyNoActiveRides("after cleanup")
        // Rides left unassigned in the shared queue would stay there for ever, so they must be gone too.
        val stillQueued = tracked.filter { r -> runCatching { client.isInQueue(r.dispatchId) }.getOrDefault(false) }
        if (stillQueued.isNotEmpty()) {
            throw FixtureCleanupException("Cleanup left ${stillQueued.size} test ride(s) in the shared queue: ${stillQueued.map { it.riderName }}")
        }
    }

    /**
     * Confirmed on staging: logging the QA driver in to the APP invalidates the driver's API token (the next call answers 401
     * "Unauthorised User"), just as an API login signs the app out. A test that creates a second ride after the app login
     * therefore needs a fresh API session, so this checks the session and logs in again if it was cut off.
     */
    private fun ensureDispatcherSession() {
        val ok = runCatching { dispatcher.myActiveRides() }.isSuccess
        if (!ok) dispatcher = DriverApiClient.login(driverConfig)
    }

    private fun create(spec: RideSpec): TestRide {
        ensureDispatcherSession()
        val name = spec.namePrefix?.let { RideTestData.uniqueRiderName(it) } ?: RideTestData.uniqueRiderName()
        val base = RideTestData.newRide(name, Random(spec.seed), spec.pickupName, spec.dropoffName)
        val request = base.copy(totalRiders = spec.partySize, ada = spec.ada, note = spec.note, flightNo = spec.flightNo, roomNo = spec.roomNo)
        val created: CreatedRide = rider.createRide(request)
        val queued = dispatcher.waitForRequestByName(name, Duration.ofSeconds(40))
        return TestRide(name, request, queued.id, assigned = false, rideCode = created.rideCode).also {
            tracked += it
            println("FIXTURE ride '$name' ride_code=${created.rideCode} dispatch=${queued.id} pickup='${request.pickup.name}' " +
                "dropoff='${request.dropoff.name}' party=${request.totalRiders} ada=${request.ada} seed=${spec.seed}")
        }
    }

    /**
     * Removes every active ride from the QA driver. An assigned ride is cancelled. A ride that was already picked up cannot
     * be cancelled (confirmed on staging), so the trip is finished instead with /proceed, which acts on the logged-in
     * driver's own trip; that is only safe when the API account is the QA driver itself.
     */
    private fun clearActiveRides(client: DriverApiClient) {
        client.myActiveRides().forEach { client.cancelQuietly(it.id, "E2E cleanup") }
        var left = runCatching { client.myActiveRides() }.getOrDefault(emptyList())
        if (left.isEmpty()) return
        val ownTrip = driverConfig.assigneeDriverId?.let { it == client.driverId() } ?: true
        if (!ownTrip) return
        repeat(6) {
            if (left.isEmpty()) return
            client.proceed()
            Thread.sleep(1_500)
            left = runCatching { client.myActiveRides() }.getOrDefault(left)
        }
    }

    private fun verifyNoActiveRides(moment: String) {
        val active = dispatcher.myActiveRides()
        if (active.isNotEmpty()) {
            throw FixtureCleanupException("The QA driver still has ${active.size} active ride(s) $moment: ids ${active.map { it.id }} (names ${active.map { it.name.ifEmpty { "(none shown)" } }})")
        }
    }
}
