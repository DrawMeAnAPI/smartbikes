package com.pjmbusnel.smartbikes

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.pjmbusnel.smartbikes.io.ItineraryLoader
import com.pjmbusnel.smartbikes.model.*
import com.pjmbusnel.smartbikes.simulator.BikeActor
import com.pjmbusnel.smartbikes.simulator.MovementEngine
import com.pjmbusnel.smartbikes.utils.PhysicsUtils
import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import java.io.File

/**
 * TEMPORARY: captures the Kotlin implementation's deterministic behavior into
 * src/test/resources/golden/ so the Java port can be verified against it.
 */
class GoldenCapture {

    private val mapper = jacksonObjectMapper()
    private val pretty = jacksonObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
    private val outDir = File("src/test/resources/golden").apply { mkdirs() }
    private val loader = ItineraryLoader()

    private fun write(name: String, value: Any) = File(outDir, name).writeText(pretty.writeValueAsString(value) + "\n")

    @Test
    fun capture() {
        captureRoutes()
        captureParsed()
        capturePhysics()
        captureTelemetry()
        captureErrors()
        captureBikeActor()
    }

    private fun captureRoutes() {
        val library = loader.loadTrips()
        val fleet = loader.loadFleetConfig()
        val entries = fleet.map { config ->
            val trip = library.trips.first { it.id == config.tripId }
            val route = loader.assembleRoute(trip, library.segments, tripReverse = config.reverse)
            mapOf(
                "fleetId" to config.id,
                "vehicleId" to (if (config.reverse) "${config.id}-R" else config.id),
                "vehicleType" to VehicleType.valueOf(config.type.uppercase()).name,
                "json" to mapper.writeValueAsString(route),
                "oscillatedJson" to mapper.writeValueAsString(route.copy(points = route.points.reversed()))
            )
        }
        // Every trip in both directions, not just the ones used by the fleet
        val allTrips = library.trips.flatMap { trip ->
            listOf(false, true).map { rev ->
                mapOf(
                    "tripId" to trip.id,
                    "reverse" to rev,
                    "json" to mapper.writeValueAsString(loader.assembleRoute(trip, library.segments, tripReverse = rev))
                )
            }
        }
        write("routes.json", mapOf("fleet" to entries, "allTrips" to allTrips))
    }

    private fun captureParsed() {
        val inputs = linkedMapOf(
            "library" to """{"segments":[{"id":"s1","points":[{"lat":1.5,"lng":2.5},{"lat":3.0,"lng":4.0,"name":"P"}],"extra":"ignored"}],"trips":[{"id":"t1","name":"T","directedSegments":[{"segmentId":"s1"},{"segmentId":"s1","reversed":true}]}]}""",
            "fleet" to """[{"id":"A","type":"MOTORBIKE","tripId":"t1"},{"id":"B","type":"e_motorbike","tripId":"t1","reverse":true,"oscillate":false}]"""
        )
        val out = linkedMapOf<String, Any>(
            "libraryInput" to inputs["library"]!!,
            "libraryJson" to mapper.writeValueAsString(mapper.readValue<ItineraryLibrary>(inputs["library"]!!)),
            "fleetInput" to inputs["fleet"]!!,
            "fleetJson" to mapper.writeValueAsString(mapper.readValue<List<BikeTripConfig>>(inputs["fleet"]!!)),
            "realLibraryJson" to mapper.writeValueAsString(loader.loadTrips()),
            "realFleetJson" to mapper.writeValueAsString(loader.loadFleetConfig())
        )
        write("parsed.json", out)
    }

    private fun capturePhysics() {
        val coords = listOf(
            Coordinate(18.7875579, 98.9626137), Coordinate(18.8012747, 98.9678656),
            Coordinate(0.0, 0.0), Coordinate(0.0, 179.9), Coordinate(0.0, -179.9),
            Coordinate(90.0, 0.0), Coordinate(-45.5, 120.25), Coordinate(18.0, 98.0), Coordinate(18.0, 98.0)
        )
        val fractions = listOf(0.0, 0.25, 0.5, 0.999, 1.0, 1.5, -0.5)
        val distances = mutableListOf<Map<String, Any>>()
        val interpolations = mutableListOf<Map<String, Any>>()
        for (a in coords) for (b in coords) {
            distances += mapOf("a" to a, "b" to b, "distance" to PhysicsUtils.calculateDistance(a, b))
            for (f in fractions) {
                interpolations += mapOf("a" to a, "b" to b, "fraction" to f, "result" to PhysicsUtils.interpolate(a, b, f))
            }
        }
        write("physics.json", mapOf("distances" to distances, "interpolations" to interpolations))
    }

