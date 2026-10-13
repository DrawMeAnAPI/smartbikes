package com.pjmbusnel.smartbikes.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Segment(String id, String name, List<Coordinate> points) {

    public Segment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(points, "points");
    }

    public Segment(String id, List<Coordinate> points) {
        this(id, null, points);
    }
}