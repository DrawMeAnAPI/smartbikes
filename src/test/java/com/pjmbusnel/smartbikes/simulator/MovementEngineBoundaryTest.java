package com.pjmbusnel.smartbikes.simulator;

import com.pjmbusnel.smartbikes.io.ItineraryLoader;
import com.pjmbusnel.smartbikes.model.Coordinate;
import com.pjmbusnel.smartbikes.model.DirectedSegment;
import com.pjmbusnel.smartbikes.model.Route;
import com.pjmbusnel.smartbikes.model.TripDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovementEngineBoundaryTest {

    private final ItineraryLoader loader = new ItineraryLoader();

    /**
     * Zero-Length Segments:
     * Tests that the engine handles segments where two points are identical.
     * This ensures interpolate() doesn't cause NaN or division by zero.
     */
    @Test
    @DisplayName("test zero-length segment handling")
    void zeroLengthSegmentHandling() {
        Coordinate sameCoord = new Coordinate(18.0, 98.0);
        Route route = new Route("r-zero", "Zero Length", List.of(sameCoord, sameCoord));
        MovementEngine engine = new MovementEngine(route, 60.0);

        // Act: A tick that would normally move the bike
        Coordinate result = engine.tick(1);

        // Assert: Distance is 0, so it should snap to the end and finish
        assertEquals(sameCoord, result);
        assertTrue(engine.isFinished());
    }

    /**
     * Single-Point Routes:
     * If a route accidentally contains only one point, it should be finished immediately.
     */
    @Test
    @DisplayName("test single-point route behavior")
    void singlePointRouteBehavior() {
        Coordinate coord = new Coordinate(18.0, 98.0);
        Route route = new Route("r-single", "Single Point", List.of(coord));
        MovementEngine engine = new MovementEngine(route, 60.0);

        assertTrue(engine.isFinished(), "A route with 1 point has no segments to travel");
        assertEquals(coord, engine.tick(1), "Tick should simply return the only point available");
    }

    /**
     * Empty Segment Lists in Trip Assembly:
     * Tests ItineraryLoader behavior when a trip points to IDs that don't exist.
     */
    @Test
    @DisplayName("test assembleRoute with non-existent segment IDs")
    void assembleRouteWithNonExistentSegmentIds() {
        TripDefinition tripDef = new TripDefinition("t-error", "Broken Trip", List.of(
                new DirectedSegment("non-existent-id")
        ));

        // Loader should return a Route with an empty points list
        Route route = loader.assembleRoute(tripDef, List.of(), false);

        assertTrue(route.points().isEmpty(), "Route should have no points if segments are missing");
    }

    /**
     * Movement Engine with Empty Route:
     * Verifies that initializing the engine with an empty list (invalid state)
     * is handled gracefully or caught.
     */
    @Test
    @DisplayName("test movement engine with empty route points")
    void movementEngineWithEmptyRoutePoints() {
        Route emptyRoute = new Route("r-empty", "Empty", List.of());

        // Throws NoSuchElementException because the engine starts at the first point
        assertThrows(NoSuchElementException.class, () -> new MovementEngine(emptyRoute, 60.0));
    }
}