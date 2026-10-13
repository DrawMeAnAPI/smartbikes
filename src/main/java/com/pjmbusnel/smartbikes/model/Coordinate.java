package com.pjmbusnel.smartbikes.model;

public record Coordinate(double lat, double lng, String name) {

    public Coordinate(double lat, double lng) {
        this(lat, lng, null);
    }
}