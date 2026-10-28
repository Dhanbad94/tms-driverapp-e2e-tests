package tms.driver.backend

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.openqa.selenium.json.Json
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * Local stand-in for the Driver API, mirroring the contract used by the Playwright suite:
 * Basic gateway auth on /login, Bearer token afterwards, and failures reported in the body `code`
 * while the HTTP status stays 200. It proves the client, not the real backend or the app.
 */
class StubDriverApiServer(
    val gatewayAuth: String = "Basic c3R1Yjpwd2Q=",
    val goodPasscode: String = "4321",
) : AutoCloseable {
    private val json = Json()
    private val queued = ConcurrentHashMap<Long, String>()      // dispatch id -> rider name (unassigned)
    private val assigned = ConcurrentHashMap<Long, Long>()      // dispatch id -> driver id
    private val assignedNames = ConcurrentHashMap<Long, String>()
    private val finished = ConcurrentHashMap<Long, String>()    // dispatch id -> "completed" | "canceled"
    @Volatile private var tripStarted = 0
    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

    val baseUrl: String get() = "http://127.0.0.1:${server.address.port}/driver/api/v3"
    /** A JWT-shaped token whose payload carries result.driver_id = 77, like the real API's. */
    val validToken = "eyJhbGciOiAiSFMyNTYiLCAidHlwIjogIkpXVCJ9.eyJyZXN1bHQiOiB7ImRyaXZlcl9pZCI6IDc3fX0.stub-signature"

    @Volatile var refuseCancel = false
    @Volatile private var pickedUp = false
    /** Simulates the rider having been picked up in the app: the ride can no longer be cancelled, only finished with /proceed. */
    fun markPickedUp() { pickedUp = true }
    fun enqueueRide(id: Long, riderName: String) { queued[id] = riderName }
    fun assignedCount() = assigned.size
    fun queuedCount() = queued.size
    fun tripStartedFlag() = tripStarted

    init {
        server.createContext("/driver/api/v3") { ex -> route(ex) }
        server.start()
    }

    private fun route(ex: HttpExchange) {
        try {
            val path = ex.requestURI.path.removePrefix("/driver/api/v3")
            val auth = ex.requestHeaders.getFirst("Authorization")
            if (path == "/login") return login(ex, auth)
            if (auth != "Bearer $validToken") return reply(ex, """{"code":401,"message":"Unauthorized"}""")
            val body = ex.requestBody.readBytes().toString(Charsets.UTF_8)
            val map: Map<*, *> = runCatching<Map<*, *>> { json.toType(body, Map::class.java) }.getOrElse { emptyMap<String, Any?>() }
            when (path) {
                "/requests" -> requests(ex)
                "/ride-requests/2" -> myRides(ex)
                "/ride-requests/3" -> inProgress(ex)
                "/assign-request" -> assign(ex, map)
                "/dispatch-action" -> dispatchAction(ex, map)
                "/request-action" -> requestAction(ex, map)
                "/proceed" -> proceed(ex)
                "/dashboard" -> reply(ex, """{"code":200,"response":{"dispatches":{"count":${assigned.size},"trip_started":$tripStarted}}}""")
                else -> reply(ex, """{"code":404,"message":"not found"}""")
            }
        } catch (e: Exception) {
            reply(ex, """{"code":500,"message":"stub failure"}""")
        }
    }

    private fun login(ex: HttpExchange, auth: String?) {
        if (auth == null) return reply(ex, """{"code":401,"message":"Authorization header missing"}""")
        if (auth != gatewayAuth) return reply(ex, """{"code":401,"message":"Authentication failed"}""")
        val map: Map<*, *> = runCatching<Map<*, *>> { json.toType(ex.requestBody.readBytes().toString(Charsets.UTF_8), Map::class.java) }
            .getOrElse { emptyMap<String, Any?>() }
        if (map["passcode"] == null) return reply(ex, """{"code":422,"message":"The passcode field is required."}""")
        if (map["passcode"].toString() != goodPasscode) return reply(ex, """{"code":400,"message":"Invalid passcode"}""")
        reply(ex, """{"code":200,"token":"$validToken","message":"ok"}""")
    }

    private fun requests(ex: HttpExchange) {
        val rows = queued.entries.joinToString(",") { """{"id":${it.key},"name":"${it.value}","status":1}""" }
        reply(ex, """{"code":200,"response":{"summary":{"count":${queued.size}},"allRequests":[$rows]}}""")
    }

    private fun assign(ex: HttpExchange, map: Map<*, *>) {
        val first = (map["requests"] as? List<*>)?.firstOrNull() as? Map<*, *>
        val id = (first?.get("dispatch") as? Number)?.toLong()
        val driver = (first?.get("driver") as? Number)?.toLong()
        val rideName = id?.let { queued[it] }
        if (id == null || driver == null || queued.remove(id) == null) {
            return reply(ex, """{"code":400,"message":"Invalid request","response":{"successCount":0,"failedCount":1}}""")
        }
        assigned[id] = driver
        assignedNames[id] = rideName.orEmpty()
        reply(ex, """{"code":200,"message":"ok","response":{"successCount":1,"failedCount":0}}""")
    }

    private fun myRides(ex: HttpExchange) {
        val rows = if (pickedUp) "" else assigned.keys.joinToString(",") { """{"id":$it,"name":"${assignedNames[it].orEmpty()}","status":2}""" }
        reply(ex, """{"code":200,"response":{"allRequests":[$rows]}}""")
    }

    private fun inProgress(ex: HttpExchange) {
        val rows = if (!pickedUp) "" else assigned.keys.joinToString(",") { """{"id":$it,"name":null,"status":4}""" }
        reply(ex, """{"code":200,"response":{"allRequests":[$rows]}}""")
    }

    private fun dispatchAction(ex: HttpExchange, map: Map<*, *>) {
        if (refuseCancel || pickedUp) return reply(ex, """{"code":400,"message":"Invalid request"}""")
        val id = (map["dispatch"] as? Number)?.toLong() ?: return reply(ex, """{"code":422,"message":"The dispatch field is required."}""")
        val action = (map["action"] as? Number)?.toInt()
        // Like the real API: /dispatch-action only works on an ASSIGNED ride.
        if (!assigned.containsKey(id)) return reply(ex, """{"code":400,"message":"Invalid request"}""")
        queued.remove(id); assigned.remove(id)
        val word = if (action == DispatchAction.COMPLETE) "completed" else "canceled"
        finished[id] = word
        reply(ex, """{"code":200,"message":"Dispatch $word successfully"}""")
    }

    private fun requestAction(ex: HttpExchange, map: Map<*, *>) {
        if (refuseCancel) return reply(ex, """{"code":400,"message":"Invalid request"}""")
        val id = (map["id"] as? Number)?.toLong() ?: return reply(ex, """{"code":422,"message":"The id field is required."}""")
        if (queued.remove(id) == null) return reply(ex, """{"code":400,"message":"Invalid request"}""")
        reply(ex, """{"code":200,"message":"Data updated"}""")
    }

    private fun proceed(ex: HttpExchange) {
        if (assigned.isEmpty()) return reply(ex, """{"code":400,"message":"No active trip"}""")
        if (pickedUp) { assigned.clear(); pickedUp = false; return reply(ex, """{"code":200,"message":"Route Updated"}""") }
        return if (tripStarted == 0) { tripStarted = 1; reply(ex, """{"code":200,"message":"Trip started"}""") }
        else reply(ex, """{"code":200,"message":"Trip Ended"}""")
    }

    private fun reply(ex: HttpExchange, body: String) {
        val bytes = body.toByteArray()
        ex.responseHeaders.add("Content-Type", "application/json")
        ex.sendResponseHeaders(200, bytes.size.toLong())   // like the real API: HTTP 200, result in body.code
        ex.responseBody.use { it.write(bytes) }
    }

    override fun close() = server.stop(0)
}
