package com.pjmbusnel.smartbikes.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Which vehicle runs which trip.
 *
 * @param type      "E_MOTORBIKE" or "MOTORBIKE" (case-insensitive)
 * @param oscillate if false, the vehicle respawns at the start (one-way)
 */
public record BikeTripConfig(String id, String type, String tripId, boolean reverse, boolean oscillate) {

    public BikeTripConfig {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(tripId, "tripId");
    }

    public BikeTripConfig(String id, String type, String tripId) {
        this(id, type, tripId, false, true);
    }

    // JSON entry point: "reverse" defaults to false and "oscillate" to true when absent
    @JsonCreator
    static BikeTripConfig fromJson(
            @JsonProperty("id") String id,
            @JsonProperty("type") String type,
            @JsonProperty("tripId") String tripId,
            @JsonProperty("reverse") boolean reverse,
            @JsonProperty("oscillate") Boolean oscillate) {
        return new BikeTripConfig(id, type, tripId, reverse, oscillate == null || oscillate);
    }
}