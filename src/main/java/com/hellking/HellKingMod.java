package com.hellking;

import com.hellking.net.ActionPayload;
import com.hellking.net.SyncPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public class HellKingMod implements ModInitializer {
    public static final String MOD_ID = "hellking";

    @Override
    public void onInitialize() {
        ModSounds.init();
        ModItems.init();

        PayloadTypeRegistry.playC2S().register(ActionPayload.ID, ActionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncPayload.ID, SyncPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ActionPayload.ID,
                (payload, context) -> HellKingManager.handleAction(context.player(), payload.action()));

        ServerTickEvents.END_SERVER_TICK.register(HellKingManager::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> HellKingManager.reset(handler.player));
    }
}
