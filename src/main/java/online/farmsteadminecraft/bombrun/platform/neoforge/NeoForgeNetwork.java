package online.farmsteadminecraft.bombrun.platform.neoforge;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import online.farmsteadminecraft.bombrun.net.BlastsPayload;
import online.farmsteadminecraft.bombrun.net.Net;
import online.farmsteadminecraft.bombrun.net.RaidEndPayload;
import online.farmsteadminecraft.bombrun.net.RaidStartPayload;
import online.farmsteadminecraft.bombrun.net.RequestStrikePayload;
import online.farmsteadminecraft.bombrun.net.ServerCapsPayload;
import online.farmsteadminecraft.bombrun.raid.RaidManager;

/** The packets of PLAN.md §5.10 on NeoForge's payload registrar. */
final class NeoForgeNetwork implements Net {
    /** Bumped whenever a payload's layout changes; clients and servers with another version do not connect. */
    static final String VERSION = "3";

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToServer(RequestStrikePayload.TYPE, RequestStrikePayload.CODEC, NeoForgeNetwork::onRequest);
        registrar.playToClient(RaidStartPayload.TYPE, RaidStartPayload.CODEC, (p, ctx) -> ClientPackets.start(p, ctx));
        registrar.playToClient(RaidEndPayload.TYPE, RaidEndPayload.CODEC, (p, ctx) -> ClientPackets.end(p, ctx));
        registrar.playToClient(BlastsPayload.TYPE, BlastsPayload.CODEC, (p, ctx) -> ClientPackets.blasts(p, ctx));
        registrar.playToClient(ServerCapsPayload.TYPE, ServerCapsPayload.CODEC, (p, ctx) -> ClientPackets.caps(p, ctx));
    }

    private static void onRequest(RequestStrikePayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            RaidManager manager = RaidManager.get();
            if (manager != null && ctx.player() instanceof ServerPlayer player) manager.request(player, p);
        });
    }

    @Override
    public void toServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    @Override
    public void toPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public void toDimension(ServerLevel level, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayersInDimension(level, payload);
    }

    /** Client handlers in their own class, so a dedicated server never loads client classes. */
    static final class ClientPackets {
        private ClientPackets() {
        }

        static void start(RaidStartPayload p, IPayloadContext ctx) {
            ctx.enqueueWork(() -> online.farmsteadminecraft.bombrun.client.raid.ClientRaids.start(p));
        }

        static void blasts(BlastsPayload p, IPayloadContext ctx) {
            ctx.enqueueWork(() -> online.farmsteadminecraft.bombrun.client.fx.BlastFx.onBlasts(p));
        }

        static void caps(ServerCapsPayload p, IPayloadContext ctx) {
            ctx.enqueueWork(() -> online.farmsteadminecraft.bombrun.client.remote.TargetingState.caps(p));
        }

        static void end(RaidEndPayload p, IPayloadContext ctx) {
            ctx.enqueueWork(() -> online.farmsteadminecraft.bombrun.client.raid.ClientRaids.end(p));
        }
    }
}
