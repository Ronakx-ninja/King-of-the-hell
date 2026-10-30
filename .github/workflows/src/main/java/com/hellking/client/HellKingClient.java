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
    private static KeyBinding KEY_TRANSFORM, KEY_FLY, KEY_BEAM, KEY_FIREBALL;
    private static boolean beamHeld = false;

    /** entityId -> {state, flying, lastUpdateTick} */
    public static final Map<Integer, int[]> STATES = new HashMap<>();
    private static int clientTicks = 0;

    private static final DustParticleEffect RED = new DustParticleEffect(new Vector3f(1.0f, 0.1f, 0.05f), 1.4f);

    public static int stateOf(Entity e) {
        int[] s = STATES.get(e.getId());
        return s == null ? 0 : s[0];
    }

    public static boolean isFlying(Entity e) {
        int[] s = STATES.get(e.getId());
        return s != null && s[1] == 1;
    }

    @Override
    public void onInitializeClient() {
        KEY_TRANSFORM = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.hellking.transform", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, CAT));
        KEY_FLY = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.hellking.fly", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F, CAT));
        KEY_BEAM = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.hellking.beam", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, CAT));
        KEY_FIREBALL = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.hellking.fireball", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, CAT));

        EntityModelLayerRegistry.registerModelLayer(HellKingFeatureRenderer.ARMOR_LAYER, HellKingFeatureRenderer::createArmorLayer);
        EntityModelLayerRegistry.registerModelLayer(HellKingFeatureRenderer.GEAR_LAYER, HellKingFeatureRenderer::createGearLayer);

        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, ctx) -> {
            if (renderer instanceof PlayerEntityRenderer pr) {
                helper.register(new HellKingFeatureRenderer(pr, ctx.getModelLoader()));
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(SyncPayload.ID, (payload, context) ->
                STATES.put(payload.entityId(), new int[]{payload.state(), payload.flying(), clientTicks}));

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
        while (KEY_FLY.wasPressed()) send(1);
        while (KEY_FIREBALL.wasPressed()) send(4);
        boolean g = KEY_BEAM.isPressed() && mc.currentScreen == null;
        if (g != beamHeld) {
            beamHeld = g;
            send(g ? 2 : 3);
        }

        // expire stale entries (player left / went out of range)
        Iterator<Map.Entry<Integer, int[]>> it = STATES.entrySet().iterator();
        while (it.hasNext()) {
            var en = it.next();
            Entity e = mc.world.getEntityById(en.getKey());
            if (e == null || clientTicks - en.getValue()[2] > 70) it.remove();
        }

        // extra client-side flame aura / eye glow embers
        for (var en : STATES.entrySet()) {
            if (en.getValue()[0] != 2) continue;
            Entity e = mc.world.getEntityById(en.getKey());
            if (e == null) continue;
            var r = mc.world.random;
            if (clientTicks % 2 == 0) {
                mc.world.addParticle(ParticleTypes.FLAME,
                        e.getX() + (r.nextDouble() - 0.5) * 1.0, e.getY() + r.nextDouble() * 2.0, e.getZ() + (r.nextDouble() - 0.5) * 1.0,
                        0, 0.04, 0);
                mc.world.addParticle(RED,
                        e.getX() + (r.nextDouble() - 0.5) * 1.4, e.getY() + r.nextDouble() * 2.2, e.getZ() + (r.nextDouble() - 0.5) * 1.4,
                        0, 0.03, 0);
            }
            if (clientTicks % 3 == 0) {
                // fire dripping from the cape
                double yaw = Math.toRadians(e.getYaw());
                double bx = e.getX() + Math.sin(yaw) * 0.4, bz = e.getZ() - Math.cos(yaw) * 0.4;
                mc.world.addParticle(ParticleTypes.SMALL_FLAME, bx + (r.nextDouble() - 0.5), e.getY() + r.nextDouble() * 1.6, bz + (r.nextDouble() - 0.5), 0, 0.01, 0);
                mc.world.addParticle(ParticleTypes.LARGE_SMOKE, bx, e.getY() + 1.8, bz, 0, 0.02, 0);
            }
        }
    }
}
