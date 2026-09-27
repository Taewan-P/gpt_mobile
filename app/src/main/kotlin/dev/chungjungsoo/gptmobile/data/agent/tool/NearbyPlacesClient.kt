package dev.chungjungsoo.gptmobile.data.agent.tool

import dev.chungjungsoo.gptmobile.BuildConfig
import dev.chungjungsoo.gptmobile.data.network.NetworkClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.utils.io.readAvailable
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class MapCoordinate(val latitude: Double, val longitude: Double) {
    val isValid: Boolean get() = latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0
}

@Serializable
data class NearbyPlace(val id: String, val name: String, val latitude: Double, val longitude: Double, val distanceMeters: Double)

data class PlaceRoute(val coordinates: List<MapCoordinate>, val distanceMeters: Double, val durationSeconds: Double)

enum class NearbyCategory(val wireName: String, val selector: String) {
    STORES("stores", "[shop]"),
    GROCERIES("groceries", "[shop~\"^(supermarket|convenience|greengrocer)$\"]"),
    RESTAURANTS("restaurants", "[amenity~\"^(restaurant|fast_food)$\"]"),
    CAFES("cafes", "[amenity=cafe]"),
    PHARMACIES("pharmacies", "[amenity=pharmacy]"),
    PARKS("parks", "[leisure=park]"),
    HOSPITALS("hospitals", "[amenity=hospital]"),
    HOTELS("hotels", "[tourism=hotel]"),
    BANKS("banks", "[amenity~\"^(bank|atm)$\"]"),
    TRANSIT("transit", "[public_transport=platform]")
}

/** User-requested OSM lookups only. Bounded responses, small memory cache and one request/second. */
@Singleton
class NearbyPlacesClient @Inject constructor(private val network: NetworkClient) {
    private val mutex = Mutex()
    private var nextRequestAt = 0L
    private val unavailableHosts = mutableMapOf<String, Long>()
    private val cache = linkedMapOf<String, Pair<Long, JsonObject>>()

    suspend fun nearby(origin: MapCoordinate, category: NearbyCategory, radiusMeters: Int, name: String = ""): List<NearbyPlace> {
        require(origin.isValid)
        val radius = radiusMeters.coerceIn(100, 5000)
        val query = "[out:json][timeout:18][maxsize:4194304];nwr(around:$radius,${origin.latitude},${origin.longitude})${category.selector};out center tags 500;"
        val result = request(query, "overpass-api.de") {
            network().preparePost("https://overpass-api.de/api/interpreter") {
                contentType(ContentType.Text.Plain)
                header("User-Agent", USER_AGENT)
                setBody(query)
                timeout {
                    requestTimeoutMillis = 22_000
                    connectTimeoutMillis = 5_000
                    socketTimeoutMillis = 20_000
                }
            }.execute { it.boundedJson() }
        }
        check(result["remark"] == null) { "Nearby places search was incomplete. Try a smaller radius." }
        return parseNearbyPlaces(result, origin, name).filter { it.distanceMeters <= radius }.take(12)
    }

    suspend fun route(origin: MapCoordinate, destination: MapCoordinate, walking: Boolean): PlaceRoute {
        require(origin.isValid && destination.isValid)
        val service = if (walking) "routed-foot" else "routed-car"
        val coords = "${origin.longitude},${origin.latitude};${destination.longitude},${destination.latitude}"
        val url = "https://routing.openstreetmap.de/$service/route/v1/driving/$coords?overview=full&geometries=geojson&steps=false"
        val result = request(url, "routing.openstreetmap.de") {
            network().prepareGet(url) {
                header("User-Agent", USER_AGENT)
                timeout {
                    requestTimeoutMillis = 15_000
                    connectTimeoutMillis = 5_000
                    socketTimeoutMillis = 12_000
                }
            }.execute { it.boundedJson() }
        }
        return parsePlaceRoute(result) ?: error("No ${if (walking) "walking" else "driving"} route available")
    }

