package tms.driver.backend

import org.openqa.selenium.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Test-side client for the TMS Rider API, used to create an on-demand (ASAP) ride the way the rider web app does.
 *
 * Contract, from the product owner and the Rider-Web source:
 *  - `POST {base}/guest-user` with the rider gateway `Authorization: Basic ...` returns `response.token` (a Bearer token);
 *  - `POST {base}/request` with `Authorization: Bearer <token>` and the header `token-id: <org code>` (e.g. ODASAP)
 *    and the ride JSON creates the ride.
 * The exact success body of /request is not confirmed, so the ride code is looked up defensively.
 * Not yet called against the real staging API.
 */
class RiderApiException(val code: Int, message: String) : RuntimeException("Rider API error $code: $message")

/** A stop as listed by the rider API. */
data class Stop(
    val id: Long,
    val placeId: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
)

/** The ride to create. Rider fields mirror the `riders` object of the request JSON. */
data class RideRequest(
    val riderName: String,
    val countryCode: String,
    val phone: String,
    val totalRiders: Int,
    val ada: Boolean,
    val note: String?,
    val pickup: Stop,
    val dropoff: Stop,
    val flightNo: String? = null,
    val roomNo: String? = null,
)

data class CreatedRide(val riderName: String, val rideCode: String?)

/** Result of the rider cancelling a ride. */
data class RiderCancelResult(val ok: Boolean, val code: Int, val message: String)

/**
 * Settings from environment variables only.
 *
 * | Variable | Meaning |
 * |---|---|
 * | TMS_RIDER_API_URL | e.g. `https://<staging-rider-api-host>/rider/api/v1` (non-production hosts only) |
 * | TMS_RIDER_BEARER | a ready Bearer token (optional; secret) |
 * | TMS_RIDER_GATEWAY_AUTH | full `Basic <base64>` value used by /guest-user when no Bearer is given (secret) |
 * | TMS_RIDER_ORG | value of the `token-id` header, default `ODASAP` |
 */
class RiderApiConfig(
    val baseUrl: String,
    private val bearer: String?,
    private val gatewayAuth: String?,
    val orgToken: String = "ODASAP",
) {
    init { DriverApiConfig.requireNonProductionHost(baseUrl) }

    internal fun bearer(): String? = bearer
    internal fun gatewayAuth(): String? = gatewayAuth

    override fun toString() = "RiderApiConfig(baseUrl=$baseUrl, org=$orgToken, bearer=${if (bearer == null) "none" else "***"}, gateway=${if (gatewayAuth == null) "none" else "***"})"

    companion object {
        fun fromEnvironment(): RiderApiConfig? {
            fun env(name: String) = System.getenv(name)?.takeIf { it.isNotBlank() }
            val base = env("TMS_RIDER_API_URL") ?: return null
            val bearer = env("TMS_RIDER_BEARER")
            val gateway = env("TMS_RIDER_GATEWAY_AUTH")
            if (bearer == null && gateway == null) return null
            return RiderApiConfig(base, bearer, gateway, env("TMS_RIDER_ORG") ?: "ODASAP")
        }
    }
}

