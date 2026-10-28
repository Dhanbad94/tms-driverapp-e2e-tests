package tms.driver.backend

import org.openqa.selenium.json.Json
import kotlin.random.Random

/**
 * Test data for creating a ride: the stop list and the request template supplied by the product owner
 * (src/test/resources/testdata/stop-list.json and ride-request.json).
 */
object RideTestData {
    private val json = Json()

    private fun resource(name: String): String =
        RideTestData::class.java.getResourceAsStream("/testdata/$name")?.readBytes()?.toString(Charsets.UTF_8)
            ?: error("Missing test data resource /testdata/$name")

    private fun map(text: String): Map<*, *> = json.toType(text, Map::class.java)

    /** All stops of the organisation, from stop-list.json. */
    fun stops(): List<Stop> {
        val list = ((map(resource("stop-list.json"))["response"] as Map<*, *>)["stops"] as List<*>)
        return list.filterIsInstance<Map<*, *>>().map {
            Stop(
                id = (it["id"] as Number).toLong(),
                placeId = it["place_id"].toString(),
                name = it["name"].toString(),
                address = it["address"].toString(),
                latitude = (it["latitude"] as Number).toDouble(),
                longitude = (it["longitude"] as Number).toDouble(),
            )
        }
    }

    /** Two different random stops: pickup first, then drop-off. */
    fun randomStopPair(random: Random = Random.Default): Pair<Stop, Stop> {
        val all = stops()
        require(all.size >= 2) { "stop-list.json needs at least two stops" }
        val pickup = all[random.nextInt(all.size)]
        val dropoff = all.filter { it.id != pickup.id }.let { it[random.nextInt(it.size)] }
        return pickup to dropoff
    }

    /**
     * A ride built from the request template with a UNIQUE rider name (so it can be found in the driver queue)
     * and a random pickup/drop-off pair. Rider country, phone, party size, ADA flag and note come from the template.
     */
    fun newRide(uniqueName: String, random: Random = Random.Default, pickupName: String? = null, dropoffName: String? = null): RideRequest {
        val riders = map(resource("ride-request.json"))["riders"] as Map<*, *>
        val random2 = randomStopPair(random)
        val all = stops()
        fun named(n: String) = all.firstOrNull { it.name == n } ?: error("No stop named '$n' in stop-list.json")
        val pickup = pickupName?.let(::named) ?: random2.first
        val dropoff = dropoffName?.let(::named) ?: random2.second
        require(pickup.id != dropoff.id) { "pickup and drop-off must differ" }
        return RideRequest(
            riderName = uniqueName,
            countryCode = riders["country"].toString(),
            phone = System.getenv("TMS_RIDER_PHONE")?.filter { it.isDigit() }?.takeIf { it.isNotEmpty() } ?: riders["phone"].toString(),
            totalRiders = riders["total_riders"].toString().toInt(),
            ada = riders["ada"] as Boolean,
            note = riders["note"]?.toString(),
            pickup = pickup,
            dropoff = dropoff,
        )
    }

    fun uniqueRiderName(prefix: String = "Appium_Rider"): String = "${prefix}_${System.currentTimeMillis()}"
}
