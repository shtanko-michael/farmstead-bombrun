package online.farmsteadminecraft.bombrun.platform.neoforge;

import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import online.farmsteadminecraft.bombrun.client.fx.BlastFx;
import net.neoforged.neoforge.common.NeoForge;
import online.farmsteadminecraft.bombrun.client.BombRunClient;
import online.farmsteadminecraft.bombrun.client.remote.TargetingState;

final class NeoForgeClientHooks {
    private NeoForgeClientHooks() {
    }

    static void register(ModContainer container, IEventBus modBus) {
        BombRunClient.init();
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> BombRunClient.tick(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> BombRunClient.leftWorld());
        NeoForge.EVENT_BUS.addListener((InputEvent.MouseScrollingEvent e) -> {
            if (Minecraft.getInstance().screen == null && TargetingState.scroll(Minecraft.getInstance(), e.getScrollDeltaY())) {
                e.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((InputEvent.InteractionKeyMappingTriggered e) -> {
            if (e.isAttack() && TargetingState.attack(Minecraft.getInstance())) {
                e.setSwingHand(false);
                e.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((ViewportEvent.ComputeCameraAngles e) -> {
            float[] o = BlastFx.cameraOffsets((float) e.getPartialTick());
            if (o == null) return;
            e.setYaw(e.getYaw() + o[0]);
            e.setPitch(e.getPitch() + o[1]);
            e.setRoll(e.getRoll() + o[2]);
        });
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent e) -> {
            // after entities the global model-view matrix carries the camera rotation, so the event's
            // identity pose plus camera-relative vertices land in the right place (as in Footprints)
            if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
                float partial = e.getPartialTick().getGameTimeDeltaPartialTick(false);
                BombRunClient.renderWorld(e.getPoseStack().last().pose(), e.getCamera().getPosition(), partial);
            }
        });
    }
}
