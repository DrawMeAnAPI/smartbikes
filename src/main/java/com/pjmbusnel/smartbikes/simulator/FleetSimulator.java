package com.pjmbusnel.smartbikes.simulator;

import com.pjmbusnel.smartbikes.io.ItineraryLoader;
import com.pjmbusnel.smartbikes.io.Json;
import com.pjmbusnel.smartbikes.model.BikeTripConfig;
import com.pjmbusnel.smartbikes.model.Coordinate;
import com.pjmbusnel.smartbikes.model.ItineraryLibrary;
import com.pjmbusnel.smartbikes.model.Route;
import com.pjmbusnel.smartbikes.model.Telemetry;
import com.pjmbusnel.smartbikes.model.TripDefinition;
import com.pjmbusnel.smartbikes.model.VehicleType;
import com.pjmbusnel.smartbikes.web.FleetWebSocketHandler;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Loads the fleet and runs each vehicle on its own virtual thread, broadcasting telemetry every tick.
 */
@Component
public class FleetSimulator implements ApplicationRunner {

    private static final int TICK_SECONDS = 2;

    private final FleetWebSocketHandler fleetSockets;

    public FleetSimulator(FleetWebSocketHandler fleetSockets) {
        this.fleetSockets = fleetSockets;
    }

    @Override
    public void run(ApplicationArguments args) {
        // Launch the simulation in the background
        Thread.ofVirtual().name("fleet-launcher").start(this::launchFleet);
    }

    private void launchFleet() {
        ItineraryLoader loader = new ItineraryLoader();
        ItineraryLibrary library = loader.loadTrips();
        List<BikeTripConfig> fleet = loader.loadFleetConfig();

        // Validate Integrity before launching any vehicles
        loader.validateIntegrity(library, fleet);

        System.out.println("--- Starting Fleet Simulation ---");

        VehicleGroup vehicles = new VehicleGroup();
        for (BikeTripConfig config : fleet) {
            TripDefinition tripDef = library.trips().stream()
                    .filter(t -> t.id().equals(config.tripId()))
                    .findFirst()
                    .orElse(null);

            if (tripDef != null) {
                vehicles.start(config.id(), () -> {
                    // Assemble route with proper point-by-point reversal
                    Route finalRoute = loader.assembleRoute(tripDef, library.segments(), config.reverse());
                    String vehicleId = config.reverse() ? config.id() + "-R" : config.id();
                    VehicleType vehicleType = VehicleType.valueOf(config.type().toUpperCase(Locale.ROOT));

                    System.out.println("Launching " + vehicleId + " on " + finalRoute.name());

                    runVehicle(vehicleId, finalRoute, vehicleType, config);
                });
            } else {
                System.out.println("Warning: Trip '" + config.tripId() + "' not found for vehicle " + config.id());
            }
        }
    }

    private void runVehicle(String id, Route initialRoute, VehicleType vehicleType, BikeTripConfig config)
            throws InterruptedException {
        Route currentRoute = initialRoute;
        Double battery = initialBattery(vehicleType);

        while (true) {
            MovementEngine engine = new MovementEngine(currentRoute, vehicleType.baseSpeedKph());
            System.out.println("Vehicle " + id + " starting leg as " + vehicleType.name() + " on route " + currentRoute.id());

            while (!engine.isFinished()) {
                Coordinate newPos = engine.tick(TICK_SECONDS);
                battery = drainBattery(battery);

                Telemetry telemetry = new Telemetry(
                        id,
                        vehicleType.name(),
                        newPos.lat(),
                        newPos.lng(),
                        engine.currentSpeedKph(),
                        battery,
                        Instant.now().toString()
                );

                fleetSockets.broadcast(Json.MAPPER.writeValueAsString(telemetry));
                Thread.sleep(TICK_SECONDS * 1000L);
            }

            // Apply Oscillate vs Loop Logic
            if (config.oscillate()) {
                System.out.println("Vehicle " + id + " reversing for return leg.");
                currentRoute = currentRoute.withPoints(List.copyOf(currentRoute.points().reversed()));
            } else {
                System.out.println("Vehicle " + id + " looping back to start (One-way).");
                // No point reversal needed for one-way streets
            }
            Thread.sleep(2000L);
        }
    }

    /** Electric vehicles start between 75% and 99.5%; others have no battery (null). */
    static Double initialBattery(VehicleType vehicleType) {
        return vehicleType == VehicleType.E_MOTORBIKE ? ThreadLocalRandom.current().nextDouble(75.0, 99.5) : null;
    }

    /** One tick of drain: -0.01, never below 0. Null stays null. */
    static Double drainBattery(Double battery) {
        return battery == null ? null : Math.max(battery - 0.01, 0.0);
    }

    @FunctionalInterface
    private interface VehicleTask {
        void run() throws InterruptedException;
    }

    /**
     * Vehicle threads that fail together, as the coroutines did under one parent scope: the first
     * uncaught failure interrupts every other vehicle and is rethrown (printed by the default handler).
     */
    private static final class VehicleGroup {
        private final List<Thread> threads = new CopyOnWriteArrayList<>();
        private final AtomicBoolean failed = new AtomicBoolean();

        void start(String name, VehicleTask task) {
            if (failed.get()) return;
            Thread thread = Thread.ofVirtual().name(name).unstarted(() -> {
                try {
                    task.run();
                } catch (InterruptedException e) {
                    // Cancelled because a sibling failed
                } catch (RuntimeException | Error e) {
                    if (failed.compareAndSet(false, true)) {
                        threads.forEach(t -> {
                            if (t != Thread.currentThread()) t.interrupt();
                        });
                        throw e;
                    }
                }
            });
            threads.add(thread);
            thread.start();
            if (failed.get()) thread.interrupt();
        }
    }
}
