package com.hellking.client;

import com.hellking.net.ActionPayload;
import com.hellking.net.SyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class HellKingClient implements ClientModInitializer {
    private static final String CAT = "key.categories.hellking";
    private static KeyBinding KEY_TRANSFORM, KEY_BEAM, KEY_FIREBALL;
    private static boolean beamHeld = false;

    /** entityId -> {state, flying, lastUpdateTick} */
    public static final Map<Integer, int[]> STATES = new HashMap<>();
    private static int clientTicks = 0;

    private static final DustParticleEffect RED = new DustParticleEffect(new Vector3f(1.0f, 0.1f, 0.05f), 1.4f);

    public static int stateOf(Entity e) {
        int[] s = STATES.get(e.getId());
        return s == null ? 0 : s[0];
    }

    private static int flagsOf(Entity e) {
        int[] s = STATES.get(e.getId());
        return s == null ? 0 : s[1];
    }

    /** 0 = Hell King, 1 = Heaven King */
    public static int formOf(Entity e) {
        return (flagsOf(e) & 8) != 0 ? 1 : 0;
    }

    public static boolean isKing(Entity e) {
        return stateOf(e) == 2;
    }

    public static boolean isFlying(Entity e) {
        // the local player reads its own flight state directly (no network delay)
        if (e instanceof net.minecraft.entity.player.PlayerEntity pe && e == MinecraftClient.getInstance().player) {
            return stateOf(e) == 2 && pe.getAbilities().flying;
        }
        return (flagsOf(e) & 1) != 0;
    }

    public static boolean isCasting(Entity e) {
        return (flagsOf(e) & 2) != 0;
    }

    public static boolean isBeaming(Entity e) {
        return (flagsOf(e) & 4) != 0;
    }

    @Override
    public void onInitializeClient() {
        KEY_TRANSFORM = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.hellking.transform", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, CAT));
        KEY_BEAM = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.hellking.beam", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, CAT));
        KEY_FIREBALL = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.hellking.fireball", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, CAT));

        EntityModelLayerRegistry.registerModelLayer(HellKingFeatureRenderer.ARMOR_LAYER, HellKingFeatureRenderer::createArmorLayer);
        EntityModelLayerRegistry.registerModelLayer(HellKingFeatureRenderer.GEAR_LAYER, HellKingFeatureRenderer::createGearLayer);

        EntityModelLayerRegistry.registerModelLayer(HeavenKingFeatureRenderer.ARMOR_LAYER, HeavenKingFeatureRenderer::createArmorLayer);
        EntityModelLayerRegistry.registerModelLayer(HeavenKingFeatureRenderer.GEAR_LAYER, HeavenKingFeatureRenderer::createGearLayer);

        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, ctx) -> {
            if (renderer instanceof PlayerEntityRenderer pr) {
                helper.register(new HellKingFeatureRenderer(pr, ctx.getModelLoader()));
                helper.register(new HeavenKingFeatureRenderer(pr, ctx.getModelLoader()));
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(SyncPayload.ID, (payload, context) ->
                STATES.put(payload.entityId(), new int[]{payload.state(), payload.flags(), clientTicks}));

        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void send(int action) {
        if (ClientPlayNetworking.canSend(ActionPayload.ID)) ClientPlayNetworking.send(new ActionPayload(action));
    }

    private void tick(MinecraftClient mc) {
        clientTicks++;
        if (mc.player == null || mc.world == null) {
            STATES.clear();
            beamHeld = false;
            return;
        }

        while (KEY_TRANSFORM.wasPressed()) send(0);
        while (KEY_FIREBALL.wasPressed()) send(4);
        boolean g = KEY_BEAM.isPressed() && mc.currentScreen == null;
        if (g != beamHeld) {
            beamHeld = g;
            send(g ? 2 : 3);
        }

        // Hell King never burns on screen
        if (stateOf(mc.player) == 2 && formOf(mc.player) == 0) mc.player.extinguish();

        // superman flight: while flying and holding W you climb / dive where you look
        if (stateOf(mc.player) == 2 && mc.player.getAbilities().flying && mc.options.forwardKey.isPressed()) {
            mc.player.addVelocity(0, mc.player.getRotationVec(1.0f).y * 0.2, 0);
        }

        // expire stale entries (player left / went out of range)
        Iterator<Map.Entry<Integer, int[]>> it = STATES.entrySet().iterator();
        while (it.hasNext()) {
            var en = it.next();
            Entity e = mc.world.getEntityById(en.getKey());
            if (e == null || clientTicks - en.getValue()[2] > 70) it.remove();
        }

    }
}