    private suspend fun request(key: String, host: String, fetch: suspend () -> JsonObject): JsonObject = mutex.withLock {
        val now = System.nanoTime() / 1_000_000
        cache[key]?.takeIf { now - it.first < 300_000 }?.let { return@withLock it.second }
        check((unavailableHosts[host] ?: 0L) <= now) { "Map service is temporarily unreachable. Check your connection and retry in a minute." }
        delay((nextRequestAt - now).coerceAtLeast(0))
        nextRequestAt = System.nanoTime() / 1_000_000 + 1100
        val response = try {
            fetch()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val dnsFailure = generateSequence<Throwable>(error) { it.cause }.take(12).any { it is java.net.UnknownHostException }
            if (dnsFailure) {
                unavailableHosts[host] = System.nanoTime() / 1_000_000 + 60_000
                throw IllegalStateException("Map service address could not be resolved. Check your connection and retry in a minute.", error)
            }
            throw error
        }
        unavailableHosts.remove(host)
        cache[key] = System.nanoTime() / 1_000_000 to response
        while (cache.size > 32) cache.remove(cache.keys.first())
        response
    }

    private suspend fun HttpResponse.boundedJson(): JsonObject {
        check(status.value in 200..299) { "Map service is unavailable (HTTP ${status.value})" }
        val channel = bodyAsChannel()
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = channel.readAvailable(buffer, 0, buffer.size)
            if (count < 0) break
            if (count == 0) {
                yield()
                continue
            }
            check(output.size() + count <= 1_048_576) { "Map service response is too large" }
            output.write(buffer, 0, count)
        }
        return Json.parseToJsonElement(output.toString("UTF-8")) as? JsonObject ?: error("Invalid map service response")
    }

    companion object {
        private val USER_AGENT = "GPTMobile/${BuildConfig.VERSION_NAME} (https://github.com/tailscale-signin/GPT_Mobile_AI-improved)"
    }
}

internal fun distanceMeters(from: MapCoordinate, to: MapCoordinate): Double {
    val dLat = Math.toRadians(to.latitude - from.latitude)
    val dLon = Math.toRadians(to.longitude - from.longitude)
    val a = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(from.latitude)) * cos(Math.toRadians(to.latitude)) * sin(dLon / 2) * sin(dLon / 2)
    return 6_371_000 * 2 * asin(sqrt(a.coerceIn(0.0, 1.0)))
}

internal fun parseNearbyPlaces(json: JsonObject, origin: MapCoordinate, name: String = ""): List<NearbyPlace> =
    (json["elements"] as? JsonArray).orEmpty().mapNotNull { raw ->
        val item = raw as? JsonObject ?: return@mapNotNull null
        val tags = item["tags"] as? JsonObject ?: return@mapNotNull null
        val center = item["center"] as? JsonObject ?: item
        val lat = center["lat"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
        val lon = center["lon"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
        val point = MapCoordinate(lat, lon).takeIf { it.isValid } ?: return@mapNotNull null
        val title = (tags["name"] ?: tags["brand"] ?: tags["shop"] ?: tags["amenity"] ?: tags["leisure"])?.jsonPrimitive?.contentOrNull?.take(120) ?: return@mapNotNull null
        if (name.isNotBlank() && !title.contains(name.take(80), ignoreCase = true)) return@mapNotNull null
        NearbyPlace("${item["type"]?.jsonPrimitive?.contentOrNull}/${item["id"]?.jsonPrimitive?.contentOrNull}", title, lat, lon, distanceMeters(origin, point))
    }.distinctBy { it.id }.sortedBy { it.distanceMeters }

internal fun parsePlaceRoute(json: JsonObject): PlaceRoute? {
    if (json["code"]?.jsonPrimitive?.contentOrNull != "Ok") return null
    val route = (json["routes"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
    val distance = route["distance"]?.jsonPrimitive?.doubleOrNull?.takeIf { it.isFinite() && it >= 0 } ?: return null
    val duration = route["duration"]?.jsonPrimitive?.doubleOrNull?.takeIf { it.isFinite() && it >= 0 } ?: return null
    val geometry = route["geometry"] as? JsonObject ?: return null
    val coordinates = (geometry["coordinates"] as? JsonArray)?.takeIf { it.size <= 20_000 }?.map { raw ->
        val pair = raw as? JsonArray ?: return null
        val lon = pair.getOrNull(0)?.jsonPrimitive?.doubleOrNull ?: return null
        val lat = pair.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: return null
        MapCoordinate(lat, lon).takeIf { it.isValid } ?: return null
    }?.takeIf { it.size >= 2 } ?: return null
    return PlaceRoute(coordinates, distance, duration)
}
