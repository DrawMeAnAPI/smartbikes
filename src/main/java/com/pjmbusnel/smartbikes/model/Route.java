package com.pjmbusnel.smartbikes.model;

import java.util.List;
import java.util.Objects;

public record Route(String id, String name, List<Coordinate> points) {

    public Route {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(points, "points");
    }

    public Route withPoints(List<Coordinate> newPoints) {
        return new Route(id, name, newPoints);
    }
}