class RiderApiClient(
    private val config: RiderApiConfig,
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
) {
    private val json = Json()

    /** Returns the configured Bearer token, or obtains a guest token from /guest-user. */
    fun bearerToken(): String {
        config.bearer()?.let { return it.removePrefix("Bearer ").trim() }
        val gateway = config.gatewayAuth() ?: throw RiderApiException(401, "no Bearer token and no gateway credential configured")
        val body = mapOf(
            "device_id" to "web", "device_token" to "000000", "device_type" to 3, "device_version" to "string",
            "platform" to 3, "device_model" to "", "device_ip_address" to "", "app_version" to "v1",
            "uuid" to "appium-e2e-${System.nanoTime()}",
        )
        val res = post("/guest-user", body, mapOf("Authorization" to gateway))
        val token = (res.map["response"] as? Map<*, *>)?.get("token")?.toString()
        if (res.http != 200 || token.isNullOrBlank()) throw RiderApiException(res.code, res.message.ifBlank { "guest-user failed" })
        return token
    }

    /** POST /request: creates the ride. */
    fun createRide(request: RideRequest, token: String = bearerToken()): CreatedRide {
        val res = post(
            "/request", payload(request),
            mapOf("Authorization" to "Bearer $token", "token-id" to config.orgToken),
        )
        if (res.http !in 200..299 || res.code != 200) throw RiderApiException(res.code, res.message.ifBlank { "request failed" })
        return CreatedRide(request.riderName, findRideCode(res.map))
    }

    /**
     * POST /cancel-ride: the RIDER cancels a ride (what the rider app does; the endpoint comes from the Playwright rider suite).
     * Body is the ride code and a reason. The token must be the one the ride was created with, which is the case with a configured Bearer.
     */
    fun cancelRide(rideCode: String, reason: String, token: String = bearerToken()): RiderCancelResult {
        val res = post(
            "/cancel-ride", linkedMapOf("code" to rideCode, "reason" to reason),
            mapOf("Authorization" to "Bearer $token", "token-id" to config.orgToken),
        )
        return RiderCancelResult(res.http in 200..299 && res.code == 200, res.code, res.message)
    }

    private class Response(val http: Int, val map: Map<*, *>) {
        val code: Int get() = (map["code"] as? Number)?.toInt() ?: http
        val message: String get() = map["message"]?.toString().orEmpty()
    }

    private fun post(path: String, body: Map<String, Any?>, headers: Map<String, String>): Response {
        val builder = HttpRequest.newBuilder(URI.create(config.baseUrl.trimEnd('/') + path))
            .timeout(Duration.ofSeconds(30))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(SimpleJson.write(body)))
        headers.forEach { (k, v) -> builder.header(k, v) }
        val res = http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        val map: Map<*, *> = runCatching<Map<*, *>> { json.toType(res.body(), Map::class.java) }.getOrElse { emptyMap<String, Any?>() }
        return Response(res.statusCode(), map)
    }

    companion object {
        /** The request JSON, with null flight/room numbers kept (the server may expect the keys). */
        fun payload(r: RideRequest): Map<String, Any?> {
            val riders = linkedMapOf<String, Any?>(
                "name" to r.riderName, "country" to r.countryCode, "phone" to r.phone,
                "total_riders" to r.totalRiders.toString(), "ada" to r.ada,
            )
            // Confirmed on staging: "note": null is rejected (400 Invalid Request); leaving the key out is accepted.
            if (!r.note.isNullOrEmpty()) riders["note"] = r.note
            riders["flight_no"] = r.flightNo?.takeIf { it.isNotEmpty() }
            riders["room_no"] = r.roomNo?.takeIf { it.isNotEmpty() }
            return linkedMapOf("riders" to riders, "pickup_stop" to stop(r.pickup), "dropoff_stop" to stop(r.dropoff))
        }

        private fun stop(s: Stop): Map<String, Any?> = linkedMapOf(
            "id" to s.id, "place_id" to s.placeId, "name" to s.name, "address" to s.address,
            "latitude" to s.latitude, "longitude" to s.longitude,
        )

        /** The success body shape is unconfirmed, so search the whole body for `ride_code`. */
        fun findRideCode(node: Any?): String? = when (node) {
            is Map<*, *> -> node["ride_code"]?.toString() ?: node.values.firstNotNullOfOrNull { findRideCode(it) }
            is List<*> -> node.firstNotNullOfOrNull { findRideCode(it) }
            else -> null
        }
    }
}

/** Minimal JSON writer that keeps `null` values (some serializers drop them). */
internal object SimpleJson {
    fun write(value: Any?): String = StringBuilder().also { append(it, value) }.toString()

    private fun append(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is String -> quote(sb, v)
            is Boolean -> sb.append(v)
            is Number -> sb.append(if (v is Double && v % 1.0 == 0.0 && Math.abs(v) < 1e15) v.toLong().toString() else v.toString())
            is Map<*, *> -> {
                sb.append('{')
                v.entries.forEachIndexed { i, e -> if (i > 0) sb.append(','); quote(sb, e.key.toString()); sb.append(':'); append(sb, e.value) }
                sb.append('}')
            }
            is List<*> -> { sb.append('['); v.forEachIndexed { i, x -> if (i > 0) sb.append(','); append(sb, x) }; sb.append(']') }
            else -> quote(sb, v.toString())
        }
    }

    private fun quote(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) when (c) {
            '"' -> sb.append("\\\""); '\\' -> sb.append("\\\\"); '\n' -> sb.append("\\n"); '\r' -> sb.append("\\r"); '\t' -> sb.append("\\t")
            else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
        }
        sb.append('"')
    }
}
