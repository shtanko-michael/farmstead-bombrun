package online.farmsteadminecraft.bombrun.registry;

import java.util.function.Supplier;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import online.farmsteadminecraft.bombrun.BombRun;
import online.farmsteadminecraft.bombrun.item.StrikeRemoteItem;

/**
 * The mod's registered things. Each loader registers them its own way and fills these suppliers; common code
 * only reads them. Sounds are our own events that point at vanilla sounds for now (placeholders, PLAN.md §8).
 */
public final class BombRunContent {
    public static final String STRIKE_REMOTE_ID = "strike_remote";
    public static final String[] SOUND_IDS = {"raid.siren", "raid.engine", "raid.whistle", "remote.confirm", "remote.deny"};

    public static Supplier<Item> strikeRemote;
    public static Supplier<SoundEvent> siren, engine, whistle, confirm, deny;

    private BombRunContent() {
    }

    public static Item newStrikeRemote() {
        return new StrikeRemoteItem(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.UNCOMMON));
    }

    public static SoundEvent newSound(String id) {
        return SoundEvent.createVariableRangeEvent(BombRun.id(id));
    }

    /** Called by the loader once the sound suppliers exist, in {@link #SOUND_IDS} order. */
    @SafeVarargs
    public static void sounds(Supplier<SoundEvent>... s) {
        siren = s[0];
        engine = s[1];
        whistle = s[2];
        confirm = s[3];
        deny = s[4];
    }
}
