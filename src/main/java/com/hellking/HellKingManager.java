package com.hellking;

import com.hellking.net.SyncPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustColorTransitionParticleEffect;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class HellKingManager {
    private static final int TRANSFORM_TICKS = 40;
    private static final double BEAM_RANGE = 28.0;

    private static class Data {
        int form = 0;           // 0 = Hell King, 1 = Heaven King
        int state = 0;          // 0 normal, 1 transforming, 2 transformed
        int transformTimer = 0;
        boolean flying = false;
        boolean beam = false;
        int fireballCooldown = 0;
        int beamTicks = 0;
        int castTicks = 0;      // left arm held out while the fireball volley comes out
        int volley = 0;         // fireballs still to launch
        int volleyTimer = 0;
        int lastFlags = -1;
        Vec3d lastFirePos = null;
    }

    private static final List<FireballEntity> BALLS = new ArrayList<>();

    private static final Map<UUID, Data> DATA = new HashMap<>();
    private static final DustParticleEffect RED_DUST = new DustParticleEffect(new Vector3f(1.0f, 0.12f, 0.05f), 2.2f);
    private static final DustParticleEffect DARK_DUST = new DustParticleEffect(new Vector3f(0.25f, 0.0f, 0.0f), 1.6f);
    // bright laser core: white-yellow fading to orange-red
    private static final DustColorTransitionParticleEffect CORE =
            new DustColorTransitionParticleEffect(new Vector3f(1.0f, 0.95f, 0.55f), new Vector3f(1.0f, 0.25f, 0.05f), 2.4f);
    private static final DustColorTransitionParticleEffect GLOW =
            new DustColorTransitionParticleEffect(new Vector3f(1.0f, 0.5f, 0.1f), new Vector3f(0.6f, 0.0f, 0.0f), 3.2f);

    /** Hell King takes no fire / lava / explosion (own fireballs) / fall damage while transformed. */
    public static boolean isImmune(net.minecraft.entity.LivingEntity e, DamageSource src) {
        if (!(e instanceof ServerPlayerEntity p)) return false;
        Data d = DATA.get(p.getUuid());
        if (d == null || d.state != 2 || d.form != 0) return false;
        return src.isIn(DamageTypeTags.IS_FIRE) || src.isIn(DamageTypeTags.IS_EXPLOSION)
                || src.isOf(DamageTypes.LAVA) || src.isOf(DamageTypes.IN_FIRE) || src.isOf(DamageTypes.ON_FIRE)
                || src.isOf(DamageTypes.HOT_FLOOR) || src.isIn(DamageTypeTags.IS_FALL);
    }

    private static Data data(ServerPlayerEntity p) {
        return DATA.computeIfAbsent(p.getUuid(), k -> new Data());
    }

    public static void reset(ServerPlayerEntity p) {
        Data d = DATA.remove(p.getUuid());
        if (d != null) restoreAbilities(p);
    }

    private static boolean hasWeapon(ServerPlayerEntity p, Data d) {
        net.minecraft.item.Item it = d.form == 1 ? ModItems.HEAVEN_TRIDENT : ModItems.HELL_SCYTHE;
        for (ItemStack s : p.getInventory().main) if (s.isOf(it)) return true;
        for (ItemStack s : p.getInventory().offHand) if (s.isOf(it)) return true;
        return false;
    }

    // ------------------------------------------------------------ actions
    public static void handleAction(ServerPlayerEntity p, int action) {
        Data d = data(p);
        switch (action) {
            case 0 -> { // transform toggle
                if (d.state == 0) {
                    if (p.isHolding(ModItems.HELL_SCYTHE)) d.form = 0;
                    else if (p.isHolding(ModItems.HEAVEN_TRIDENT)) d.form = 1;
                    else {
                        p.sendMessage(Text.literal("§cHell Scythe ya Heaven Trident haath mein pakdo, phir V dabao!"), true);
                        return;
                    }
                    d.state = 1;
                    d.transformTimer = TRANSFORM_TICKS;
                    p.getWorld().playSound(null, p.getBlockPos(), ModSounds.TRANSFORM, SoundCategory.PLAYERS, 2.0f, d.form == 1 ? 1.6f : 1.0f);
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
                if (d.form == 1) {
                    d.fireballCooldown = 60;
                    d.castTicks = 14;
                    HeavenKing.throwWaterBall(p);
                    return;
                }
                d.fireballCooldown = 45;
                d.volley = 4;
                d.volleyTimer = 6;
                d.castTicks = 20;
                p.getWorld().playSound(null, p.getBlockPos(), ModSounds.FIREBALL, SoundCategory.PLAYERS, 1.2f, 0.7f);
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

    /** Launches one hellfire ball out of the left (empty) hand. */
    private static void fireball(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        Vec3d look = p.getRotationVec(1.0f);
        Vec3d right = look.crossProduct(new Vec3d(0, 1, 0));
        right = right.lengthSquared() < 1.0E-4 ? new Vec3d(1, 0, 0) : right.normalize();
        Vec3d hand = p.getEyePos().add(look.multiply(1.1)).add(right.multiply(-0.45)).add(0, -0.45, 0);
        var rnd = p.getRandom();
        Vec3d dir = look.add((rnd.nextDouble() - 0.5) * 0.12, (rnd.nextDouble() - 0.5) * 0.12, (rnd.nextDouble() - 0.5) * 0.12).normalize();
        FireballEntity ball = new FireballEntity(w, p, dir.multiply(1.8), 4);
        ball.setItem(new ItemStack(ModItems.HELL_FIREBALL));
        ball.setPosition(hand);
        ball.setVelocity(dir.multiply(1.8));
        w.spawnEntity(ball);
        BALLS.add(ball);
        w.playSound(null, p.getBlockPos(), ModSounds.FIREBALL, SoundCategory.PLAYERS, 1.6f, 0.8f + rnd.nextFloat() * 0.3f);
        w.spawnParticles(ParticleTypes.FLAME, hand.x, hand.y, hand.z, 10, 0.15, 0.15, 0.15, 0.05);
    }

    // ------------------------------------------------------------ sync
    private static int flagsOf(Data d) {
        return (d.flying ? 1 : 0) | (d.castTicks > 0 ? 2 : 0) | (d.beam && d.state == 2 ? 4 : 0) | (d.form == 1 ? 8 : 0);
    }

    private static void sync(ServerPlayerEntity p, Data d) {
        d.lastFlags = flagsOf(d);
        SyncPayload payload = new SyncPayload(p.getId(), d.state, flagsOf(d));
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

            if (!p.isAlive() || !hasWeapon(p, d)) {
                detransform(p, d);
                continue;
            }

            if (p.age % 20 == 0) sync(p, d);

            if (d.state == 1) {
                tickTransforming(p, d, w);
                continue;
            }

            // ---- state 2: transformed
            if (d.form == 0) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 40, 0, true, false, false));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 40, 1, true, false, false));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 40, 1, true, false, false));
                if (p.isOnFire()) p.extinguish();
            } else {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 40, 1, true, false, false));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 40, 1, true, false, false));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.WATER_BREATHING, 40, 0, true, false, false));
            }
            p.fallDistance = 0;

            // double-tap space flight (vanilla creative-style) is enabled for the whole transformation
            if (!p.getAbilities().allowFlying) {
                p.getAbilities().allowFlying = true;
                p.getAbilities().setFlySpeed(0.1f);
                p.sendAbilitiesUpdate();
            }
            boolean fl = p.getAbilities().flying;
            if (fl != d.flying) d.flying = fl;

            // fireball volley: 4 balls, one every 3 ticks, out of the left hand
            if (d.form == 0 && d.volley > 0 && --d.volleyTimer <= 0) {
                fireball(p);
                d.volley--;
                d.volleyTimer = 3;
            }
            if (d.castTicks > 0) d.castTicks--;

            if (d.beam) {
                if (d.form == 1) { d.beamTicks++; HeavenKing.tickBeam(p, d.beamTicks, w); }
                else tickBeam(p, d, w);
            } else d.beamTicks = 0;

            boolean griefing = w.getGameRules().getBoolean(net.minecraft.world.GameRules.DO_MOB_GRIEFING);
            if (griefing && d.form == 0) {
                if (d.flying) flightSmash(p, w);
                else walkingFire(p, d, w);
            }

            if (flagsOf(d) != d.lastFlags || p.age % 20 == 0) sync(p, d);
        }

        HeavenKing.tickWaterBalls();

        // flame trail behind every hellfire ball
        for (int i = BALLS.size() - 1; i >= 0; i--) {
            FireballEntity b = BALLS.get(i);
            if (b.isRemoved() || !(b.getWorld() instanceof ServerWorld bw)) {
                BALLS.remove(i);
                continue;
            }
            bw.spawnParticles(ParticleTypes.FLAME, b.getX(), b.getY(), b.getZ(), 4, 0.25, 0.25, 0.25, 0.02);
            bw.spawnParticles(ParticleTypes.SMALL_FLAME, b.getX(), b.getY(), b.getZ(), 3, 0.4, 0.4, 0.4, 0.03);
            if (b.age % 3 == 0) bw.spawnParticles(ParticleTypes.LAVA, b.getX(), b.getY(), b.getZ(), 1, 0.3, 0.3, 0.3, 0.0);
        }
    }

    /** True if this block may be wiped out by the Hell King. */
    private static boolean smashable(ServerWorld w, ServerPlayerEntity p, BlockPos bp, float maxHardness) {
        BlockState st = w.getBlockState(bp);
        if (st.isAir() || st.isOf(Blocks.BEDROCK)) return false;
        float h = st.getHardness(w, bp);
        if (h < 0 || h > maxHardness) return false;
        return w.canPlayerModifyAt(p, bp);
    }

    /** While flying: everything in the way (trees, leaves, rock...) is blasted away so the flight never stops. */
    private static void flightSmash(ServerPlayerEntity p, ServerWorld w) {
        Vec3d look = p.getRotationVec(1.0f);
        Vec3d c = p.getPos().add(0, 0.9, 0).add(look.multiply(1.6));
        BlockPos center = BlockPos.ofFloored(c);
        int r = 3;
        int broken = 0;
        for (int dx = -r; dx <= r; dx++) for (int dy = -r; dy <= r; dy++) for (int dz = -r; dz <= r; dz++) {
            if (dx * dx + dy * dy + dz * dz > r * r) continue;
            BlockPos bp = center.add(dx, dy, dz);
            if (!smashable(w, p, bp, 30f)) continue;
            w.setBlockState(bp, Blocks.AIR.getDefaultState(), 18);
            broken++;
        }
        if (broken > 0 && p.age % 3 == 0) {
            w.spawnParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0.8, 0.8, 0.8, 0.0);
            w.spawnParticles(ParticleTypes.SMOKE, c.x, c.y, c.z, 6, 0.8, 0.8, 0.8, 0.02);
        }
    }

    /** On the ground: fire follows the Hell King's footsteps and nearby flammable things catch fire. */
    private static void walkingFire(ServerPlayerEntity p, Data d, ServerWorld w) {
        if (p.age % 4 != 0) return;
        Vec3d pos = p.getPos();
        if (d.lastFirePos == null || d.lastFirePos.squaredDistanceTo(pos) > 0.6) {
            d.lastFirePos = pos;
            BlockPos feet = p.getBlockPos();
            if (p.isOnGround() && w.getBlockState(feet).isAir() && AbstractFireBlock.canPlaceAt(w, feet, Direction.UP)
                    && w.canPlayerModifyAt(p, feet)) {
                w.setBlockState(feet, AbstractFireBlock.getState(w, feet));
            }
        }
        // things that are close and flammable (wood, leaves, grass, wool...) catch fire
        var rnd = p.getRandom();
        for (int i = 0; i < 14; i++) {
            BlockPos bp = p.getBlockPos().add(rnd.nextInt(7) - 3, rnd.nextInt(5) - 1, rnd.nextInt(7) - 3);
            BlockState st = w.getBlockState(bp);
            if (!st.isBurnable()) continue;
            for (Direction dir : Direction.values()) {
                BlockPos n = bp.offset(dir);
                if (w.getBlockState(n).isAir() && AbstractFireBlock.canPlaceAt(w, n, dir.getOpposite()) && w.canPlayerModifyAt(p, n)) {
                    w.setBlockState(n, AbstractFireBlock.getState(w, n));
                    break;
                }
            }
        }
    }

    private static void tickTransforming(ServerPlayerEntity p, Data d, ServerWorld w) {
        if (d.form == 1) {
            HeavenKing.tickTransforming(p, TRANSFORM_TICKS - d.transformTimer, w);
            if (--d.transformTimer <= 0) {
                d.state = 2;
                p.getAbilities().allowFlying = true;
                p.getAbilities().setFlySpeed(0.1f);
                p.sendAbilitiesUpdate();
                HeavenKing.finishTransform(p, w);
                sync(p, d);
            }
            return;
        }
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
            p.getAbilities().allowFlying = true;
            p.getAbilities().setFlySpeed(0.1f);
            p.sendAbilitiesUpdate();
            w.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, p.getX(), p.getY() + 1, p.getZ(), 1, 0, 0, 0, 0);
            w.spawnParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1, p.getZ(), 120, 0.4, 0.8, 0.4, 0.25);
            w.spawnParticles(ParticleTypes.LAVA, p.getX(), p.getY() + 1, p.getZ(), 30, 0.6, 0.8, 0.6, 0.0);
            sync(p, d);
        }
    }

    private static void tickBeam(ServerPlayerEntity p, Data d, ServerWorld w) {
        d.beamTicks++;
        Vec3d look = p.getRotationVec(1.0f);
        Vec3d eye = p.getEyePos();
        Vec3d right = look.crossProduct(new Vec3d(0, 1, 0));
        right = right.lengthSquared() < 1.0E-4 ? new Vec3d(1, 0, 0) : right.normalize();
        // the visible beam leaves the scythe hand (low and to the right) so it never covers the screen centre
        Vec3d hand = eye.add(look.multiply(0.7)).add(right.multiply(0.55)).add(0, -0.6, 0);
        Vec3d end = eye.add(look.multiply(BEAM_RANGE));

        var hit = w.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, p));
        Vec3d target = hit.getType() == HitResult.Type.MISS ? end : hit.getPos();
        Vec3d dir = target.subtract(hand);
        double len = dir.length();
        if (len < 0.5) return;
        dir = dir.multiply(1.0 / len);

        // ---- visuals: bright core + orange glow + flames, nothing within 2.5 blocks of the player
        for (double s = 2.5; s <= len; s += 0.55) {
            Vec3d c = hand.add(dir.multiply(s));
            double grow = Math.min(0.6, 0.15 + s * 0.02);
            w.spawnParticles(CORE, c.x, c.y, c.z, 1, 0.04, 0.04, 0.04, 0.0);
            w.spawnParticles(GLOW, c.x, c.y, c.z, 1, grow * 0.6, grow * 0.6, grow * 0.6, 0.0);
            w.spawnParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 2, grow, grow, grow, 0.02);
            if (((int) (s / 0.55)) % 3 == 0) w.spawnParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 1, grow * 0.5, grow * 0.5, grow * 0.5, 0.02);
            if (((int) (s / 0.55)) % 5 == 0) w.spawnParticles(ParticleTypes.LAVA, c.x, c.y, c.z, 1, grow, grow, grow, 0.0);
        }

        // ---- damage along the real aim line
        if (d.beamTicks % 4 == 0) {
            Set<Entity> victims = new HashSet<>();
            double aim = eye.distanceTo(target);
            for (double s = 1.5; s <= aim; s += 1.0) {
                Vec3d c = eye.add(look.multiply(s));
                Box box = new Box(c.x - 1.3, c.y - 1.3, c.z - 1.3, c.x + 1.3, c.y + 1.3, c.z + 1.3);
                victims.addAll(w.getOtherEntities(p, box, e -> e instanceof LivingEntity && e.isAlive()));
            }
            for (Entity e : victims) {
                e.damage(w.getDamageSources().playerAttack(p), 5.0f);
                e.setOnFireFor(8);
            }
        }

        // ---- impact: big blast of particles + the beam eats blocks (bores a tunnel while held)
        if (hit.getType() != HitResult.Type.MISS) {
            Vec3d h = hit.getPos();
            w.spawnParticles(ParticleTypes.LAVA, h.x, h.y, h.z, 4, 0.8, 0.8, 0.8, 0.0);
            w.spawnParticles(ParticleTypes.FLAME, h.x, h.y, h.z, 12, 0.8, 0.8, 0.8, 0.08);
            w.spawnParticles(ParticleTypes.EXPLOSION, h.x, h.y, h.z, 1, 0.6, 0.6, 0.6, 0.0);

            if (w.getGameRules().getBoolean(net.minecraft.world.GameRules.DO_MOB_GRIEFING)) {
                BlockPos center = BlockPos.ofFloored(h.x + look.x * 0.5, h.y + look.y * 0.5, h.z + look.z * 0.5);
                int r = 4;
                for (int dx = -r; dx <= r; dx++) for (int dy = -r; dy <= r; dy++) for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz > r * r + 1) continue;
                    BlockPos bp = center.add(dx, dy, dz);
                    BlockState st = w.getBlockState(bp);
                    if (!smashable(w, p, bp, 60f)) continue;
                    w.setBlockState(bp, Blocks.AIR.getDefaultState(), 18);
                    if (p.getRandom().nextInt(6) == 0) {
                        w.spawnParticles(ParticleTypes.LAVA, bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5, 1, 0.3, 0.3, 0.3, 0.0);
                    }
                }
                // leave burning ground behind
                BlockPos fire = center.down(r);
                if (w.getBlockState(fire).isAir() && AbstractFireBlock.canPlaceAt(w, fire, Direction.UP)) {
                    w.setBlockState(fire, AbstractFireBlock.getState(w, fire));
                }
            }
        }

        if (d.beamTicks % 8 == 0) {
            w.playSound(null, p.getBlockPos(), ModSounds.BEAM, SoundCategory.PLAYERS, 1.8f, 1.0f);
        }
    }
}
