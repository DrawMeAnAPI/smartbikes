package com.pjmbusnel.smartbikes.io;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The shared JSON mapper, configured to behave like the original Kotlin {@code jacksonObjectMapper()}
 * (Jackson 2 defaults) where Jackson 3 changed them; verified by {@code GoldenMasterTest}.
 * Unknown properties fail, and a missing number (e.g. a coordinate without "lat") reads as 0.0
 * instead of failing. Properties keep declaration order (records already do).
 */
public final class Json {

    public static final JsonMapper MAPPER = JsonMapper.builder()
            .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    private Json() {
    }
}
