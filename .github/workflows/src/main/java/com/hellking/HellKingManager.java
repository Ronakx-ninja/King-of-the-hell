package com.hellking;

import com.hellking.net.SyncPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class HellKingManager {
    private static final int TRANSFORM_TICKS = 40;
    private static final double BEAM_RANGE = 28.0;

    private static class Data {
        int state = 0;          // 0 normal, 1 transforming, 2 hell king
        int transformTimer = 0;
        boolean flying = false;
        boolean beam = false;
        int fireballCooldown = 0;
        int beamTicks = 0;
    }

    private static final Map<UUID, Data> DATA = new HashMap<>();
    private static final DustParticleEffect RED_DUST = new DustParticleEffect(new Vector3f(1.0f, 0.12f, 0.05f), 2.2f);
    private static final DustParticleEffect DARK_DUST = new DustParticleEffect(new Vector3f(0.25f, 0.0f, 0.0f), 1.6f);

    private static Data data(ServerPlayerEntity p) {
        return DATA.computeIfAbsent(p.getUuid(), k -> new Data());
    }

    public static void reset(ServerPlayerEntity p) {
        Data d = DATA.remove(p.getUuid());
        if (d != null) restoreAbilities(p);
    }

    private static boolean hasScythe(ServerPlayerEntity p) {
        for (ItemStack s : p.getInventory().main) if (s.isOf(ModItems.HELL_SCYTHE)) return true;
        for (ItemStack s : p.getInventory().offHand) if (s.isOf(ModItems.HELL_SCYTHE)) return true;
        return false;
    }

    // ------------------------------------------------------------ actions
    public static void handleAction(ServerPlayerEntity p, int action) {
        Data d = data(p);
        switch (action) {
            case 0 -> { // transform toggle
                if (d.state == 0) {
                    if (!p.isHolding(ModItems.HELL_SCYTHE)) {
                        p.sendMessage(Text.literal("§cHell Scythe haath mein pakdo, phir transform karo!"), true);
                        return;
                    }
                    d.state = 1;
                    d.transformTimer = TRANSFORM_TICKS;
                    p.getWorld().playSound(null, p.getBlockPos(), ModSounds.TRANSFORM, SoundCategory.PLAYERS, 2.0f, 1.0f);
                    sync(p, d);
                } else if (d.state == 2) {
                    detransform(p, d);
                }
            }
            case 1 -> { // fly toggle
                if (d.state != 2) return;
                if (!d.flying) {
                    d.flying = true;
                    p.getAbilities().allowFlying = true;
                    p.getAbilities().flying = true;
                    p.getAbilities().setFlySpeed(0.12f);
                    p.setVelocity(p.getVelocity().add(0, 0.6, 0));
                    p.velocityModified = true;
                    p.sendAbilitiesUpdate();
                    p.getWorld().playSound(null, p.getBlockPos(), ModSounds.FLY, SoundCategory.PLAYERS, 1.5f, 1.0f);
                } else {
                    d.flying = false;
                    restoreAbilities(p);
                }
                sync(p, d);
            }
            case 2 -> { if (d.state == 2) d.beam = true; }
            case 3 -> d.beam = false;
            case 4 -> {
                if (d.state != 2 || d.fireballCooldown > 0) return;
                d.fireballCooldown = 12;
                fireball(p);
            }
            default -> {}
        }
    }

    private static void detransform(ServerPlayerEntity p, Data d) {
        d.state = 0;
        d.flying = false;
        d.beam = false;
        restoreAbilities(p);
        p.getWorld().playSound(null, p.getBlockPos(), ModSounds.FIREBALL, SoundCategory.PLAYERS, 1.0f, 0.6f);
        sync(p, d);
    }

    private static void restoreAbilities(ServerPlayerEntity p) {
        p.getAbilities().setFlySpeed(0.05f);
        if (!p.isCreative() && !p.isSpectator()) {
            p.getAbilities().allowFlying = false;
            p.getAbilities().flying = false;
        }
        p.sendAbilitiesUpdate();
    }

    private static void fireball(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        Vec3d look = p.getRotationVec(1.0f);
        Vec3d pos = p.getEyePos().add(look.multiply(1.2)).add(0, -0.35, 0);
        FireballEntity ball = new FireballEntity(w, p, look.multiply(1.6), 2);
        ball.setPosition(pos);
        w.spawnEntity(ball);
        w.playSound(null, p.getBlockPos(), ModSounds.FIREBALL, SoundCategory.PLAYERS, 1.6f, 0.9f + p.getRandom().nextFloat() * 0.2f);
        w.spawnParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, 18, 0.3, 0.3, 0.3, 0.05);
    }

    // ------------------------------------------------------------ sync
    private static void sync(ServerPlayerEntity p, Data d) {
        SyncPayload payload = new SyncPayload(p.getId(), d.state, d.flying ? 1 : 0);
        MinecraftServer server = p.getServer();
        if (server == null) return;
        for (ServerPlayerEntity other : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(other, payload);
        }
    }

    // ------------------------------------------------------------ tick
    public static void tick(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            Data d = DATA.get(p.getUuid());
            if (d == null || d.state == 0) continue;
            ServerWorld w = p.getServerWorld();

            if (d.fireballCooldown > 0) d.fireballCooldown--;

            if (!p.isAlive() || !hasScythe(p)) {
                detransform(p, d);
                continue;
            }

            if (p.age % 20 == 0) sync(p, d);

            if (d.state == 1) {
                tickTransforming(p, d, w);
                continue;
            }

            // ---- state 2: Hell King
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 40, 0, true, false, false));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 40, 1, true, false, false));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 40, 1, true, false, false));
            p.fallDistance = 0;

            if (d.flying) {
                if (!p.getAbilities().flying && p.isOnGround()) {
                    d.flying = false;
                    restoreAbilities(p);
                    sync(p, d);
                } else {
                    w.spawnParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 0.2, p.getZ(), 5, 0.4, 0.1, 0.4, 0.02);
                    w.spawnParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 0.1, p.getZ(), 2, 0.4, 0.1, 0.4, 0.01);
                    if (p.age % 14 == 0) w.playSound(null, p.getBlockPos(), ModSounds.FLY, SoundCategory.PLAYERS, 0.7f, 1.0f);
                }
            }

            // aura
            if (p.age % 2 == 0) {
                w.spawnParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1.0, p.getZ(), 3, 0.5, 0.9, 0.5, 0.01);
                w.spawnParticles(RED_DUST, p.getX(), p.getY() + 1.0, p.getZ(), 2, 0.5, 0.9, 0.5, 0.0);
                w.spawnParticles(ParticleTypes.ASH, p.getX(), p.getY() + 1.2, p.getZ(), 2, 0.6, 0.8, 0.6, 0.0);
            }

            if (d.beam) tickBeam(p, d, w);
            else d.beamTicks = 0;
        }
    }

    private static void tickTransforming(ServerPlayerEntity p, Data d, ServerWorld w) {
        int t = TRANSFORM_TICKS - d.transformTimer;
        double ang = t * 0.5;
        for (int i = 0; i < 3; i++) {
            double a = ang + i * Math.PI * 2 / 3;
            double r = 1.8 - t * 0.03;
            w.spawnParticles(ParticleTypes.FLAME, p.getX() + Math.cos(a) * r, p.getY() + t * 0.05, p.getZ() + Math.sin(a) * r, 2, 0.05, 0.05, 0.05, 0.02);
            w.spawnParticles(RED_DUST, p.getX() + Math.cos(a) * r, p.getY() + 0.3 + t * 0.04, p.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
        w.spawnParticles(ParticleTypes.LAVA, p.getX(), p.getY() + 1, p.getZ(), 1, 0.6, 0.8, 0.6, 0.0);
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 10, 4, true, false, false));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 10, 3, true, false, false));

        if (--d.transformTimer <= 0) {
            d.state = 2;
            w.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, p.getX(), p.getY() + 1, p.getZ(), 1, 0, 0, 0, 0);
            w.spawnParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1, p.getZ(), 120, 0.4, 0.8, 0.4, 0.25);
            w.spawnParticles(ParticleTypes.LAVA, p.getX(), p.getY() + 1, p.getZ(), 30, 0.6, 0.8, 0.6, 0.0);
            sync(p, d);
        }
    }

    private static void tickBeam(ServerPlayerEntity p, Data d, ServerWorld w) {
        d.beamTicks++;
        Vec3d look = p.getRotationVec(1.0f);
        Vec3d start = p.getEyePos().add(look.multiply(0.8)).add(0, -0.45, 0);
        Vec3d end = start.add(look.multiply(BEAM_RANGE));

        var hit = w.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, p));
        double len = BEAM_RANGE;
        if (hit.getType() != HitResult.Type.MISS) len = hit.getPos().distanceTo(start);

        Set<Entity> victims = new HashSet<>();
        for (double s = 0; s <= len; s += 0.8) {
            Vec3d c = start.add(look.multiply(s));
            double spread = 0.25 + s * 0.012;
            w.spawnParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 5, spread, spread, spread, 0.02);
            w.spawnParticles(RED_DUST, c.x, c.y, c.z, 2, spread, spread, spread, 0.0);
            if (s % 1.6 < 0.8) w.spawnParticles(ParticleTypes.SMALL_FLAME, c.x, c.y, c.z, 3, spread * 1.4, spread * 1.4, spread * 1.4, 0.03);
            if (s % 3.2 < 0.8) w.spawnParticles(DARK_DUST, c.x, c.y, c.z, 1, spread, spread, spread, 0.0);

            if (d.beamTicks % 4 == 0) {
                Box box = new Box(c.x - 1.3, c.y - 1.3, c.z - 1.3, c.x + 1.3, c.y + 1.3, c.z + 1.3);
                victims.addAll(w.getOtherEntities(p, box, e -> e instanceof LivingEntity && e.isAlive()));
            }
        }

        if (hit.getType() != HitResult.Type.MISS) {
            Vec3d h = hit.getPos();
            w.spawnParticles(ParticleTypes.LAVA, h.x, h.y, h.z, 3, 0.4, 0.4, 0.4, 0.0);
            w.spawnParticles(ParticleTypes.EXPLOSION, h.x, h.y, h.z, 1, 0.3, 0.3, 0.3, 0.0);
        }

        if (d.beamTicks % 4 == 0) {
            for (Entity e : victims) {
                e.damage(w.getDamageSources().playerAttack(p), 4.0f);
                e.setOnFireFor(8);
            }
        }
        if (d.beamTicks % 8 == 0) {
            w.playSound(null, p.getBlockPos(), ModSounds.BEAM, SoundCategory.PLAYERS, 1.8f, 1.0f);
            if (hit.getType() == HitResult.Type.BLOCK && w.getGameRules().getBoolean(net.minecraft.world.GameRules.DO_MOB_GRIEFING)) {
                BlockPos fire = new BlockPos(
                        (int) Math.floor(hit.getPos().x - look.x * 0.3),
                        (int) Math.floor(hit.getPos().y - look.y * 0.3),
                        (int) Math.floor(hit.getPos().z - look.z * 0.3));
                BlockState state = w.getBlockState(fire);
                if (state.isAir() && AbstractFireBlock.canPlaceAt(w, fire, Direction.UP)) {
                    w.setBlockState(fire, AbstractFireBlock.getState(w, fire));
                }
            }
        }
    }
}
