package com.pjmbusnel.smartbikes;

import com.pjmbusnel.smartbikes.io.ItineraryLoader;
import com.pjmbusnel.smartbikes.io.Json;
import com.pjmbusnel.smartbikes.model.BikeTripConfig;
import com.pjmbusnel.smartbikes.model.Coordinate;
import com.pjmbusnel.smartbikes.model.DirectedSegment;
import com.pjmbusnel.smartbikes.model.ItineraryLibrary;
import com.pjmbusnel.smartbikes.model.Route;
import com.pjmbusnel.smartbikes.model.Segment;
import com.pjmbusnel.smartbikes.model.Telemetry;
import com.pjmbusnel.smartbikes.model.TripDefinition;
import com.pjmbusnel.smartbikes.model.VehicleType;
import com.pjmbusnel.smartbikes.simulator.BikeActor;
import com.pjmbusnel.smartbikes.simulator.MovementEngine;
import com.pjmbusnel.smartbikes.utils.PhysicsUtils;
import com.pjmbusnel.smartbikes.web.FleetWebSocketHandler;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Verifies the Java implementation against outputs captured from the original Kotlin implementation
 * (src/test/resources/golden/). Golden files must only change on purpose.
 */
class GoldenMasterTest {

    private static final JsonMapper GOLDEN = JsonMapper.builder().build();
    private static final TypeReference<List<BikeTripConfig>> FLEET = new TypeReference<>() {
    };

    private final ItineraryLoader loader = new ItineraryLoader();

    private static JsonNode golden(String name) throws IOException {
        try (InputStream in = GoldenMasterTest.class.getResourceAsStream("/golden/" + name)) {
            return GOLDEN.readTree(in);
        }
    }

    @Test
    void routesMatchKotlin() throws IOException {
        JsonNode golden = golden("routes.json");
        ItineraryLibrary library = loader.loadTrips();
        List<BikeTripConfig> fleet = loader.loadFleetConfig();

        JsonNode goldenFleet = golden.get("fleet");
        assertEquals(goldenFleet.size(), fleet.size());
        for (int i = 0; i < fleet.size(); i++) {
            BikeTripConfig config = fleet.get(i);
            JsonNode expected = goldenFleet.get(i);
            TripDefinition trip = library.trips().stream().filter(t -> t.id().equals(config.tripId())).findFirst().orElseThrow();
            Route route = loader.assembleRoute(trip, library.segments(), config.reverse());

            assertEquals(expected.get("fleetId").asString(), config.id());
            assertEquals(expected.get("vehicleId").asString(), config.reverse() ? config.id() + "-R" : config.id());
            assertEquals(expected.get("vehicleType").asString(), VehicleType.valueOf(config.type().toUpperCase(Locale.ROOT)).name());
            assertEquals(expected.get("json").asString(), Json.MAPPER.writeValueAsString(route), config.id());
            assertEquals(expected.get("oscillatedJson").asString(),
                    Json.MAPPER.writeValueAsString(route.withPoints(List.copyOf(route.points().reversed()))), config.id());
        }

        JsonNode goldenTrips = golden.get("allTrips");
        assertEquals(goldenTrips.size(), library.trips().size() * 2);
        int i = 0;
        for (TripDefinition trip : library.trips()) {
            for (boolean reverse : new boolean[]{false, true}) {
                JsonNode expected = goldenTrips.get(i++);
                assertEquals(expected.get("tripId").asString(), trip.id());
                assertEquals(expected.get("reverse").asBoolean(), reverse);
                assertEquals(expected.get("json").asString(),
                        Json.MAPPER.writeValueAsString(loader.assembleRoute(trip, library.segments(), reverse)));
            }
        }
    }

    @Test
    void parsingAndDefaultsMatchKotlin() throws IOException {
        JsonNode golden = golden("parsed.json");
        ItineraryLibrary library = Json.MAPPER.readValue(golden.get("libraryInput").asString(), ItineraryLibrary.class);
        assertEquals(golden.get("libraryJson").asString(), Json.MAPPER.writeValueAsString(library));

        List<BikeTripConfig> fleet = Json.MAPPER.readValue(golden.get("fleetInput").asString(), FLEET);
        assertEquals(golden.get("fleetJson").asString(), Json.MAPPER.writeValueAsString(fleet));

        assertEquals(golden.get("realLibraryJson").asString(), Json.MAPPER.writeValueAsString(loader.loadTrips()));
        assertEquals(golden.get("realFleetJson").asString(), Json.MAPPER.writeValueAsString(loader.loadFleetConfig()));
    }

    @Test
    void physicsMatchesKotlinBitForBit() throws IOException {
        JsonNode golden = golden("physics.json");
        for (JsonNode d : golden.get("distances")) {
            Coordinate a = Json.MAPPER.treeToValue(d.get("a"), Coordinate.class);
            Coordinate b = Json.MAPPER.treeToValue(d.get("b"), Coordinate.class);
            assertEquals(d.get("distance").doubleValue(), PhysicsUtils.calculateDistance(a, b), () -> a + " -> " + b);
        }
        for (JsonNode n : golden.get("interpolations")) {
            Coordinate a = Json.MAPPER.treeToValue(n.get("a"), Coordinate.class);
            Coordinate b = Json.MAPPER.treeToValue(n.get("b"), Coordinate.class);
            double fraction = n.get("fraction").doubleValue();
            Coordinate expected = Json.MAPPER.treeToValue(n.get("result"), Coordinate.class);
            assertEquals(expected, PhysicsUtils.interpolate(a, b, fraction), () -> a + " -> " + b + " @ " + fraction);
        }
    }