    @OptIn(kotlin.time.ExperimentalTime::class)
    private fun captureTelemetry() {
        val withBattery = Telemetry("EB-001", "E_MOTORBIKE", 18.7875579, 98.9626137, 61.23456789, 88.5, "2026-01-01T00:00:00Z")
        val withoutBattery = Telemetry("MB-002-R", "MOTORBIKE", -0.0, 1.0E-7, 75.0, null, "2026-01-01T00:00:00.123456Z")
        val sessionCount = mapOf("type" to "SESSION_COUNT", "count" to 3)
        val instants = listOf(0, 1_000_000, 120_000_000, 123_456_000, 123_456_789, 1_000).map { nanos ->
            mapOf(
                "epochSeconds" to 1_767_225_600L,
                "nanos" to nanos,
                "string" to kotlin.time.Instant.fromEpochSeconds(1_767_225_600L, nanos).toString()
            )
        }
        write("telemetry.json", mapOf(
            "withBattery" to mapper.writeValueAsString(withBattery),
            "withoutBattery" to mapper.writeValueAsString(withoutBattery),
            "sessionCount" to mapper.writeValueAsString(sessionCount),
            "instantToString" to instants
        ))
    }

    private fun outcome(case: String, block: () -> Unit): Map<String, Any?> = try {
        block()
        mapOf("case" to case, "throws" to false)
    } catch (e: Exception) {
        val isJackson = e is com.fasterxml.jackson.core.JacksonException
        mapOf(
            "case" to case,
            "throws" to true,
            "jackson" to isJackson,
            // Jackson's own messages are implementation-specific; only record ours and the JDK's
            "type" to if (isJackson) null else e.javaClass.name,
            "message" to if (isJackson) null else e.message
        )
    }

    private fun captureErrors() {
        val seg = Segment("s1", null, listOf(Coordinate(1.0, 1.0), Coordinate(2.0, 2.0)))
        val lib = ItineraryLibrary(listOf(seg), listOf(
            TripDefinition("t1", "ok", listOf(DirectedSegment("s1"))),
            TripDefinition("t2", "bad", listOf(DirectedSegment("s1"), DirectedSegment("ghost")))
        ))
        val results = listOf(
            outcome("integrityOk") { loader.validateIntegrity(lib, listOf(BikeTripConfig("A", "MOTORBIKE", "t1"))) },
            outcome("integrityUnknownTrip") { loader.validateIntegrity(lib, listOf(BikeTripConfig("A", "MOTORBIKE", "nope"))) },
            outcome("integrityUnknownSegment") { loader.validateIntegrity(lib, listOf(BikeTripConfig("B", "MOTORBIKE", "t2"))) },
            outcome("engineEmptyRoute") { MovementEngine(Route("r", "r", emptyList()), 60.0) },
            outcome("vehicleTypeUnknown") { VehicleType.valueOf("SCOOTER".uppercase()) },
            outcome("vehicleTypeLowercase") { VehicleType.valueOf("motorbike".uppercase()) },
            outcome("jsonFleetMissingTripId") { mapper.readValue<List<BikeTripConfig>>("""[{"id":"A","type":"MOTORBIKE"}]""") },
            outcome("jsonFleetUnknownField") { mapper.readValue<List<BikeTripConfig>>("""[{"id":"A","type":"MOTORBIKE","tripId":"t","color":"red"}]""") },
            outcome("jsonCoordinateMissingLat") { mapper.readValue<Coordinate>("""{"lng":1.0}""") },
            outcome("jsonCoordinateUnknownField") { mapper.readValue<Coordinate>("""{"lat":1.0,"lng":1.0,"alt":3}""") },
            outcome("jsonSegmentUnknownField") { mapper.readValue<Segment>("""{"id":"s","points":[],"speedLimit":50}""") },
            outcome("jsonSegmentMissingPoints") { mapper.readValue<Segment>("""{"id":"s"}""") },
            outcome("jsonTripMissingSegments") { mapper.readValue<TripDefinition>("""{"id":"t","name":"n"}""") },
            outcome("jsonDirectedSegmentMissingId") { mapper.readValue<DirectedSegment>("""{"reversed":true}""") },
            outcome("jsonMalformed") { mapper.readValue<List<BikeTripConfig>>("""[{"id":""") }
        )
        write("errors.json", results)
    }

    private fun captureBikeActor() {
        val route = Route("r", "Long", listOf(Coordinate(18.0, 98.0), Coordinate(19.0, 98.0)))
        val actor = BikeActor("BA-1", route, speedKph = 25.0, tickRateMs = 20)
        val finishedAfterStop = runBlocking {
            val job = launch(Dispatchers.Default) { actor.startSimulation() }
            delay(100)
            actor.stop()
            val done = withTimeoutOrNull(1000) { job.join() } != null
            job.cancelAndJoin()
            done
        }
        File(outDir, "bikeactor.txt").writeText("stopEndsLoop=$finishedAfterStop\n")
    }
}
