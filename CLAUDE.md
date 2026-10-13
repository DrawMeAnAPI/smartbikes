# CLAUDE.md

Fleet simulator for motorbikes in Chiang Mai: vehicles drive along predefined routes and stream telemetry over a WebSocket to a Leaflet map.

## Stack
- Java 25, Spring Boot 4 (embedded Tomcat, raw WebSocket — no STOMP), Jackson 3 (`tools.jackson`)
- Concurrency: virtual threads (`spring.threads.virtual.enabled=true`; the simulator starts its own with `Thread.ofVirtual()`)
- Model classes are records
- Build with the Maven wrapper: always use `./mvnw`, not a system `mvn`

## Commands
- Tests: `./mvnw test`
- Run: `./mvnw spring-boot:run` (or `./mvnw package` then `java -jar target/smartbikes-sim-1.0-SNAPSHOT.jar`)
- View: open `src/main/resources/index.html` directly in a browser (it connects to `ws://localhost:8080/fleet`; it is not served by the app)

## Layout (`src/main/java/com/pjmbusnel/smartbikes/`)
- `SmartbikesApplication` — entry point
- `model/` — records: `Coordinate`, `Segment`, `TripDefinition`, `DirectedSegment`, `ItineraryLibrary`, `BikeTripConfig`, `Route`, `Telemetry`; enum `VehicleType` (base speeds)
- `io/ItineraryLoader` — loads `itineraries.json` (segments + trips) and `bikes-trips.json` (fleet), stitches segments into a `Route`, validates integrity
- `io/Json` — the single shared `JsonMapper`; use it for all JSON (its settings reproduce the original Jackson 2 behavior)
- `simulator/MovementEngine` — per-leg position/speed (±10% random speed per tick)
- `simulator/FleetSimulator` — one virtual thread per vehicle, 2 s tick, oscillate/loop between legs; the vehicle threads fail together (one crash stops all)
- `web/FleetWebSocketHandler` + `web/WebSocketConfig` — `/fleet` endpoint, any origin allowed (the page is opened from `file://`)
- `simulator/BikeActor` — unused early prototype, see below

## Data files (`src/main/resources/`)
- `itineraries.json`: `segments` (id, name, points) and `trips` (id, name, `directedSegments` with optional `reversed`)
- `bikes-trips.json`: fleet entries `{id, type: E_MOTORBIKE|MOTORBIKE, tripId, reverse=false, oscillate=true}`. Reverse vehicles are named `<id>-R`.

## Wire protocol (`ws://localhost:8080/fleet`)
- Every 2 s per vehicle: `{"bikeId","vehicleType","lat","lng","speedKph","batteryLevel","timestamp"}` — keys in this order; `batteryLevel` is `null` for MOTORBIKE; `timestamp` is ISO-8601 (`Instant.toString()`)
- On a client disconnect, others get `{"type":"SESSION_COUNT","count":n}` (nothing on connect). `index.html` currently has its `SESSION_COUNT` handling commented out.
- Incoming client messages are ignored.

## Tests
- `GoldenMasterTest` compares against `src/test/resources/golden/`, captured from the original Kotlin implementation (routes, parsing defaults, physics bit-for-bit, wire JSON, error messages, BikeActor stop behavior). **Golden files only change on purpose** — a failure there means behavior changed.
- `RandomnessInvariantsTest` checks the random parts by their rules (speed range, battery range and drain, tick geometry).
- Unit tests: `ItineraryLoaderTest`, `MovementEngineTest`, `MovementEngineBoundaryTest`.

## BikeActor and MQTT
- `BikeActor` was the first version of the per-bike loop (fixed speed, console output only), replaced by `MovementEngine` + `FleetSimulator`. Nothing uses it. Its `stop()` does not end the loop — a bug carried over from the Kotlin original on purpose (see the comment in the class).
- The Eclipse Paho MQTT client (`org.eclipse.paho.client.mqttv3`) is a dependency for a planned feature, publishing telemetry to AWS IoT Core; no code uses it yet.
