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
 * | TMS_TRIP_DRIVER_ID | numeric id of the QA driver that rides are assigned to |
 * | TMS_TRIP_RIDER_NAME | the rider name of a ride that is already waiting in the queue |
 */
class DriverApiConfig(
    val baseUrl: String,
    private val gatewayAuthValue: String?,
    val dispatcherPhone: String,
    val dispatcherIsd: String,
    private val dispatcherPasscode: String,
    val assigneeDriverId: Long?,
    val riderName: String?,
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

        fun fromEnvironment(): DriverApiConfig? {
            fun env(name: String) = System.getenv(name)?.takeIf { it.isNotBlank() }
            val base = env("TMS_TRIP_API_URL") ?: return null
            val phone = env("TMS_DISPATCHER_PHONE") ?: return null
            val pass = env("TMS_DISPATCHER_PASSCODE") ?: return null
            return DriverApiConfig(
                baseUrl = base,
                gatewayAuthValue = env("TMS_TRIP_AUTH_VALUE"),
                dispatcherPhone = phone.filter { it.isDigit() },
                dispatcherIsd = (env("TMS_DISPATCHER_ISD") ?: "91").filter { it.isDigit() },
                dispatcherPasscode = pass,
                assigneeDriverId = env("TMS_TRIP_DRIVER_ID")?.toLongOrNull(),
                riderName = env("TMS_TRIP_RIDER_NAME"),
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

    /** GET /requests: the unassigned self-serve queue. */
    fun requests(): List<QueuedRequest> {
        val res = call("GET", "/requests")
        if (res.code != 200) throw DriverApiException(res.code, res.message)
        val rows = res.section("response")["allRequests"] as? List<*> ?: return emptyList()
        return rows.filterIsInstance<Map<*, *>>().map {
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

    /** POST /dispatch-action: complete (1) or cancel (2). */
    fun dispatchAction(dispatchId: Long, action: Int, reason: String): ActionResult =
        action(call("POST", "/dispatch-action", mapOf("dispatch" to dispatchId, "action" to action, "message" to reason)))

    /** Best-effort cleanup so a half-finished test never leaves an active ride; errors are swallowed. */
    fun cancelQuietly(dispatchId: Long, reason: String = "E2E teardown") {
        runCatching { dispatchAction(dispatchId, DispatchAction.CANCEL, reason) }
    }

    /**
     * POST /proceed advances the LOGGED-IN driver's trip. For an on-demand dispatch the first call after
     * assignment starts the trip (there is no dedicated on-demand start endpoint; /start-trip is fixed-route only).
     * It acts on the account this client is logged in as, so it only makes sense for the driver being driven.
     */
    fun proceed(): ActionResult = action(call("POST", "/proceed", emptyMap()))

    /** GET /dashboard -> dispatches.trip_started (0 = not started, 1 = started). */
    fun tripStarted(): Int {
        val res = call("GET", "/dashboard")
        return ((res.section("response")["dispatches"] as? Map<*, *>)?.get("trip_started") as? Number)?.toInt() ?: 0
    }
}
