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
}
