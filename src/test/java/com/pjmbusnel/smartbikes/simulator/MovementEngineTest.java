package com.pjmbusnel.smartbikes.simulator;

import com.pjmbusnel.smartbikes.model.Coordinate;
import com.pjmbusnel.smartbikes.model.Route;
import com.pjmbusnel.smartbikes.utils.PhysicsUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovementEngineTest {

    @Test
    @DisplayName("test engine progression updates public currentPosition")
    void engineProgressionUpdatesPublicCurrentPosition() {
        Coordinate start = new Coordinate(18.79, 98.98);
        Coordinate end = new Coordinate(18.80, 98.99);
        Route route = new Route("test-id", "Test Route", List.of(start, end));
        MovementEngine engine = new MovementEngine(route, 60.0);

        // Verify initial state
        assertEquals(start.lat(), engine.currentPosition().lat());

        // Act: Move for a few seconds
        engine.tick(5);

        // Assert: Accessing the public property should now show the new position
        assertNotEquals(start.lat(), engine.currentPosition().lat(), "Position should have moved from start");
        assertTrue(engine.currentPosition().lat() > start.lat(), "Position should be progressing toward end");
    }

    /**
     * Fixed Distance Validation:
     * Verifies that the engine moves the vehicle by the correct number of meters
     * relative to the speed reported for that tick.
     */
    @Test
    @DisplayName("test fixed distance validation")
    void fixedDistanceValidation() {
        // Setup: A long straight route north
        Coordinate start = new Coordinate(18.0, 98.0);
        Coordinate end = new Coordinate(18.1, 98.0); // Approx 11.1km away
        Route route = new Route("r1", "Straight", List.of(start, end));
        MovementEngine engine = new MovementEngine(route, 36.0); // 36 km/h = 10 m/s

        // Act: 1 second tick
        engine.tick(1);

        // The engine fluctuates speed (+/- 10%), so we check against the currentSpeedKph it generated
        double expectedDistance = (engine.currentSpeedKph() * 1000.0 / 3600.0) * 1.0;
        double actualDistance = PhysicsUtils.calculateDistance(start, engine.currentPosition());

        // Assert precision within 1 millimeter
        assertEquals(expectedDistance, actualDistance, 0.001);
    }

    /**
     * Waypoint Overshoot:
     * Verifies that if a vehicle travels further than the distance to the next waypoint,
     * it "snaps" exactly to that waypoint and does not fly past it.
     */
    @Test
    @DisplayName("test waypoint overshoot logic")
    void waypointOvershootLogic() {
        // Setup: A very short segment (~1.1 meters)
        Coordinate start = new Coordinate(18.0, 98.0);
        Coordinate end = new Coordinate(18.00001, 98.0);
        Route route = new Route("r2", "Short", List.of(start, end));

        // Speed: 360 km/h = 100 m/s. 1 tick = 100m (covers the 1.1m segment easily)
        MovementEngine engine = new MovementEngine(route, 360.0);

        // Act: 1 second tick
        engine.tick(1);

        // Assert: The engine must land exactly on the destination point
        assertEquals(end.lat(), engine.currentPosition().lat());
        assertEquals(end.lng(), engine.currentPosition().lng());

        // The engine must report it is finished
        assertTrue(engine.isFinished());
    }

    /**
     * Multi-Waypoint Progression:
     * Verifies that the engine can move through multiple waypoints sequentially.
     * Note: Current logic clears a maximum of one waypoint per tick.
     */
    @Test
    @DisplayName("test movement through multiple waypoints")
    void movementThroughMultipleWaypoints() {
        Coordinate p1 = new Coordinate(18.0, 98.0);
        Coordinate p2 = new Coordinate(18.0001, 98.0); // ~11m
        Coordinate p3 = new Coordinate(18.0002, 98.0); // ~11m
        Route route = new Route("r3", "Multi", List.of(p1, p2, p3));

        MovementEngine engine = new MovementEngine(route, 360.0); // 100 m/s

        // Tick 1: Clears p1 -> p2
        engine.tick(1);
        assertEquals(p2.lat(), engine.currentPosition().lat());
        assertFalse(engine.isFinished());

        // Tick 2: Clears p2 -> p3
        engine.tick(1);
        assertEquals(p3.lat(), engine.currentPosition().lat());
        assertTrue(engine.isFinished());
    }
}