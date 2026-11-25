package online.farmsteadminecraft.bombrun.platform.neoforge;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import online.farmsteadminecraft.bombrun.BombRun;
import online.farmsteadminecraft.bombrun.ModLoaderAccess;
import online.farmsteadminecraft.bombrun.command.BombRunCommand;
import online.farmsteadminecraft.bombrun.config.BombRunConfig;
import online.farmsteadminecraft.bombrun.net.Net;
import online.farmsteadminecraft.bombrun.payload.Bombs;
import online.farmsteadminecraft.bombrun.raid.ChunkTickets;
import online.farmsteadminecraft.bombrun.raid.RaidManager;
import online.farmsteadminecraft.bombrun.registry.BombRunContent;

/** NeoForge entrypoint: registers the content and forwards loader events to the common code. */
@Mod(BombRun.MOD_ID)
public class BombRunNeoForge {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BombRun.MOD_ID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, BombRun.MOD_ID);

    public BombRunNeoForge(IEventBus modBus, ModContainer container) {
        ModLoaderAccess.HOLDER.install(new NeoForgeLoaderAccess());
        Net.HOLDER.install(new NeoForgeNetwork());
        ChunkTickets.HOLDER.install(new NeoForgeChunkTickets());
        BombRun.init();

        Supplier<Item> remote = ITEMS.register(BombRunContent.STRIKE_REMOTE_ID, BombRunContent::newStrikeRemote);
        BombRunContent.strikeRemote = remote;
        @SuppressWarnings("unchecked")
        Supplier<SoundEvent>[] sounds = new Supplier[BombRunContent.SOUND_IDS.length];
        for (int i = 0; i < sounds.length; i++) {
            String id = BombRunContent.SOUND_IDS[i];
            sounds[i] = SOUNDS.register(id, () -> BombRunContent.newSound(id));
        }
        BombRunContent.sounds(sounds);
        ITEMS.register(modBus);
        SOUNDS.register(modBus);

        modBus.addListener(NeoForgeNetwork::register);
        modBus.addListener(NeoForgeChunkTickets::register);
        modBus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) e.accept(remote.get());
        });

        NeoForge.EVENT_BUS.addListener((ServerStartedEvent e) -> RaidManager.serverStarted(e.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> RaidManager.serverStopping());
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> BombRunCommand.register(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Pre e) -> {
            RaidManager m = RaidManager.get();
            if (m != null && e.getLevel() instanceof ServerLevel level) m.levelTick(level);
        });
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post e) -> {
            RaidManager m = RaidManager.get();
            if (m != null && e.getLevel() instanceof ServerLevel level) m.levelTickEnd(level);
        });
        NeoForge.EVENT_BUS.addListener((EntityJoinLevelEvent e) -> {
            RaidManager m = RaidManager.get();
            if (m == null || !(e.getLevel() instanceof ServerLevel level)) return;
            // explosions.dropItems = false: what a bomb knocks loose does not pop out as items
            if (e.getEntity() instanceof ItemEntity item && !e.loadedFromDisk() && !BombRunConfig.DROP_ITEMS.on()
                && m.bombs(level).nearBlast(item.getX(), item.getY(), item.getZ())) {
                e.setCanceled(true);
                return;
            }
            m.entityJoined(e.getEntity(), level);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> sync(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> sync(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((ExplosionEvent.Detonate e) -> {
            if (!Bombs.isBomb(e.getExplosion().getDirectSourceEntity())) return;
            // explosions.breakBlocks = false: the bombs hurt but leave every block standing
            if (!BombRunConfig.BREAK_BLOCKS.on()) e.getAffectedBlocks().clear();
            // falling bombs keep their course, and the blast skips their line-of-sight checks
            if (BombRunConfig.BOMBS_IGNORE_BLASTS.on()) e.getAffectedEntities().removeIf(Bombs::isBomb);
            RaidManager m = RaidManager.get();
            if (m != null && e.getLevel() instanceof ServerLevel level) {
                Vec3 c = e.getExplosion().center();
                m.bombs(level).blast(c.x, c.y, c.z, e.getExplosion().radius());
            }
        });

        if (ModLoaderAccess.get().isClient()) NeoForgeClientHooks.register(container, modBus);
    }

    private static void sync(net.minecraft.world.entity.player.Player player) {
        RaidManager m = RaidManager.get();
        if (m != null && player instanceof ServerPlayer sp) m.syncTo(sp);
    }
}
