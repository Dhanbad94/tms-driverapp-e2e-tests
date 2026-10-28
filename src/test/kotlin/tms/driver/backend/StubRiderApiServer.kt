package tms.driver.backend

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.openqa.selenium.json.Json
import java.net.InetSocketAddress

/**
 * Local stand-in for the Rider API (guest-user + request) with the contract above. A created ride is reported
 * to [onRideCreated], which a test can use to put the ride into the stub Driver API's queue.
 */
class StubRiderApiServer(
    val gatewayAuth: String = "Basic cmlkZXI6c3R1Yg==",
    val orgToken: String = "ODASAP",
    private val onRideCreated: (riderName: String) -> Unit = {},
) : AutoCloseable {
    private val json = Json()
    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val validToken = "stub-guest-jwt"
    val received = java.util.concurrent.CopyOnWriteArrayList<String>()   // raw request bodies
    val cancelled = java.util.concurrent.CopyOnWriteArrayList<String>()  // ride codes the rider cancelled

    val baseUrl: String get() = "http://127.0.0.1:${server.address.port}/rider/api/v1"

    init {
        server.createContext("/rider/api/v1") { ex -> route(ex) }
        server.start()
    }

    private fun route(ex: HttpExchange) {
        try {
            val path = ex.requestURI.path.removePrefix("/rider/api/v1")
            val auth = ex.requestHeaders.getFirst("Authorization")
            val body = ex.requestBody.readBytes().toString(Charsets.UTF_8)
            when (path) {
                "/guest-user" ->
                    if (auth == gatewayAuth) reply(ex, 200, """{"code":200,"response":{"token":"$validToken"}}""")
                    else reply(ex, 200, """{"code":401,"message":"Authentication failed"}""")
                "/request" -> request(ex, auth, body)
                "/cancel-ride" -> cancelRide(ex, auth, body)
                else -> reply(ex, 404, """{"code":404,"message":"not found"}""")
            }
        } catch (e: Exception) {
            reply(ex, 500, """{"code":500,"message":"stub failure"}""")
        }
    }

    private fun request(ex: HttpExchange, auth: String?, body: String) {
        if (auth != "Bearer $validToken") return reply(ex, 200, """{"code":401,"message":"Unauthorized"}""")
        if (ex.requestHeaders.getFirst("token-id") != orgToken) return reply(ex, 200, """{"code":400,"message":"Invalid organization"}""")
        val map: Map<*, *> = runCatching<Map<*, *>> { json.toType(body, Map::class.java) }.getOrElse { emptyMap<String, Any?>() }
        val riders = map["riders"] as? Map<*, *>
        val name = riders?.get("name")?.toString()
        val pickup = (map["pickup_stop"] as? Map<*, *>)?.get("id")
        val dropoff = (map["dropoff_stop"] as? Map<*, *>)?.get("id")
        if (name.isNullOrBlank() || pickup == null || dropoff == null) return reply(ex, 200, """{"code":422,"message":"riders and stops are required"}""")
        if (pickup.toString() == dropoff.toString()) return reply(ex, 200, """{"code":400,"message":"Pickup and drop-off must differ"}""")
        received += body
        onRideCreated(name)
        reply(ex, 200, """{"code":200,"message":"Success","response":{"ride_code":"STUB${received.size}","ride_web_link":"http://example.invalid/j/STUB${received.size}"}}""")
    }

    private fun cancelRide(ex: HttpExchange, auth: String?, body: String) {
        if (auth != "Bearer $validToken") return reply(ex, 200, """{"code":401,"message":"Unauthorized"}""")
        val map: Map<*, *> = runCatching<Map<*, *>> { json.toType(body, Map::class.java) }.getOrElse { emptyMap<String, Any?>() }
        if (map["code"]?.toString().isNullOrBlank() || map["reason"]?.toString().isNullOrBlank()) return reply(ex, 200, """{"code":422,"message":"code and reason are required"}""")
        cancelled += map["code"].toString()
        reply(ex, 200, """{"code":200,"message":"Ride Canceled Successfully!"}""")
    }

    private fun reply(ex: HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray()
        ex.responseHeaders.add("Content-Type", "application/json")
        ex.sendResponseHeaders(status, bytes.size.toLong())
        ex.responseBody.use { it.write(bytes) }
    }

    override fun close() = server.stop(0)
}
