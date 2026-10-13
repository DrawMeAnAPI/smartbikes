package com.pjmbusnel.smartbikes.utils;

import com.pjmbusnel.smartbikes.model.Coordinate;

public final class PhysicsUtils {

    private static final double EARTH_RADIUS_METERS = 6371000.0;

    private PhysicsUtils() {
    }

    /**
     * Calculates the distance between two points using the Haversine formula.
     */
    public static double calculateDistance(Coordinate start, Coordinate end) {
        double dLat = Math.toRadians(end.lat() - start.lat());
        double dLon = Math.toRadians(end.lng() - start.lng());

        double a = Math.pow(Math.sin(dLat / 2), 2) +
                Math.cos(Math.toRadians(start.lat())) * Math.cos(Math.toRadians(end.lat())) *
                Math.pow(Math.sin(dLon / 2), 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

    /**
     * Finds a point between {@code start} and {@code end} based on a {@code fraction} (0.0 to 1.0).
     */
    public static Coordinate interpolate(Coordinate start, Coordinate end, double fraction) {
        double lat = start.lat() + (end.lat() - start.lat()) * fraction;
        double lng = start.lng() + (end.lng() - start.lng()) * fraction;
        return new Coordinate(lat, lng);
    }
}
