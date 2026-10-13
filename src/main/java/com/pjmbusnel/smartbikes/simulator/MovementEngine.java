package com.pjmbusnel.smartbikes.simulator;

import com.pjmbusnel.smartbikes.model.Coordinate;
import com.pjmbusnel.smartbikes.model.Route;
import com.pjmbusnel.smartbikes.utils.PhysicsUtils;

import java.util.NoSuchElementException;
import java.util.concurrent.ThreadLocalRandom;

public class MovementEngine {

    private final Route route;
    private final double baseSpeedKph;

    private int currentPointIndex = 0;
    private Coordinate currentPosition;

    // We store the "actual" current speed for reporting
    private double currentSpeedKph;

    public MovementEngine(Route route, double baseSpeedKph) {
        this.route = route;
        this.baseSpeedKph = baseSpeedKph;
        if (route.points().isEmpty()) {
            throw new NoSuchElementException("List is empty.");
        }
        this.currentPosition = route.points().getFirst();
        this.currentSpeedKph = baseSpeedKph;
    }

    public Coordinate currentPosition() {
        return currentPosition;
    }

    public double currentSpeedKph() {
        return currentSpeedKph;
    }

    public Coordinate tick(int secondsPassed) {
        if (isFinished()) return route.points().getLast();

        // 1. Simulate light speed variation (+/- 10% of base speed)
        double variance = baseSpeedKph * 0.1;
        currentSpeedKph = baseSpeedKph + ThreadLocalRandom.current().nextDouble(-variance, variance);

        // 2. Convert current variable speed to meters per second
        double metersPerSecond = (currentSpeedKph * 1000.0) / 3600.0;
        Coordinate nextWaypoint = route.points().get(currentPointIndex + 1);
        double distanceToNext = PhysicsUtils.calculateDistance(currentPosition, nextWaypoint);
        double distanceTraveled = metersPerSecond * secondsPassed;

        if (distanceTraveled >= distanceToNext) {
            currentPointIndex++;
            currentPosition = nextWaypoint;
        } else {
            double fraction = distanceTraveled / distanceToNext;
            currentPosition = PhysicsUtils.interpolate(currentPosition, nextWaypoint, fraction);
        }
        return currentPosition;
    }

    public boolean isFinished() {
        return currentPointIndex >= route.points().size() - 1;
    }
}