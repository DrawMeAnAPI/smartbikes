package com.pjmbusnel.smartbikes.simulator;

import com.pjmbusnel.smartbikes.model.Coordinate;
import com.pjmbusnel.smartbikes.model.Route;
import com.pjmbusnel.smartbikes.model.VehicleType;
import com.pjmbusnel.smartbikes.utils.PhysicsUtils;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The random parts can't be compared value-for-value with the Kotlin version (different generators),
 * so these check the rules the Kotlin code guaranteed instead.
 */
class RandomnessInvariantsTest {

    private static final int RUNS = 1_000;

    @Test
    void speedStaysWithinTenPercentOfBase() {
        for (VehicleType type : VehicleType.values()) {
            double base = type.baseSpeedKph();
            Route route = new Route("r", "Long", List.of(new Coordinate(18.0, 98.0), new Coordinate(19.0, 98.0)));
            MovementEngine engine = new MovementEngine(route, base);
            for (int i = 0; i < RUNS; i++) {
                engine.tick(1);
                double speed = engine.currentSpeedKph();
                assertTrue(speed >= base - base * 0.1 && speed < base + base * 0.1, type + " speed " + speed);
            }
        }
    }

    @Test
    void initialBatteryIsInRangeForElectricAndNullOtherwise() {
        for (int i = 0; i < RUNS; i++) {
            Double battery = FleetSimulator.initialBattery(VehicleType.E_MOTORBIKE);
            assertTrue(battery >= 75.0 && battery < 99.5, "battery " + battery);
            assertNull(FleetSimulator.initialBattery(VehicleType.MOTORBIKE));
        }
    }

    @Test
    void batteryDrainsByExactlyOneHundredthAndNeverBelowZero() {
        for (int i = 0; i < RUNS; i++) {
            double start = FleetSimulator.initialBattery(VehicleType.E_MOTORBIKE);
            assertEquals(start - 0.01, FleetSimulator.drainBattery(start));
        }
        assertEquals(0.0, FleetSimulator.drainBattery(0.005));
        assertEquals(0.0, FleetSimulator.drainBattery(0.0));
        assertNull(FleetSimulator.drainBattery(null));

        Double battery = 0.05;
        for (int i = 0; i < 10; i++) battery = FleetSimulator.drainBattery(battery);
        assertEquals(0.0, battery);
    }

    @Test
    void everyTickFollowsFromTheReportedSpeed() {
        // Mix of long and very short legs so both the interpolate and snap branches are exercised
        List<Coordinate> points = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            points.add(new Coordinate(18.0 + i * (i % 3 == 0 ? 0.00001 : 0.001), 98.0 + i * 0.0005));
        }
        Route route = new Route("r", "Mixed", points);

        for (int run = 0; run < RUNS / 50; run++) {
            MovementEngine engine = new MovementEngine(route, 60.0);
            int index = 0;
            Coordinate previous = engine.currentPosition();
            while (!engine.isFinished()) {
                Coordinate next = points.get(index + 1);
                Coordinate actual = engine.tick(2);

                double traveled = (engine.currentSpeedKph() * 1000.0) / 3600.0 * 2;
                double distance = PhysicsUtils.calculateDistance(previous, next);
                Coordinate expected;
                if (traveled >= distance) {
                    expected = next;
                    index++; // at most one waypoint per tick
                } else {
                    expected = PhysicsUtils.interpolate(previous, next, traveled / distance);
                }
                assertEquals(expected, actual);
                assertEquals(expected, engine.currentPosition());
                previous = actual;
            }
            assertEquals(points.size() - 1, index);
            assertEquals(points.getLast(), engine.tick(2), "a finished engine stays on the last point");
        }
    }
}