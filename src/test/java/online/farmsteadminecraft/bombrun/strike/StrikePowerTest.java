package online.farmsteadminecraft.bombrun.strike;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class StrikePowerTest {
    @Test
    void defaultsGrowInPlanesAndDensity() {
        List<StrikePower> all = StrikePower.parseAll(StrikePower.DEFAULTS, null);
        assertEquals(5, all.size());
        for (int i = 1; i < all.size(); i++) {
            assertTrue(all.get(i).planes() > all.get(i - 1).planes());
            assertTrue(all.get(i).bombs(32, 100000) > all.get(i - 1).bombs(32, 100000));
        }
    }

    @Test
    void bombsFollowAreaAndStopAtTheLimit() {
        StrikePower p = new StrikePower(3, 5);
        assertEquals(Math.round(Math.PI * 400 / 25), p.bombs(20, 100000));
        assertEquals(1, p.bombs(0.5, 100000));
        // the apocalypse on the largest ring hits the 5000 cap
        assertEquals(5000, StrikePower.parse("12:1.6").bombs(96, 5000));
        assertEquals(4, new StrikePower(12, 2).planes(4));
    }

    @Test
    void brokenEntriesFallBackToTheDefaultAtTheirPlace() {
        List<String> problems = new ArrayList<>();
        List<StrikePower> all = StrikePower.parseAll(List.of("2:6", "lots", "0:3"), problems);
        assertEquals(new StrikePower(2, 6), all.get(0));
        assertEquals(StrikePower.parse(StrikePower.DEFAULTS.get(1)), all.get(1));
        assertEquals(StrikePower.parse(StrikePower.DEFAULTS.get(2)), all.get(2));
        assertEquals(2, problems.size());
        assertNull(StrikePower.parse("3:0.1"));
    }
}
