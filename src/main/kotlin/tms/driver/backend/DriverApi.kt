package tms.driver.backend

import org.openqa.selenium.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Test-side client for the TMS Driver API, used to put a ride in front of the QA driver and to clean it up.
 *
 * The contract below is taken from the sibling Playwright rider-app suite (utils/api/driver-client.ts), which
 * exercises the same API: two-layer auth (a Basic gateway credential for /login, then a per-driver Bearer token),
 * and the dispatch calls /requests, /assign-request, /proceed, /dashboard and /dispatch-action. It is NOT yet
 * verified against the staging build the app uses (the app calls .../driver/api/v3; the Playwright suite uses v1).
 *
 * The API answers HTTP 200 even for failures and reports the real result in the body `code` (401, 400, 422, ...),
 * so every result is judged on `code`, not on the HTTP status.
 */
class DriverApiException(val code: Int, message: String) : RuntimeException("Driver API error $code: $message")

/** Result of a driver action. */
data class ActionResult(val ok: Boolean, val code: Int, val message: String)

/** Result of POST /assign-request (success = at least one assigned and none failed). */
data class AssignResult(val ok: Boolean, val code: Int, val message: String, val successCount: Int, val failedCount: Int)

/** A row of GET /requests (the unassigned self-serve queue). */
data class QueuedRequest(val id: Long, val name: String, val status: Int)

/** Action codes for POST /dispatch-action. */
object DispatchAction {
    const val COMPLETE = 1
    const val CANCEL = 2
}

/**
 * Connection settings, from environment variables only (no endpoint or credential in the repo).
 *
 * | Variable | Meaning |
 * |---|---|
 * | TMS_TRIP_API_URL | Driver API base URL of a NON-production environment, e.g. `https://<host>/driver/api/v3` |
 * | TMS_TRIP_AUTH_VALUE | full Authorization header value for the gateway, `Basic <base64>` (secret) |
 * | TMS_DISPATCHER_PHONE / _ISD / _PASSCODE | a driver account used ONLY for API calls. It must not be the driver signed in on the device: logging the same driver in elsewhere would probably log the app out |
 * | TMS_TRIP_DRIVER_ID | numeric id of the QA driver rides are assigned to (optional: taken from the login token when the QA driver is the API account) |
 * | TMS_DISPATCHER_USE_QA_DRIVER | `true` to use the QA driver's own credentials as the API account |
 */
class DriverApiConfig(
    val baseUrl: String,
    private val gatewayAuthValue: String?,
    val dispatcherPhone: String,
    val dispatcherIsd: String,
    private val dispatcherPasscode: String,
    val assigneeDriverId: Long?,
) {
    init { requireNonProductionHost(baseUrl) }

    internal fun gatewayAuth(): String? = gatewayAuthValue
    internal fun passcode(): String = dispatcherPasscode

    override fun toString() = "DriverApiConfig(baseUrl=$baseUrl, dispatcher=***, gateway=${if (gatewayAuthValue == null) "none" else "***"})"

    companion object {
        /**
         * Only staging / development / local hosts are accepted. Preproduction shares the production database,
         * so it is refused as well. There is deliberately no override.
         */
        fun requireNonProductionHost(url: String) {
            val host = URI.create(url).host?.lowercase() ?: throw IllegalArgumentException("Not a valid URL: $url")
            val allowed = host == "localhost" || host == "127.0.0.1" || host.contains("staging") || host.contains("development")
            require(allowed) {
                "Refusing to use '$host' for the Driver API: only staging, development or local hosts are allowed " +
                    "(preproduction shares the production database)."
            }
        }

        /**
         * Reads the settings. The API account is TMS_DISPATCHER_PHONE / _PASSCODE; when those are not set and
         * TMS_DISPATCHER_USE_QA_DRIVER=true, the QA driver's own credentials are used instead (this may log the
         * app out if both are signed in at once, so do the API calls before the app login).
         */
        /**
         * Settings for the SECOND staging driver ("Automation Driver 2"), from TMS_DRIVER2_PHONE / _PASSCODE / _ISD plus the
         * same host and gateway as the first account. It is used by API calls only and never signed in on the device.
         */
        fun forSecondDriverFromEnvironment(): DriverApiConfig? {
            fun env(name: String) = System.getenv(name)?.takeIf { it.isNotBlank() }
            val base = env("TMS_TRIP_API_URL") ?: return null
            val phone = env("TMS_DRIVER2_PHONE")?.filter { it.isDigit() } ?: return null
            val pass = env("TMS_DRIVER2_PASSCODE") ?: return null
            return DriverApiConfig(base, env("TMS_TRIP_AUTH_VALUE"), phone, (env("TMS_DRIVER2_ISD") ?: "91").filter { it.isDigit() }, pass, null)
        }

        fun fromEnvironment(): DriverApiConfig? {
            fun env(name: String) = System.getenv(name)?.takeIf { it.isNotBlank() }
            val base = env("TMS_TRIP_API_URL") ?: return null
            var phone = env("TMS_DISPATCHER_PHONE")?.filter { it.isDigit() }
            var isd = (env("TMS_DISPATCHER_ISD") ?: "91").filter { it.isDigit() }
            var pass = env("TMS_DISPATCHER_PASSCODE")
            if ((phone == null || pass == null) && env("TMS_DISPATCHER_USE_QA_DRIVER") == "true") {
                val qa = tms.driver.core.config.Credentials.fromEnvironment() ?: return null
                phone = qa.phoneDigits()
                isd = qa.isdCode.filter { it.isDigit() }
                pass = qa.passcode
            }
            if (phone == null || pass == null) return null
            return DriverApiConfig(
                baseUrl = base,
                gatewayAuthValue = env("TMS_TRIP_AUTH_VALUE"),
                dispatcherPhone = phone,
                dispatcherIsd = isd,
                dispatcherPasscode = pass,
                assigneeDriverId = env("TMS_TRIP_DRIVER_ID")?.toLongOrNull(),
            )
        }
    }
}

