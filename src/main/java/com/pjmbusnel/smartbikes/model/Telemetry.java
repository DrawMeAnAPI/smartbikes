package com.pjmbusnel.smartbikes.model;

/**
 * One position report, sent to WebSocket clients as JSON.
 *
 * @param batteryLevel only for electric vehicles, otherwise null
 */
public record Telemetry(
        String bikeId,
        String vehicleType,
        double lat,
        double lng,
        double speedKph,
        Double batteryLevel,
        String timestamp
) {
}