    @Test
    void wireFormatMatchesKotlin() throws IOException {
        JsonNode golden = golden("telemetry.json");
        Telemetry withBattery = new Telemetry("EB-001", "E_MOTORBIKE", 18.7875579, 98.9626137, 61.23456789, 88.5, "2026-01-01T00:00:00Z");
        Telemetry withoutBattery = new Telemetry("MB-002-R", "MOTORBIKE", -0.0, 1.0E-7, 75.0, null, "2026-01-01T00:00:00.123456Z");

        assertEquals(golden.get("withBattery").asString(), Json.MAPPER.writeValueAsString(withBattery));
        assertEquals(golden.get("withoutBattery").asString(), Json.MAPPER.writeValueAsString(withoutBattery));
        assertEquals(golden.get("sessionCount").asString(), FleetWebSocketHandler.sessionCountJson(3));

        for (JsonNode instant : golden.get("instantToString")) {
            Instant javaInstant = Instant.ofEpochSecond(instant.get("epochSeconds").longValue(), instant.get("nanos").intValue());
            assertEquals(instant.get("string").asString(), javaInstant.toString());
        }
    }

    @Test
    void errorsMatchKotlin() throws IOException {
        Segment seg = new Segment("s1", List.of(new Coordinate(1.0, 1.0), new Coordinate(2.0, 2.0)));
        ItineraryLibrary lib = new ItineraryLibrary(List.of(seg), List.of(
                new TripDefinition("t1", "ok", List.of(new DirectedSegment("s1"))),
                new TripDefinition("t2", "bad", List.of(new DirectedSegment("s1"), new DirectedSegment("ghost")))
        ));

        Map<String, Runnable> cases = new LinkedHashMap<>();
        cases.put("integrityOk", () -> loader.validateIntegrity(lib, List.of(new BikeTripConfig("A", "MOTORBIKE", "t1"))));
        cases.put("integrityUnknownTrip", () -> loader.validateIntegrity(lib, List.of(new BikeTripConfig("A", "MOTORBIKE", "nope"))));
        cases.put("integrityUnknownSegment", () -> loader.validateIntegrity(lib, List.of(new BikeTripConfig("B", "MOTORBIKE", "t2"))));
        cases.put("engineEmptyRoute", () -> new MovementEngine(new Route("r", "r", List.of()), 60.0));
        cases.put("vehicleTypeUnknown", () -> VehicleType.valueOf("SCOOTER".toUpperCase(Locale.ROOT)));
        cases.put("vehicleTypeLowercase", () -> VehicleType.valueOf("motorbike".toUpperCase(Locale.ROOT)));
        cases.put("jsonFleetMissingTripId", () -> Json.MAPPER.readValue("[{\"id\":\"A\",\"type\":\"MOTORBIKE\"}]", FLEET));
        cases.put("jsonFleetUnknownField", () -> Json.MAPPER.readValue("[{\"id\":\"A\",\"type\":\"MOTORBIKE\",\"tripId\":\"t\",\"color\":\"red\"}]", FLEET));
        cases.put("jsonCoordinateMissingLat", () -> Json.MAPPER.readValue("{\"lng\":1.0}", Coordinate.class));
        cases.put("jsonCoordinateUnknownField", () -> Json.MAPPER.readValue("{\"lat\":1.0,\"lng\":1.0,\"alt\":3}", Coordinate.class));
        cases.put("jsonSegmentUnknownField", () -> Json.MAPPER.readValue("{\"id\":\"s\",\"points\":[],\"speedLimit\":50}", Segment.class));
        cases.put("jsonSegmentMissingPoints", () -> Json.MAPPER.readValue("{\"id\":\"s\"}", Segment.class));
        cases.put("jsonTripMissingSegments", () -> Json.MAPPER.readValue("{\"id\":\"t\",\"name\":\"n\"}", TripDefinition.class));
        cases.put("jsonDirectedSegmentMissingId", () -> Json.MAPPER.readValue("{\"reversed\":true}", DirectedSegment.class));
        cases.put("jsonMalformed", () -> Json.MAPPER.readValue("[{\"id\":", FLEET));

        JsonNode golden = golden("errors.json");
        assertEquals(golden.size(), cases.size());
        for (JsonNode expected : golden) {
            String name = expected.get("case").asString();
            Runnable action = cases.get(name);
            try {
                action.run();
                if (expected.get("throws").asBoolean()) fail(name + ": expected an exception");
            } catch (RuntimeException e) {
                if (!expected.get("throws").asBoolean()) fail(name + ": unexpected exception", e);
                boolean jackson = expected.get("jackson").asBoolean();
                assertEquals(jackson, e instanceof JacksonException, name + ": " + e);
                if (!jackson) {
                    assertEquals(expected.get("type").asString(), e.getClass().getName(), name);
                    assertEquals(expected.get("message").asString(), e.getMessage(), name);
                }
            }
        }
    }

    @Test
    void bikeActorStopMatchesKotlin() throws Exception {
        boolean expectedStopEndsLoop;
        try (InputStream in = getClass().getResourceAsStream("/golden/bikeactor.txt")) {
            expectedStopEndsLoop = Boolean.parseBoolean(new String(in.readAllBytes()).trim().split("=")[1]);
        }

        Route route = new Route("r", "Long", List.of(new Coordinate(18.0, 98.0), new Coordinate(19.0, 98.0)));
        BikeActor actor = new BikeActor("BA-1", route, 25.0, 20);
        Thread thread = Thread.ofVirtual().start(() -> {
            try {
                actor.startSimulation();
            } catch (InterruptedException ignored) {
                // cancelled
            }
        });
        Thread.sleep(100);
        actor.stop();
        boolean finished = thread.join(Duration.ofSeconds(1));
        thread.interrupt();
        assertTrue(thread.join(Duration.ofSeconds(1)), "interrupt must end the loop");

        assertEquals(expectedStopEndsLoop, finished);
    }
}
