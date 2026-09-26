package dev.chungjungsoo.gptmobile.data.agent.tool

import dev.chungjungsoo.gptmobile.data.agent.AgentTool
import dev.chungjungsoo.gptmobile.data.agent.AgentToolDefinition
import dev.chungjungsoo.gptmobile.data.agent.AgentToolResult
import dev.chungjungsoo.gptmobile.data.agent.ToolResultContent
import dev.chungjungsoo.gptmobile.data.database.entity.BuiltInAgentTool
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Singleton
class DeviceLocationTool @Inject constructor(
    private val locationProvider: DeviceLocationProvider,
    private val placesClient: NearbyPlacesClient
) : AgentTool {

    override val definition: AgentToolDefinition = AgentToolDefinition(
        name = BuiltInAgentTool.DEVICE_LOCATION,
        description = "Get current location / GPS coordinates from the Android phone (get_current_location capability). Use this for where-am-I requests, not timezone or memory inference. Returns latitude, longitude, accuracy and altitude when Android permission and location services allow it. For nearby requests also set nearby to the requested place category; optionally supply place_name and radius_meters. This searches OpenStreetMap online and returns places for map markers.",
        inputSchema = buildJsonObject {
            put("type", "object")
            put(
                "properties",
                buildJsonObject {
                    put(
                        "nearby",
                        buildJsonObject {
                            put("type", "string")
                            put("description", "Place category for a nearby search. Omit for location only.")
                            put("enum", JsonArray(NearbyCategory.entries.map { JsonPrimitive(it.wireName) }))
                        }
                    )
                    put(
                        "place_name",
                        buildJsonObject {
                            put("type", "string")
                            put("description", "Optional store or place name to filter matches")
                            put("maxLength", 80)
                        }
                    )
                    put(
                        "radius_meters",
                        buildJsonObject {
                            put("type", "integer")
                            put("minimum", 100)
                            put("maximum", 5000)
                            put("default", 1500)
                        }
                    )
                }
            )
            put("additionalProperties", false)
        }
    )

    override suspend fun execute(callId: String, arguments: JsonObject): AgentToolResult {
        if (!locationProvider.hasPermission()) {
            return AgentToolResult(
                callId = callId,
                content = ToolResultContent.Text("Android location permission is not granted. Enable Device location in this AI profile and grant the app location permission in Android settings. No separate MCP server is required."),
                isError = true
            )
        }

        val location = locationProvider.getCurrentLocation()
            ?: return AgentToolResult(
                callId = callId,
                content = ToolResultContent.Text("No recent device location fix is available after requesting Android location. Turn on Location in Android settings, keep this app open while retrying, and allow Precise location for better accuracy. A cold GPS fix may need up to 30 seconds and a clearer view of the sky. Opening Google Maps is not required. Do not substitute a timezone or remembered address for a current location."),
                isError = true
            )

        val categoryName = arguments["nearby"]?.jsonPrimitive?.contentOrNull
        val category = NearbyCategory.entries.firstOrNull { it.wireName == categoryName }
        var placesError: String? = null
        val places = if (category != null) {
            try {
                placesClient.nearby(
                    MapCoordinate(location.latitude, location.longitude),
                    category,
                    arguments["radius_meters"]?.jsonPrimitive?.intOrNull ?: 1500,
                    arguments["place_name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                placesError = "Nearby places could not be loaded. Location is available; retry places later or use web search."
                emptyList()
            }
        } else {
            emptyList()
        }

        val responseJson = buildJsonObject {
            put("latitude", location.latitude)
            put("longitude", location.longitude)
            location.accuracy?.let { put("accuracy_meters", it.toDouble()) }
            location.altitude?.let { put("altitude_meters", it) }
            if (categoryName != null) {
                put("nearby_category", categoryName)
                put("places", Json.parseToJsonElement(Json.encodeToString(places)))
                put("places_source", "© OpenStreetMap contributors · Overpass; closest returned matches by straight-line distance, coverage may be incomplete")
                put("places_status", if (category == null) "Unsupported nearby category" else placesError ?: if (places.isEmpty()) "No matching places returned" else "Found ${places.size} nearby places")
            }
            put("timestamp", location.timestamp)
            location.provider?.let { put("provider", it) }
        }

        return AgentToolResult(
            callId = callId,
            content = ToolResultContent.Json(responseJson),
            isError = false
        )
    }
}
