package online.farmsteadminecraft.bombrun.client.raid;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundSource;
import online.farmsteadminecraft.bombrun.BombRun;
import online.farmsteadminecraft.bombrun.client.sound.EngineSound;
import online.farmsteadminecraft.bombrun.client.sound.WhistleSound;
import online.farmsteadminecraft.bombrun.config.BombRunClientConfig;
import online.farmsteadminecraft.bombrun.net.RaidEndPayload;
import online.farmsteadminecraft.bombrun.net.RaidStartPayload;
import online.farmsteadminecraft.bombrun.registry.BombRunContent;
import online.farmsteadminecraft.bombrun.strike.FlightPlan;

/**
 * The raids this client knows about: plans received in one packet, drawn and heard from {@code position(t)}
 * with no further traffic (PLAN.md §5.1).
 */
public final class ClientRaids {
    private static final Map<Integer, ClientRaid> RAIDS = new LinkedHashMap<>();

    private ClientRaids() {
    }

    public static final class ClientRaid {
        public final int id;
        public final FlightPlan plan;
        public final long startTick;
        boolean sirenPlayed;
        int nextDrop;
        final EngineSound[] engines;
        boolean ended;

        ClientRaid(int id, FlightPlan plan, long startTick) {
            this.id = id;
            this.plan = plan;
            this.startTick = startTick;
            this.engines = new EngineSound[plan.planes()];
        }

        /** Ticks since the raid started, with the frame's fraction. */
        public double time(Minecraft mc, float partial) {
            return mc.level == null ? 0 : mc.level.getGameTime() - startTick + partial;
        }

        public boolean alive() {
            return !ended;
        }
    }

    public static Collection<ClientRaid> all() {
        return RAIDS.values();
    }

    public static void start(RaidStartPayload p) {
        try {
            ClientRaid old = RAIDS.remove(p.raidId());
            if (old != null) old.ended = true;
            RAIDS.put(p.raidId(), new ClientRaid(p.raidId(), FlightPlan.fromBytes(p.plan()), p.startTick()));
        } catch (IOException e) {
            BombRun.LOGGER.warn("[Bomb Run] raid #{}: unreadable flight plan from the server ({})", p.raidId(), e.getMessage());
        }
    }

    public static void end(RaidEndPayload p) {
        ClientRaid raid = RAIDS.get(p.raidId());
        if (raid == null) return;
        // a finished raid's planes are already gone; a cancelled one disappears now
        if (p.cancelled()) {
            raid.ended = true;
            RAIDS.remove(p.raidId());
        }
    }

    public static void clear() {
        for (ClientRaid r : RAIDS.values()) r.ended = true;
        RAIDS.clear();
    }

    public static void tick(Minecraft mc) {
        if (mc.level == null) {
            if (!RAIDS.isEmpty()) clear();
            return;
        }
        int maxEngines = BombRunClientConfig.MAX_ENGINES.value();
        int whistles = BombRunClientConfig.WHISTLES.value();
        int engines = 0;
        for (Iterator<ClientRaid> it = RAIDS.values().iterator(); it.hasNext(); ) {
            ClientRaid raid = it.next();
            FlightPlan plan = raid.plan;
            long t = mc.level.getGameTime() - raid.startTick;
            if (t > plan.duration() + 40) {
                raid.ended = true;
                it.remove();
                continue;
            }
            if (!raid.sirenPlayed && BombRunClientConfig.SIREN.on() && BombRunContent.siren != null && t < plan.firstDropTick()) {
                mc.getSoundManager().play(new SimpleSoundInstance(BombRunContent.siren.get(), SoundSource.HOSTILE, 8f, 1f,
                    mc.level.getRandom(), plan.targetX, plan.targetY + 2, plan.targetZ));
            }
            raid.sirenPlayed = true;
            for (int i = 0; i < plan.planes(); i++) {
                EngineSound e = raid.engines[i];
                if (e != null && e.isStopped()) raid.engines[i] = e = null;
                if (e != null) {
                    engines++;
                    continue;
                }
                if (engines >= maxEngines || !plan.flying(i, t) || BombRunContent.engine == null) continue;
                e = new EngineSound(raid, i);
                raid.engines[i] = e;
                mc.getSoundManager().play(e);
                engines++;
            }
            while (raid.nextDrop < plan.drops() && plan.dropTick(raid.nextDrop) <= t) {
                if (raid.nextDrop < whistles && BombRunContent.whistle != null) {
                    mc.getSoundManager().play(new WhistleSound(plan, raid.nextDrop));
                }
                raid.nextDrop++;
            }
        }
    }
}
