package online.farmsteadminecraft.bombrun.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ExplosionBudgetTest {
    @Test
    void limitsExplosionsPerTick() {
        ExplosionBudget b = new ExplosionBudget();
        b.newTick();
        for (int i = 0; i < 3; i++) assertTrue(b.tryExplode(3));
        assertFalse(b.tryExplode(3));
        assertEquals(1, b.delayedExplosions());
        b.newTick();
        assertTrue(b.tryExplode(3));
        assertEquals(3, b.peakPerTick());
    }

    @Test
    void limitsLiveBombs() {
        ExplosionBudget b = new ExplosionBudget();
        b.bombAdded();
        b.bombAdded();
        assertFalse(b.canDrop(2));
        b.bombGone();
        assertTrue(b.canDrop(2));
        b.bombGone();
        b.bombGone();
        assertEquals(0, b.live());
    }

    @Test
    void allowanceShrinksWhenTicksRunLongAndGrowsBack() {
        ExplosionBudget b = new ExplosionBudget();
        assertEquals(48, b.allowance(48));
        b.adapt(20, 35, 2, 48);
        int start = b.allowance(48);
        for (int i = 0; i < 40; i++) b.adapt(120, 35, 2, 48);
        assertEquals(2, b.allowance(48), "a server stuck at 120 ms ticks gets the minimum");
        for (int i = 0; i < 200; i++) b.adapt(10, 35, 2, 48);
        assertEquals(48, b.allowance(48), "a fast server climbs back to the maximum");
        assertTrue(start >= 2 && start <= 48);
    }

    @Test
    void allowanceHoldsInTheDeadBand() {
        ExplosionBudget b = new ExplosionBudget();
        b.adapt(30, 35, 2, 48);
        int a = b.allowance(48);
        for (int i = 0; i < 10; i++) b.adapt(30, 35, 2, 48);
        assertEquals(a, b.allowance(48), "between 80 % and 100 % of the target nothing changes");
    }
}
