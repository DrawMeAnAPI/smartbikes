package com.pjmbusnel.smartbikes.model;

import java.util.List;
import java.util.Objects;

public record ItineraryLibrary(List<Segment> segments, List<TripDefinition> trips) {

    public ItineraryLibrary {
        Objects.requireNonNull(segments, "segments");
        Objects.requireNonNull(trips, "trips");
    }
}