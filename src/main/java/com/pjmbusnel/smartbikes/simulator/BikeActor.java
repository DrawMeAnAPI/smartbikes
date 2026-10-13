package com.pjmbusnel.smartbikes.simulator;

import com.pjmbusnel.smartbikes.model.Coordinate;
import com.pjmbusnel.smartbikes.model.Route;
import com.pjmbusnel.smartbikes.utils.PhysicsUtils;

import java.time.Instant;

/**
 * Early prototype of the per-bike simulation loop (fixed speed, console logging only).
 * Superseded by {@link MovementEngine} and {@code FleetSimulator}; not used by the application.
 */
public class BikeActor {

    private final String bikeId;
    private final Route route;
    private final double speedKph;
    private final long tickRateMs;

    private Coordinate currentPosition;
    private int pointIndex = 0;

    // Kept for parity with the Kotlin original, whose loop condition resolved `isActive` to the
    // coroutine's isActive rather than this field: stop() never ended the loop. The loop here
    // likewise checks the thread's interrupt status, the virtual-thread equivalent.
    private volatile boolean isActive = true;

    public BikeActor(String bikeId, Route route) {
        this(bikeId, route, 25.0, 5000);
    }

    public BikeActor(String bikeId, Route route, double speedKph, long tickRateMs) {
        this.bikeId = bikeId;
        this.route = route;
        this.speedKph = speedKph;
        this.tickRateMs = tickRateMs;
        this.currentPosition = route.points().getFirst();
    }

    public String bikeId() {
        return bikeId;
    }

    /**
     * The main simulation loop for this specific bike. Blocks; run it on a virtual thread.
     */
    public void startSimulation() throws InterruptedException {
        System.out.println("[" + bikeId + "] Starting delivery: " + route.name());

        while (!Thread.currentThread().isInterrupted() && pointIndex < route.points().size() - 1) {
            Coordinate start = currentPosition;
            Coordinate destination = route.points().get(pointIndex + 1);

            // Calculate movement for this "tick"
            currentPosition = moveTowards(start, destination);

            // Log Telemetry (This is what we will eventually send to AWS IoT)
            logTelemetry();

            // Check if we reached the waypoint
            if (currentPosition.equals(destination)) {
                pointIndex++;
            }

            Thread.sleep(tickRateMs);
        }

        System.out.println("[" + bikeId + "] Destination reached or simulation stopped.");
    }

    private Coordinate moveTowards(Coordinate start, Coordinate end) {
        double distanceToEnd = PhysicsUtils.calculateDistance(start, end);
        double metersPerTick = (speedKph * 1000 / 3600) * (tickRateMs / 1000.0);

        if (distanceToEnd <= metersPerTick) {
            return end; // We arrive at the next waypoint
        }
        double fraction = metersPerTick / distanceToEnd;
        return PhysicsUtils.interpolate(start, end, fraction);
    }

    private void logTelemetry() {
        Instant timestamp = Instant.now();
        System.out.println("[" + bikeId + "] | " + timestamp + " | Pos: (" + "%.5f".formatted(currentPosition.lat())
                + ", " + "%.5f".formatted(currentPosition.lng()) + ") | State: MOVING");
    }

    public void stop() {
        isActive = false;
    }
}