/** HTTP client for the Driver API, logged in as the dispatcher account. */
class DriverApiClient private constructor(
    private val config: DriverApiConfig,
    private val http: HttpClient,
    private val token: String,
) {
    private class Response(val http: Int, val body: Map<*, *>) {
        val code: Int get() = (body["code"] as? Number)?.toInt() ?: http
        val message: String get() = body["message"]?.toString().orEmpty()
        fun section(name: String): Map<*, *> = body[name] as? Map<*, *> ?: emptyMap<String, Any?>()
    }

    companion object {
        private val json = Json()

        // Device fields the /login call requires; fixed, non-secret automation values.
        private val DEVICE = mapOf(
            "device_id" to "tms-appium-e2e-dispatcher",
            "device_type" to "1",
            "platform" to "1",
            "model" to "Appium-Automation",
            "version" to "1.0",
            "device_token" to "tms-appium-e2e-token",
            "device_mac" to "00:00:00:00:00:00",
            "device_ip_address" to "127.0.0.1",
        )

        fun login(
            config: DriverApiConfig,
            http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
        ): DriverApiClient {
            val body = mapOf(
                "phone_no" to config.dispatcherPhone,
                "isd_code" to config.dispatcherIsd,
                "passcode" to config.passcode(),
            ) + DEVICE
            val res = send(config, http, "POST", "/login", body, config.gatewayAuth())
            val token = res.body["token"]?.toString()
            if (res.http != 200 || res.code != 200 || token.isNullOrBlank()) {
                throw DriverApiException(res.code, res.message.ifBlank { "login failed" })
            }
            return DriverApiClient(config, http, token)
        }

        private fun send(
            config: DriverApiConfig,
            http: HttpClient,
            method: String,
            path: String,
            body: Map<String, Any?>?,
            authorization: String?,
        ): Response {
            val builder = HttpRequest.newBuilder(URI.create(config.baseUrl.trimEnd('/') + path))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .method(method, if (body == null) HttpRequest.BodyPublishers.noBody() else HttpRequest.BodyPublishers.ofString(json.toJson(body)))
            if (body != null) builder.header("Content-Type", "application/json")
            if (authorization != null) builder.header("Authorization", authorization)
            val res = http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
            val map: Map<*, *> = runCatching<Map<*, *>> { json.toType(res.body(), Map::class.java) }
                .getOrElse { emptyMap<String, Any?>() }
            return Response(res.statusCode(), map)
        }
    }

    private fun call(method: String, path: String, body: Map<String, Any?>? = null): Response =
        send(config, http, method, path, body, "Bearer $token")

    private fun action(res: Response) = ActionResult(res.http == 200 && res.code == 200, res.code, res.message)

    /** The logged-in driver's numeric id, read from the token's `result.driver_id` claim (no extra call). */
    fun driverId(): Long {
        val payload = token.split('.').getOrNull(1) ?: throw DriverApiException(500, "token is not a JWT")
        val claims: Map<*, *> = json.toType(String(java.util.Base64.getUrlDecoder().decode(payload.padEnd((payload.length + 3) / 4 * 4, '='))), Map::class.java)
        return ((claims["result"] as? Map<*, *>)?.get("driver_id") as? Number)?.toLong()
            ?: throw DriverApiException(500, "token has no driver_id claim")
    }

    /** GET /requests: the unassigned self-serve queue. */
    fun requests(): List<QueuedRequest> {
        val res = call("GET", "/requests")
        if (res.code != 200) throw DriverApiException(res.code, res.message)
        return rows(res)
    }

    /**
     * The driver's own rides that are still active: GET /ride-requests/2 (assigned) and /3 (in progress).
     * Used by cleanup to find leftovers without knowing their ids. The shape is the same as GET /requests.
     */
    fun myActiveRides(): List<QueuedRequest> = listOf(2, 3).flatMap { type ->
        val res = call("GET", "/ride-requests/$type")
        if (res.code != 200) throw DriverApiException(res.code, "ride-requests/$type: ${res.message}")
        rows(res)
    }

    private fun rows(res: Response): List<QueuedRequest> {
        val list = res.section("response")["allRequests"] as? List<*> ?: return emptyList()
        return list.filterIsInstance<Map<*, *>>().map {
            QueuedRequest(
                id = (it["id"] as? Number)?.toLong() ?: -1,
                name = it["name"]?.toString().orEmpty(),
                status = (it["status"] as? Number)?.toInt() ?: -1,
            )
        }
    }

    fun isInQueue(dispatchId: Long): Boolean = requests().any { it.id == dispatchId }

    /** Polls the queue until a ride for [riderName] appears (there is a short delay after the rider submits). */
    fun waitForRequestByName(riderName: String, timeout: Duration = Duration.ofSeconds(30), interval: Duration = Duration.ofSeconds(2)): QueuedRequest {
        val end = System.nanoTime() + timeout.toNanos()
        while (System.nanoTime() < end) {
            requests().firstOrNull { it.name == riderName }?.let { return it }
            Thread.sleep(interval.toMillis())
        }
        throw DriverApiException(404, "No ride for rider '$riderName' appeared in the queue within ${timeout.seconds}s")
    }

    /** POST /assign-request. Success is successCount >= 1 and failedCount == 0 (the API answers 200 either way). */
    fun assign(dispatchId: Long, driverId: Long): AssignResult {
        val res = call("POST", "/assign-request", mapOf("requests" to listOf(mapOf("dispatch" to dispatchId, "driver" to driverId))))
        val r = res.section("response")
        val ok = (r["successCount"] as? Number)?.toInt() ?: 0
        val failed = (r["failedCount"] as? Number)?.toInt() ?: 0
        return AssignResult(res.http == 200 && res.code == 200 && ok >= 1 && failed == 0, res.code, res.message, ok, failed)
    }

    /**
     * POST /change-driver: moves an already assigned ride to [newDriverId] (what the app's "Assign to another Driver" calls for an
     * assigned ride). Any driver account of the organisation may send it.
     */
    fun changeDriver(dispatchId: Long, newDriverId: Long): ActionResult =
        action(call("POST", "/change-driver", mapOf("dispatch" to dispatchId, "newDriver" to newDriverId)))

    /** POST /dispatch-action: complete (1) or cancel (2). */
    fun dispatchAction(dispatchId: Long, action: Int, reason: String): ActionResult =
        action(call("POST", "/dispatch-action", mapOf("dispatch" to dispatchId, "action" to action, "message" to reason)))

    /**
     * POST /request-action: complete (1) or cancel (2) a ride that is still UNASSIGNED in the queue.
     * Confirmed on staging: /dispatch-action refuses an unassigned ride (400 "Invalid request").
     */
    fun requestAction(requestId: Long, action: Int, reason: String): ActionResult =
        action(call("POST", "/request-action", mapOf("id" to requestId, "action" to action, "undo" to false, "message" to reason)))

    /** Cancels a ride whatever its state: unassigned rides use /request-action, assigned ones /dispatch-action. */
    fun cancelRide(id: Long, reason: String): ActionResult =
        if (isInQueue(id)) requestAction(id, DispatchAction.CANCEL, reason) else dispatchAction(id, DispatchAction.CANCEL, reason)

    /** Best-effort cleanup so a half-finished test never leaves an active ride; errors are swallowed. */
    fun cancelQuietly(dispatchId: Long, reason: String = "E2E teardown") {
        runCatching { cancelRide(dispatchId, reason) }
    }

    /**
     * POST /proceed advances the LOGGED-IN driver's trip. For an on-demand dispatch the first call after
     * assignment starts the trip (there is no dedicated on-demand start endpoint; /start-trip is fixed-route only).
     * It acts on the account this client is logged in as, so it only makes sense for the driver being driven.
     */
    fun proceed(): ActionResult = action(call("POST", "/proceed", emptyMap()))

    /** GET /dashboard -> dispatches.count: how many dispatches the logged-in driver currently has. */
    fun activeDispatchCount(): Int {
        val res = call("GET", "/dashboard")
        if (res.code != 200) throw DriverApiException(res.code, res.message)
        return ((res.section("response")["dispatches"] as? Map<*, *>)?.get("count") as? Number)?.toInt() ?: 0
    }

    /** GET /dashboard -> dispatches.trip_started (0 = not started, 1 = started). */
    fun tripStarted(): Int {
        val res = call("GET", "/dashboard")
        if (res.code != 200) throw DriverApiException(res.code, res.message)
        return ((res.section("response")["dispatches"] as? Map<*, *>)?.get("trip_started") as? Number)?.toInt() ?: 0
    }
}
