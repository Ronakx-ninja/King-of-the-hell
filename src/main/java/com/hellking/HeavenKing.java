package com.hellking;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustColorTransitionParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.RaycastContext;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Heaven King abilities: neon water laser that freezes blocks, and the water ball that digs a flooded, ice-sealed crater. */
public class HeavenKing {
    private static final double BEAM_RANGE = 40.0;
    private static final int CRATER_R = 6;
    private static final int CRATER_DEPTH = 7;

    private static final DustColorTransitionParticleEffect CORE =
            new DustColorTransitionParticleEffect(new Vector3f(0.9f, 1.0f, 1.0f), new Vector3f(0.1f, 0.8f, 1.0f), 2.4f);
    private static final DustColorTransitionParticleEffect GLOW =
            new DustColorTransitionParticleEffect(new Vector3f(0.2f, 0.7f, 1.0f), new Vector3f(0.0f, 0.2f, 0.9f), 3.2f);

    private static class Ball {
        SnowballEntity e;
        Vec3d pos, vel;
        ServerPlayerEntity owner;
    }

    private static final List<Ball> BALLS = new ArrayList<>();

    // ------------------------------------------------------------ transformation
    public static void tickTransforming(ServerPlayerEntity p, int t, ServerWorld w) {
        double ang = t * 0.45;
        for (int i = 0; i < 3; i++) {
            double a = ang + i * Math.PI * 2 / 3;
            double r = 1.9 - t * 0.03;
            double x = p.getX() + Math.cos(a) * r, z = p.getZ() + Math.sin(a) * r, y = p.getY() + 0.2 + t * 0.05;
            w.spawnParticles(ParticleTypes.SNOWFLAKE, x, y, z, 1, 0.05, 0.05, 0.05, 0.0);
            w.spawnParticles(ParticleTypes.SPLASH, x, y, z, 3, 0.1, 0.1, 0.1, 0.1);
            w.spawnParticles(GLOW, x, y + 0.3, z, 1, 0, 0, 0, 0);
        }
        w.spawnParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1.0, p.getZ(), 1, 0.5, 0.8, 0.5, 0.01);
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 10, 4, true, false, false));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 10, 3, true, false, false));
    }

    public static void finishTransform(ServerPlayerEntity p, ServerWorld w) {
        w.spawnParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1.0, p.getZ(), 90, 0.5, 0.9, 0.5, 0.25);
        w.spawnParticles(ParticleTypes.SNOWFLAKE, p.getX(), p.getY() + 1.0, p.getZ(), 70, 0.6, 0.9, 0.6, 0.2);
        w.spawnParticles(ParticleTypes.SPLASH, p.getX(), p.getY() + 0.5, p.getZ(), 80, 0.7, 0.4, 0.7, 0.3);
        w.spawnParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.3, p.getZ(), 30, 0.7, 0.2, 0.7, 0.1);
        w.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.5f, 1.6f);
    }

    // ------------------------------------------------------------ G : neon water laser, freezes whatever it touches
    public static void tickBeam(ServerPlayerEntity p, int beamTicks, ServerWorld w) {
        Vec3d look = p.getRotationVec(1.0f);
        Vec3d eye = p.getEyePos();
        Vec3d right = look.crossProduct(new Vec3d(0, 1, 0));
        right = right.lengthSquared() < 1.0E-4 ? new Vec3d(1, 0, 0) : right.normalize();
        Vec3d hand = eye.add(look.multiply(0.7)).add(right.multiply(0.55)).add(0, -0.6, 0);
        Vec3d end = eye.add(look.multiply(BEAM_RANGE));

        var hit = w.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
        Vec3d target = hit.getType() == HitResult.Type.MISS ? end : hit.getPos();
        Vec3d dir = target.subtract(hand);
        double len = dir.length();
        if (len < 0.5) return;
        dir = dir.multiply(1.0 / len);

        for (double s = 2.5; s <= len; s += 0.5) {
            Vec3d c = hand.add(dir.multiply(s));
            double grow = Math.min(0.5, 0.12 + s * 0.015);
            w.spawnParticles(CORE, c.x, c.y, c.z, 1, 0.03, 0.03, 0.03, 0.0);
            w.spawnParticles(GLOW, c.x, c.y, c.z, 1, grow * 0.6, grow * 0.6, grow * 0.6, 0.0);
            int k = (int) (s / 0.5);
            if (k % 2 == 0) w.spawnParticles(ParticleTypes.SPLASH, c.x, c.y, c.z, 2, grow, grow, grow, 0.05);
            if (k % 3 == 0) w.spawnParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 1, grow * 0.5, grow * 0.5, grow * 0.5, 0.01);
            if (k % 4 == 0) w.spawnParticles(ParticleTypes.SNOWFLAKE, c.x, c.y, c.z, 1, grow, grow, grow, 0.02);
        }

        // damage + freeze entities on the aim line
        if (beamTicks % 4 == 0) {
            Set<Entity> victims = new HashSet<>();
            double aim = eye.distanceTo(target);
            for (double s = 1.5; s <= aim; s += 1.0) {
                Vec3d c = eye.add(look.multiply(s));
                Box box = new Box(c.x - 1.2, c.y - 1.2, c.z - 1.2, c.x + 1.2, c.y + 1.2, c.z + 1.2);
                victims.addAll(w.getOtherEntities(p, box, e -> e instanceof LivingEntity && e.isAlive()));
            }
            for (Entity e : victims) {
                e.damage(w.getDamageSources().playerAttack(p), 4.0f);
                e.setFrozenTicks(260);
                if (e instanceof LivingEntity le) le.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 3));
                e.extinguish();
            }
        }

        // impact: freeze every block around the hit point into ice
        if (hit.getType() != HitResult.Type.MISS) {
            Vec3d h = hit.getPos();
            w.spawnParticles(ParticleTypes.SNOWFLAKE, h.x, h.y, h.z, 10, 0.7, 0.7, 0.7, 0.05);
            w.spawnParticles(ParticleTypes.SPLASH, h.x, h.y, h.z, 12, 0.6, 0.6, 0.6, 0.2);
            w.spawnParticles(ParticleTypes.END_ROD, h.x, h.y, h.z, 4, 0.5, 0.5, 0.5, 0.05);
            if (w.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)) {
                BlockPos center = BlockPos.ofFloored(h.x + look.x * 0.4, h.y + look.y * 0.4, h.z + look.z * 0.4);
                int r = 2;
                for (int dx = -r; dx <= r; dx++) for (int dy = -r; dy <= r; dy++) for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz > r * r + 1) continue;
                    BlockPos bp = center.add(dx, dy, dz);
                    if (freezable(w, p, bp)) w.setBlockState(bp, Blocks.ICE.getDefaultState(), 2 | 16);
                }
            }
        }
        if (beamTicks % 8 == 0) w.playSound(null, p.getBlockPos(), ModSounds.BEAM, SoundCategory.PLAYERS, 1.4f, 1.7f);
    }

    private static boolean freezable(ServerWorld w, ServerPlayerEntity p, BlockPos bp) {
        BlockState st = w.getBlockState(bp);
        if (st.isAir() || st.isOf(Blocks.BEDROCK) || st.isOf(Blocks.ICE) || st.hasBlockEntity()) return false;
        if (st.getHardness(w, bp) < 0) return false;
        return w.canPlayerModifyAt(p, bp);
    }

    // ------------------------------------------------------------ R : water ball
    public static void throwWaterBall(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        Vec3d look = p.getRotationVec(1.0f);
        Vec3d right = look.crossProduct(new Vec3d(0, 1, 0));
        right = right.lengthSquared() < 1.0E-4 ? new Vec3d(1, 0, 0) : right.normalize();
        Vec3d hand = p.getEyePos().add(look.multiply(1.1)).add(right.multiply(-0.45)).add(0, -0.45, 0);
        Vec3d vel = look.multiply(1.6);
        SnowballEntity e = new SnowballEntity(w, p);
        e.setItem(new ItemStack(ModItems.WATER_BALL));
        e.setNoGravity(true);
        e.setPosition(hand);
        e.setVelocity(vel);
        w.spawnEntity(e);
        Ball b = new Ball();
        b.e = e; b.pos = hand; b.vel = vel; b.owner = p;
        BALLS.add(b);
        w.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_DOLPHIN_SPLASH, SoundCategory.PLAYERS, 1.6f, 0.8f);
        w.spawnParticles(ParticleTypes.SPLASH, hand.x, hand.y, hand.z, 14, 0.2, 0.2, 0.2, 0.1);
    }

    public static void tickWaterBalls() {
        for (int i = BALLS.size() - 1; i >= 0; i--) {
            Ball b = BALLS.get(i);
            if (!(b.e.getWorld() instanceof ServerWorld w)) { BALLS.remove(i); continue; }
            if (b.e.isRemoved()) {
                BALLS.remove(i);
                impact(b, w);
                continue;
            }
            b.pos = b.e.getPos();
            b.vel = b.e.getVelocity();
            if (b.e.age > 120) { b.e.discard(); continue; }
            w.spawnParticles(ParticleTypes.SPLASH, b.pos.x, b.pos.y, b.pos.z, 4, 0.25, 0.25, 0.25, 0.1);
            w.spawnParticles(GLOW, b.pos.x, b.pos.y, b.pos.z, 1, 0.2, 0.2, 0.2, 0.0);
            w.spawnParticles(ParticleTypes.FALLING_WATER, b.pos.x, b.pos.y, b.pos.z, 3, 0.3, 0.3, 0.3, 0.0);
            w.spawnParticles(ParticleTypes.SNOWFLAKE, b.pos.x, b.pos.y, b.pos.z, 1, 0.3, 0.3, 0.3, 0.02);
        }
    }

    /** Big blast: digs a deep bowl, floods it with water and seals the opening with a layer of ice. */
    private static void impact(Ball b, ServerWorld w) {
        ServerPlayerEntity p = b.owner;
        Vec3d dir = b.vel.lengthSquared() < 1.0E-4 ? new Vec3d(0, -1, 0) : b.vel.normalize();
        var hit = w.raycast(new RaycastContext(b.pos, b.pos.add(dir.multiply(2.5)), RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, p));
        Vec3d at = hit.getType() == HitResult.Type.MISS ? b.pos : hit.getPos();

        w.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        w.spawnParticles(ParticleTypes.SPLASH, at.x, at.y + 0.5, at.z, 260, 3.0, 1.2, 3.0, 0.5);
        w.spawnParticles(ParticleTypes.CLOUD, at.x, at.y + 0.5, at.z, 40, 2.5, 0.8, 2.5, 0.1);
        w.spawnParticles(ParticleTypes.SNOWFLAKE, at.x, at.y + 1.0, at.z, 80, 3.0, 1.5, 3.0, 0.1);
        w.spawnParticles(ParticleTypes.END_ROD, at.x, at.y + 1.0, at.z, 40, 2.0, 1.2, 2.0, 0.2);
        w.playSound(null, BlockPos.ofFloored(at), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 2.0f, 0.7f);
        w.playSound(null, BlockPos.ofFloored(at), SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED, SoundCategory.PLAYERS, 2.0f, 0.6f);

        // opponents near the blast: hurt, soaked and slowed so they sink into the pit
        Box area = new Box(at.x - CRATER_R - 2, at.y - CRATER_DEPTH, at.z - CRATER_R - 2, at.x + CRATER_R + 2, at.y + 4, at.z + CRATER_R + 2);
        for (Entity e : w.getOtherEntities(p, area, en -> en instanceof LivingEntity && en.isAlive())) {
            e.damage(w.getDamageSources().playerAttack(p), 10.0f);
            if (e instanceof LivingEntity le) le.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 2));
        }

        if (!w.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)) return;

        BlockPos c = BlockPos.ofFloored(at.x + dir.x * 0.3, at.y + dir.y * 0.3, at.z + dir.z * 0.3);
        int top = c.getY();                                    // surface layer: becomes the ice lid
        List<BlockPos> bowl = new ArrayList<>();
        for (int dx = -CRATER_R; dx <= CRATER_R; dx++) for (int dz = -CRATER_R; dz <= CRATER_R; dz++) {
            for (int dy = 0; dy <= CRATER_DEPTH; dy++) {
                double ny = dy * (double) CRATER_R / CRATER_DEPTH;
                if (dx * dx + dz * dz + ny * ny > CRATER_R * CRATER_R + 1) continue;
                bowl.add(new BlockPos(c.getX() + dx, top - dy, c.getZ() + dz));
            }
        }
        // 1) dig
        for (BlockPos bp : bowl) {
            BlockState st = w.getBlockState(bp);
            if (st.isAir() || st.isOf(Blocks.BEDROCK) || st.getHardness(w, bp) < 0 || !w.canPlayerModifyAt(p, bp)) continue;
            w.setBlockState(bp, Blocks.AIR.getDefaultState(), 2 | 16);
        }
        // 2) flood below the surface layer, 3) seal the surface layer with ice
        for (BlockPos bp : bowl) {
            if (!w.getBlockState(bp).isAir() && !w.getBlockState(bp).isOf(Blocks.WATER)) continue;
            if (bp.getY() == top) {
                w.setBlockState(bp, Blocks.ICE.getDefaultState(), 3);
                BlockPos above = bp.up();                      // plants/snow left floating above the lid
                BlockState as = w.getBlockState(above);
                if (!as.isAir() && as.isReplaceable()) w.setBlockState(above, Blocks.AIR.getDefaultState(), 2 | 16);
            } else {
                w.setBlockState(bp, Blocks.WATER.getDefaultState(), 3);
            }
        }
    }
}
