package tms.driver.backend

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.random.Random

/** Rider API client and ride test data, against a local stub (no device, no real backend). */
@Tag("config")
@DisplayName("Rider API client and ride test data (against a local stub)")
class RiderApiClientTest {
    private var stub: StubRiderApiServer? = null
    @AfterEach fun stop() { stub?.close() }

    private fun start(onCreated: (String) -> Unit = {}): StubRiderApiServer =
        StubRiderApiServer(onRideCreated = onCreated).also { stub = it }

    private fun config(s: StubRiderApiServer, gateway: String? = s.gatewayAuth, bearer: String? = null, org: String = "ODASAP") =
        RiderApiConfig(baseUrl = s.baseUrl, bearer = bearer, gatewayAuth = gateway, orgToken = org)

    @Test
    @DisplayName("The supplied stop list loads with ten stops, each with an id, name and coordinates")
    fun loadsTheStopList() {
        val stops = RideTestData.stops()
        assertEquals(10, stops.size)
        assertTrue(stops.all { it.id > 0 && it.name.isNotBlank() && it.placeId.isNotBlank() })
    }

    @Test
    @DisplayName("A random pickup and drop-off pair always uses two different stops")
    fun randomPairIsDistinct() {
        repeat(200) {
            val (pickup, dropoff) = RideTestData.randomStopPair(Random(it))
            assertNotEquals(pickup.id, dropoff.id)
        }
    }

    @Test
    @DisplayName("A new ride keeps the template's rider details and gets the unique name that was asked for")
    fun buildsARideFromTheTemplate() {
        val ride = RideTestData.newRide("Appium_Rider_1", Random(1))
        assertEquals("Appium_Rider_1", ride.riderName)
        assertEquals(5, ride.totalRiders)
        assertTrue(ride.ada)
        assertEquals("91", ride.countryCode)
    }

    @Test
    @DisplayName("The request JSON has the riders and both stops, and keeps flight and room as null")
    fun requestJsonShape() {
        val text = SimpleJson.write(RiderApiClient.payload(RideTestData.newRide("n", Random(2))))
        assertTrue(text.contains("\"flight_no\":null") && text.contains("\"room_no\":null"), text)
        assertTrue(text.contains("\"total_riders\":\"5\""), "total_riders is sent as a string like the template")
        assertTrue(text.contains("\"pickup_stop\"") && text.contains("\"dropoff_stop\""))
    }

    @Test
    @DisplayName("A ride without a note leaves the note out of the request instead of sending null (staging rejects null)")
    fun omitsANullNote() {
        val text = SimpleJson.write(RiderApiClient.payload(RideTestData.newRide("n", Random(8)).copy(note = null)))
        assertFalse(text.contains("\"note\""), text)
        assertTrue(SimpleJson.write(RiderApiClient.payload(RideTestData.newRide("n", Random(8)).copy(note = "Hello"))).contains("\"note\":\"Hello\""))
    }

    @Test
    @DisplayName("A guest token is obtained with the gateway credential and used to create the ride")
    fun createsARideWithAGuestToken() {
        val created = mutableListOf<String>()
        val s = start { created += it }
        val ride = RiderApiClient(config(s)).createRide(RideTestData.newRide("Appium_Rider_2", Random(3)))
        assertEquals("Appium_Rider_2", ride.riderName)
        assertTrue(ride.rideCode!!.startsWith("STUB"), "the ride code should be read from the response")
        assertEquals(listOf("Appium_Rider_2"), created)
    }

    @Test
    @DisplayName("A ready Bearer token is used directly, without calling the guest-user endpoint")
    fun usesAGivenBearer() {
        val s = start()
        val client = RiderApiClient(config(s, gateway = null, bearer = "Bearer ${s.validToken}"))
        assertTrue(client.createRide(RideTestData.newRide("Appium_Rider_3", Random(4))).rideCode != null)
    }

    @Test
    @DisplayName("A wrong gateway credential is reported as 401 even though the HTTP status is 200")
    fun rejectsWrongGateway() {
        val s = start()
        assertEquals(401, assertThrows(RiderApiException::class.java) { RiderApiClient(config(s, gateway = "Basic d3Jvbmc=")).bearerToken() }.code)
    }

    @Test
    @DisplayName("A wrong organisation token-id is rejected with 400")
    fun rejectsWrongOrganisation() {
        val s = start()
        val ex = assertThrows(RiderApiException::class.java) { RiderApiClient(config(s, org = "OTHER")).createRide(RideTestData.newRide("x", Random(5))) }
        assertEquals(400, ex.code)
    }

    @Test
    @DisplayName("A ride with the same pickup and drop-off is rejected with 400")
    fun rejectsSameStops() {
        val s = start()
        val stop = RideTestData.stops().first()
        val ride = RideTestData.newRide("x", Random(6)).copy(pickup = stop, dropoff = stop)
        assertEquals(400, assertThrows(RiderApiException::class.java) { RiderApiClient(config(s)).createRide(ride) }.code)
    }

    @Test
    @DisplayName("A ride created through the Rider API shows up in the Driver API queue and can be assigned")
    fun riderToDriverFlow() {
        val driver = StubDriverApiServer()
        try {
            val rider = start { name -> driver.enqueueRide(9001, name) }
            val name = RideTestData.uniqueRiderName()
            RiderApiClient(config(rider)).createRide(RideTestData.newRide(name, Random(7)))

            val api = DriverApiClient.login(
                DriverApiConfig(driver.baseUrl, driver.gatewayAuth, "9000000001", "91", driver.goodPasscode, 77),
            )
            val queued = api.waitForRequestByName(name, Duration.ofSeconds(5), Duration.ofMillis(100))
            assertEquals(9001L, queued.id)
            assertTrue(api.assign(queued.id, 77).ok)
            assertFalse(api.isInQueue(queued.id))
        } finally {
            driver.close()
        }
    }

    @Test
    @DisplayName("A production rider API host is refused and credentials never appear in the settings text")
    fun safety() {
        assertThrows(IllegalArgumentException::class.java) { RiderApiConfig("https://api.trackmyshuttle.com/rider/api/v1", "Bearer abc", null) }
        val text = RiderApiConfig("http://127.0.0.1:1/x", "Bearer super-secret", "Basic top-secret").toString()
        assertFalse(text.contains("super-secret") || text.contains("top-secret"))
    }

    @Test
    @DisplayName("The rider can cancel a ride with its ride code and a reason, and a missing reason is refused")
    fun ridersCancel() {
        val s = start()
        val client = RiderApiClient(config(s))
        val code = client.createRide(RideTestData.newRide("Appium_Rider_9", Random(9))).rideCode!!
        val result = client.cancelRide(code, "Change in travel plans")
        assertTrue(result.ok, result.toString())
        assertEquals(listOf(code), s.cancelled.toList())
        assertEquals(422, client.cancelRide(code, "").code)
    }
}
