package com.pjmbusnel.smartbikes.io;

import com.pjmbusnel.smartbikes.model.Coordinate;
import com.pjmbusnel.smartbikes.model.DirectedSegment;
import com.pjmbusnel.smartbikes.model.Route;
import com.pjmbusnel.smartbikes.model.Segment;
import com.pjmbusnel.smartbikes.model.TripDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItineraryLoaderTest {

    private final ItineraryLoader loader = new ItineraryLoader();

    // Test Data
    private final Segment segA = new Segment("seg-A", "Segment A", List.of(
            new Coordinate(10.0, 10.0),
            new Coordinate(10.0, 11.0)
    ));

    private final Segment segB = new Segment("seg-B", "Segment B", List.of(
            new Coordinate(10.0, 11.0),
            new Coordinate(10.0, 12.0)
    ));

    private final Segment segC = new Segment("seg-C", "Segment C", List.of(
            new Coordinate(10.0, 12.0),
            new Coordinate(10.0, 13.0)
    ));

    // --- 1. Basic Assembly & Stitching ---

    @Test
    @DisplayName("test assembleRoute stitches points correctly")
    void assembleRouteStitchesPointsCorrectly() {
        TripDefinition trip = new TripDefinition("t1", "Test", List.of(
                new DirectedSegment("seg-A"),
                new DirectedSegment("seg-B")
        ));
        Route route = loader.assembleRoute(trip, List.of(segA, segB), false);

        // Total points: 2 + 2 - 1 (stitched) = 3
        assertEquals(3, route.points().size());
        assertEquals(new Coordinate(10.0, 10.0), route.points().getFirst());
        assertEquals(new Coordinate(10.0, 12.0), route.points().getLast());
    }

    @Test
    @DisplayName("test assembleRoute stitches multiple segments without duplicates")
    void assembleRouteStitchesMultipleSegmentsWithoutDuplicates() {
        TripDefinition trip = new TripDefinition("t-multi", "Multi", List.of(
                new DirectedSegment("seg-A"),
                new DirectedSegment("seg-B"),
                new DirectedSegment("seg-C")
        ));
        Route route = loader.assembleRoute(trip, List.of(segA, segB, segC), false);

        // Verifies the drop(1) logic works across multiple junctions
        assertEquals(4, route.points().size());
        assertEquals(new Coordinate(10.0, 13.0), route.points().getLast());
    }

    // --- 2. Direction & XOR Logic ---

    @Test
    @DisplayName("test assembleRoute return trip XOR logic")
    void assembleRouteReturnTripXorLogic() {
        TripDefinition trip = new TripDefinition("t3", "Full Trip", List.of(
                new DirectedSegment("seg-A", false),
                new DirectedSegment("seg-B", false)
        ));
        // Start should be the end of Seg B (10.0, 12.0)
        Route route = loader.assembleRoute(trip, List.of(segA, segB), true);

        assertEquals(new Coordinate(10.0, 12.0), route.points().getFirst());
        assertEquals(new Coordinate(10.0, 10.0), route.points().getLast());
    }

    @Test
    @DisplayName("test assembleRoute complex XOR logic with mixed directions")
    void assembleRouteComplexXorLogicWithMixedDirections() {
        // Seg B naturally is 11->12. We want a trip driving A(10->11) then B-reversed(12->11)
        TripDefinition trip = new TripDefinition("t-mixed", "Mixed", List.of(
                new DirectedSegment("seg-A", false),
                new DirectedSegment("seg-B", true)
        ));

        // Forward: A(10->11) + B-flipped(12->11). Ends at 11.
        Route forward = loader.assembleRoute(trip, List.of(segA, segB), false);
        assertEquals(new Coordinate(10.0, 11.0), forward.points().getLast());

        // Return (Reverse): Everything flips. B is now 11->12, A is 11->10
        Route returnRoute = loader.assembleRoute(trip, List.of(segA, segB), true);
        assertEquals(new Coordinate(10.0, 11.0), returnRoute.points().getFirst());
        assertEquals(new Coordinate(10.0, 10.0), returnRoute.points().getLast());
    }

    // --- 3. Robustness & Error Handling ---

    @Test
    @DisplayName("test assembleRoute handles missing segment IDs gracefully")
    void assembleRouteHandlesMissingSegmentIdsGracefully() {
        TripDefinition trip = new TripDefinition("t-missing", "Broken", List.of(
                new DirectedSegment("seg-A"),
                new DirectedSegment("NON_EXISTENT"),
                new DirectedSegment("seg-B")
        ));
        // Should skip the missing one and keep the others
        Route route = loader.assembleRoute(trip, List.of(segA, segB), false);
        assertEquals(3, route.points().size());
    }

    @Test
    @DisplayName("test assembleRoute with empty directedSegments")
    void assembleRouteWithEmptyDirectedSegments() {
        TripDefinition emptyTrip = new TripDefinition("t-empty", "Empty", List.of());
        Route route = loader.assembleRoute(emptyTrip, List.of(segA, segB), false);
        assertTrue(route.points().isEmpty()); // Boundary case check
    }

    /**
     * Multi-Segment "Return" Leg:
     * Verifies that for a 3-segment trip (A -> B -> C), the return leg (tripReverse = true)
     * correctly orders the segments as [C-reversed, B-reversed, A-reversed].
     */
    @Test
    @DisplayName("test multi-segment return leg coordinate order")
    void multiSegmentReturnLegCoordinateOrder() {
        Segment segA = new Segment("A", List.of(new Coordinate(10.0, 10.0), new Coordinate(10.0, 11.0)));
        Segment segB = new Segment("B", List.of(new Coordinate(10.0, 11.0), new Coordinate(10.0, 12.0)));
        Segment segC = new Segment("C", List.of(new Coordinate(10.0, 12.0), new Coordinate(10.0, 13.0)));

        TripDefinition trip = new TripDefinition("t-multi", "Multi-Trip", List.of(
                new DirectedSegment("A"), new DirectedSegment("B"), new DirectedSegment("C")
        ));

        // Act: Generate return leg
        Route route = loader.assembleRoute(trip, List.of(segA, segB, segC), true);

        // Assert: Correct sequence: End of C -> Start of C -> Start of B -> Start of A
        // (10.13) -> (10.12) -> (10.11) -> (10.10)
        assertEquals(4, route.points().size());
        assertEquals(new Coordinate(10.0, 13.0), route.points().get(0)); // End of C
        assertEquals(new Coordinate(10.0, 12.0), route.points().get(1)); // Junction C/B
        assertEquals(new Coordinate(10.0, 11.0), route.points().get(2)); // Junction B/A
        assertEquals(new Coordinate(10.0, 10.0), route.points().get(3)); // Start of A
    }

    /**
     * Stitching Integrity:
     * Verifies that if Segment A ends at (1.0, 1.0) and Segment B starts at (1.0, 1.0),
     * the join logic (drop(1)) prevents coordinate duplication.
     */
    @Test
    @DisplayName("test stitching integrity avoids duplicate junction points")
    void stitchingIntegrityAvoidsDuplicateJunctionPoints() {
        Segment segA = new Segment("A", List.of(new Coordinate(1.0, 1.0), new Coordinate(2.0, 2.0), new Coordinate(3.0, 3.0)));
        Segment segB = new Segment("B", List.of(new Coordinate(3.0, 3.0), new Coordinate(4.0, 4.0), new Coordinate(5.0, 5.0)));

        TripDefinition trip = new TripDefinition("t-stitch", "Stitch", List.of(new DirectedSegment("A"), new DirectedSegment("B")));

        // Act
        Route route = loader.assembleRoute(trip, List.of(segA, segB), false);

        // Assert: Should have 5 points (3 from A + 2 from B), not 6.
        assertEquals(5, route.points().size());
        // Verify middle point is only present once
        assertEquals(new Coordinate(3.0, 3.0), route.points().get(2));
        assertEquals(new Coordinate(4.0, 4.0), route.points().get(3));
    }

    /**
     * Oscillation Flip:
     * Verifies that the Route model can be correctly reversed for a return leg
     * when the vehicle reaches its destination.
     */
    @Test
    @DisplayName("test oscillation flip correctly reverses route points")
    void oscillationFlipCorrectlyReversesRoutePoints() {
        List<Coordinate> originalPoints = List.of(
                new Coordinate(18.0, 98.0),
                new Coordinate(18.1, 98.1),
                new Coordinate(18.2, 98.2)
        );
        Route route = new Route("r1", "Oscillation Test", originalPoints);

        // Act: Simulate the flip in runVehicle
        Route flippedRoute = route.withPoints(List.copyOf(route.points().reversed()));

        // Assert
        assertEquals(new Coordinate(18.2, 98.2), flippedRoute.points().getFirst());
        assertEquals(new Coordinate(18.1, 98.1), flippedRoute.points().get(1));
        assertEquals(new Coordinate(18.0, 98.0), flippedRoute.points().getLast());
    }
}
