package com.pjmbusnel.smartbikes.model;

import java.util.Objects;

/**
 * A reference to a segment within a trip. If {@code reversed} is true, this specific segment is reversed.
 */
public record DirectedSegment(String segmentId, boolean reversed) {

    public DirectedSegment {
        Objects.requireNonNull(segmentId, "segmentId");
    }

    public DirectedSegment(String segmentId) {
        this(segmentId, false);
    }
}