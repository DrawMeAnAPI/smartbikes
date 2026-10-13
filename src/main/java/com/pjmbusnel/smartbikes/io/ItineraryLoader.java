package com.pjmbusnel.smartbikes.io;

import com.pjmbusnel.smartbikes.model.BikeTripConfig;
import com.pjmbusnel.smartbikes.model.Coordinate;
import com.pjmbusnel.smartbikes.model.DirectedSegment;
import com.pjmbusnel.smartbikes.model.ItineraryLibrary;
import com.pjmbusnel.smartbikes.model.Route;
import com.pjmbusnel.smartbikes.model.Segment;
import com.pjmbusnel.smartbikes.model.TripDefinition;
import tools.jackson.core.type.TypeReference;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ItineraryLoader {

    /**
     * Loads the road infrastructure (segments and trip definitions).
     */
    public ItineraryLibrary loadTrips() {
        try (InputStream inputStream = resource("itineraries.json", "itineraries.json not found in resources!")) {
            return Json.MAPPER.readValue(inputStream, ItineraryLibrary.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Loads the fleet configuration (which bike does which trip).
     */
    public List<BikeTripConfig> loadFleetConfig() {
        try (InputStream stream = resource("bikes-trips.json", "bikes-trips.json not found")) {
            return Json.MAPPER.readValue(stream, new TypeReference<List<BikeTripConfig>>() {
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private InputStream resource(String name, String notFoundMessage) {
        InputStream stream = getClass().getClassLoader().getResourceAsStream(name);
        if (stream == null) {
            throw new IllegalStateException(notFoundMessage);
        }
        return stream;
    }

    public Route assembleRoute(TripDefinition tripDef, List<Segment> allSegments) {
        return assembleRoute(tripDef, allSegments, false);
    }

    /**
     * Logic to stitch segments together into a single continuous Route.
     * Parameter 'tripReverse' to handle coordinate-level inversion.
     */
    public Route assembleRoute(TripDefinition tripDef, List<Segment> allSegments, boolean tripReverse) {
        // 1. If the WHOLE TRIP is reversed (Return leg), reverse the segment sequence
        List<DirectedSegment> directedSegments =
                tripReverse ? tripDef.directedSegments().reversed() : tripDef.directedSegments();
        List<Coordinate> allPoints = new ArrayList<>();

        for (DirectedSegment directedSeg : directedSegments) {
            Segment segment = allSegments.stream()
                    .filter(s -> s.id().equals(directedSeg.segmentId()))
                    .findFirst()
                    .orElse(null);
            if (segment != null) {
                // 2. Logic: Should this specific segment be flipped?
                // A segment is flipped if (it's marked reverse in the trip) XOR (the whole trip is a return leg)
                boolean shouldFlipSegment = directedSeg.reversed() ^ tripReverse;

                List<Coordinate> points = shouldFlipSegment ? segment.points().reversed() : segment.points();

                if (allPoints.isEmpty()) {
                    allPoints.addAll(points);
                } else {
                    // TODO is it still necessary ??
                    points.stream().skip(1).forEach(allPoints::add); // Stitching point
                }
            }
        }
        return new Route(tripDef.id(), tripDef.name(), allPoints);
    }

    /**
     * Performs a "Smoke Test" to ensure all JSON files are internally consistent.
     * Throws an IllegalStateException if a mismatch is found.
     */
    public void validateIntegrity(ItineraryLibrary library, List<BikeTripConfig> fleet) {
        Set<String> tripIds = library.trips().stream().map(TripDefinition::id).collect(Collectors.toSet());
        Set<String> segmentIds = library.segments().stream().map(Segment::id).collect(Collectors.toSet());

        for (BikeTripConfig bike : fleet) {
            // 1. Check if the tripId exists
            if (!tripIds.contains(bike.tripId())) {
                throw new IllegalStateException(
                        "Integrity Error: Bike '" + bike.id() + "' refers to non-existent trip '" + bike.tripId() + "'");
            }

            // 2. Check if all segments within that trip exist
            library.trips().stream()
                    .filter(t -> t.id().equals(bike.tripId()))
                    .findFirst()
                    .ifPresent(trip -> {
                        for (DirectedSegment dirSeg : trip.directedSegments()) {
                            if (!segmentIds.contains(dirSeg.segmentId())) {
                                throw new IllegalStateException("Integrity Error: Trip '" + trip.id()
                                        + "' refers to non-existent segment '" + dirSeg.segmentId() + "'");
                            }
                        }
                    });
        }
        System.out.println("--- Integrity Check Passed: Fleet and Infrastructure are synced ---");
    }
}