package com.pjmbusnel.smartbikes.model;

import java.util.List;
import java.util.Objects;

public record TripDefinition(String id, String name, List<DirectedSegment> directedSegments) {

    public TripDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(directedSegments, "directedSegments");
    }
}