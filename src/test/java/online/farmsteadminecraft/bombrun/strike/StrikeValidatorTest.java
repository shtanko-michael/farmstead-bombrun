package online.farmsteadminecraft.bombrun.strike;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class StrikeValidatorTest {
    private static final StrikeLimits LIMITS = new StrikeLimits(true, 256, 4, 96, 12, 2000, 600, 8);

    private static StrikeRequest ok() {
        return new StrikeRequest(true, true, true, 100, 16, 3, 50, Long.MAX_VALUE, false, 0);
    }

    @Test
    void passes() {
        assertNull(StrikeValidator.check(ok(), LIMITS));
    }

    @Test
    void refusesEachLimit() {
        String p = StrikeValidator.PREFIX;
        assertEquals(new Denial(p + "disabled"), StrikeValidator.check(ok(), new StrikeLimits(false, 256, 4, 96, 12, 2000, 600, 8)));
        assertEquals(new Denial(p + "permission"), StrikeValidator.check(new StrikeRequest(true, false, true, 100, 16, 3, 50, Long.MAX_VALUE, false, 0), LIMITS));
        assertEquals(new Denial(p + "dimension"), StrikeValidator.check(new StrikeRequest(true, true, false, 100, 16, 3, 50, Long.MAX_VALUE, false, 0), LIMITS));
        assertEquals(new Denial(p + "range", 256), StrikeValidator.check(new StrikeRequest(true, true, true, 300, 16, 3, 50, Long.MAX_VALUE, false, 0), LIMITS));
        assertEquals(new Denial(p + "radius", 4, 96), StrikeValidator.check(new StrikeRequest(true, true, true, 100, 200, 3, 50, Long.MAX_VALUE, false, 0), LIMITS));
        assertEquals(new Denial(p + "planes", 12), StrikeValidator.check(new StrikeRequest(true, true, true, 100, 16, 13, 50, Long.MAX_VALUE, false, 0), LIMITS));
        assertEquals(new Denial(p + "bombs", 2000), StrikeValidator.check(new StrikeRequest(true, true, true, 100, 16, 3, 2001, Long.MAX_VALUE, false, 0), LIMITS));
        assertEquals(new Denial(p + "busy", 8), StrikeValidator.check(new StrikeRequest(true, true, true, 100, 16, 3, 50, Long.MAX_VALUE, false, 8), LIMITS));
        assertEquals(new Denial(p + "cooldown", 25), StrikeValidator.check(new StrikeRequest(true, true, true, 100, 16, 3, 50, 100, false, 0), LIMITS));
    }

    @Test
    void commandSkipsPlayerChecks() {
        assertNull(StrikeValidator.check(new StrikeRequest(false, false, true, 5000, 16, 3, 50, 0, false, 0), LIMITS));
        assertNull(StrikeValidator.check(new StrikeRequest(true, true, true, 100, 16, 3, 50, 0, true, 0), LIMITS));
    }
}
