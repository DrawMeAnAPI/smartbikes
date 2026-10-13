package com.pjmbusnel.smartbikes.model;

/**
 * Defines vehicle categories and their average cruising speeds.
 */
public enum VehicleType {
    E_MOTORBIKE(60.0),
    MOTORBIKE(75.0);

    private final double baseSpeedKph;

    VehicleType(double baseSpeedKph) {
        this.baseSpeedKph = baseSpeedKph;
    }

    public double baseSpeedKph() {
        return baseSpeedKph;
    }
}