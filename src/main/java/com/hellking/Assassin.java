package com.hellking;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.RaycastContext;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Wind Assassin (Katana): M1 slice, G horizontal cutting slash, R two thrown katanas, B draw / sheathe. */
public class Assassin {
    private static final int SLASH_G_TICKS = 8;
    private static final int SLASH_M1_TICKS = 5;
    private static final int THROW_TICKS = 20;
    private static final double SLASH_RANGE = 9.0;

    private static final DustParticleEffect VOID = new DustParticleEffect(new Vector3f(0.02f, 0.02f, 0.05f), 3.2f);
    private static final DustParticleEffect VOID_SMALL = new DustParticleEffect(new Vector3f(0.0f, 0.0f, 0.02f), 1.8f);
    private static final DustParticleEffect WIND = new DustParticleEffect(new Vector3f(0.92f, 0.97f, 1.0f), 1.5f);

    private static class St {
        boolean drawn;
        boolean wasSwing;
        int combo;
        int slashTicks;          // 1..max while a slash animation / sweep is running, else 0
        int slashType;           // 0 = G horizontal, 1 = M1
        double slashYaw;         // player yaw (radians) when the G slash started
        double slashY;
        Set<Integer> hit = new HashSet<>();
        int throwTimer;          // counts down from THROW_TICKS; katanas leave the hands at THROW_TICKS/2
        int cooldownG, cooldownR;
    }

    private static final Map<UUID, St> STATE = new HashMap<>();

    private static class Katana {
        UUID owner;
        Vec3d pos, dir;
        int mode;                // 0 flying, 1 planted in block, 2 planted in entity, 3 returning
        int ticks;
        Entity victim;
        Vec3d offset = Vec3d.ZERO;
        List<DisplayEntity.ItemDisplayEntity> disp = new ArrayList<>();
        ServerWorld world;
        net.minecraft.nbt.NbtElement itemTag;
    }

    private static final List<Katana> KATANAS = new ArrayList<>();

    private static St st(ServerPlayerEntity p) {
        return STATE.computeIfAbsent(p.getUuid(), k -> new St());
    }

    // ------------------------------------------------------------ sync bits (see HellKingClient for the layout)
    public static int bits(ServerPlayerEntity p) {
        St s = STATE.get(p.getUuid());
        if (s == null) return 0;
        int b = s.drawn ? 32 : 0;
        if (s.slashTicks > 0) b |= (s.slashTicks << 8) | (s.slashType << 12);
        if (s.throwTimer > 0) b |= (Math.min(THROW_TICKS, THROW_TICKS - s.throwTimer + 1) << 13);
        if (away(p)) b |= (1 << 18);
        b |= ((s.combo & 1) << 19);
        return b;
    }

    private static boolean away(ServerPlayerEntity p) {
        for (Katana k : KATANAS) if (k.owner.equals(p.getUuid())) return true;
        return false;
    }

    public static void reset(ServerPlayerEntity p) {
        STATE.remove(p.getUuid());
        for (int i = KATANAS.size() - 1; i >= 0; i--) {
            if (KATANAS.get(i).owner.equals(p.getUuid())) remove(KATANAS.remove(i));
        }
    }

    // ------------------------------------------------------------ transformation visuals
    public static void tickTransforming(ServerPlayerEntity p, int t, ServerWorld w) {
        double ang = t * 0.6;
        for (int i = 0; i < 4; i++) {
            double a = ang + i * Math.PI / 2;
            double r = 2.0 - t * 0.035;
            double x = p.getX() + Math.cos(a) * r, z = p.getZ() + Math.sin(a) * r, y = p.getY() + 0.2 + t * 0.05;
            w.spawnParticles(WIND, x, y, z, 1, 0, 0, 0, 0);
            w.spawnParticles(ParticleTypes.CLOUD, x, y, z, 1, 0.05, 0.05, 0.05, 0.02);
            w.spawnParticles(ParticleTypes.SQUID_INK, x, y + 0.3, z, 1, 0.1, 0.1, 0.1, 0.01);
        }
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 10, 4, true, false, false));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 10, 3, true, false, false));
    }

    public static void finishTransform(ServerPlayerEntity p, ServerWorld w) {
        w.spawnParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 1, p.getZ(), 90, 0.5, 0.9, 0.5, 0.35);
        w.spawnParticles(ParticleTypes.SQUID_INK, p.getX(), p.getY() + 1, p.getZ(), 60, 0.5, 0.9, 0.5, 0.25);
        w.spawnParticles(ParticleTypes.SWEEP_ATTACK, p.getX(), p.getY() + 1, p.getZ(), 4, 0.8, 0.5, 0.8, 0.0);
        w.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_PHANTOM_FLAP, SoundCategory.PLAYERS, 2.0f, 0.6f);
        st(p).drawn = false;
    }

    // ------------------------------------------------------------ B : draw / sheathe
    public static void onB(ServerPlayerEntity p) {
        St s = st(p);
        s.drawn = !s.drawn;
        p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.ITEM_TRIDENT_RETURN, SoundCategory.PLAYERS, 1.2f, s.drawn ? 1.6f : 1.0f);
        ((ServerWorld) p.getWorld()).spawnParticles(ParticleTypes.CRIT, p.getX(), p.getY() + 1.0, p.getZ(), 10, 0.4, 0.3, 0.4, 0.2);
    }

    // ------------------------------------------------------------ G : horizontal slash
    public static void onG(ServerPlayerEntity p) {
        St s = st(p);
        if (s.slashTicks > 0 || s.cooldownG > 0) return;
        s.cooldownG = 30;
        s.slashType = 0;
        s.slashTicks = 1;
        s.slashYaw = Math.toRadians(p.getYaw());
        s.slashY = p.getY();
        s.hit.clear();
        s.drawn = true;
        p.getWorld().playSound(null, p.getBlockPos(), ModSounds.SLASH, SoundCategory.PLAYERS, 2.0f, 0.7f);
    }

    private static void sweepStep(ServerPlayerEntity p, St s, ServerWorld w) {
        int k = s.slashTicks - 1;                                  // 0..7
        double step = 170.0 / SLASH_G_TICKS;
        double a0 = 85.0 - k * step, a1 = a0 - step;               // degrees, right -> left
        boolean grief = w.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING);
        Set<BlockPos> done = new HashSet<>();
        for (double r = 1.0; r <= SLASH_RANGE; r += 0.5) {
            int samples = Math.max(2, (int) Math.ceil(Math.toRadians(step) * r / 0.6));
            for (int i = 0; i <= samples; i++) {
                double a = Math.toRadians(a0 + (a1 - a0) * i / samples);
                double yaw = s.slashYaw + a;
                double x = p.getX() - Math.sin(yaw) * r, z = p.getZ() + Math.cos(yaw) * r;
                for (int dy = 0; dy <= 3; dy++) {
                    BlockPos bp = BlockPos.ofFloored(x, s.slashY + dy, z);
                    if (!done.add(bp)) continue;
                    if (grief) cut(w, p, bp);
                }
                // black blade trail
                if (i == samples) {
                    w.spawnParticles(VOID, x, s.slashY + 1.2, z, 1, 0.1, 0.45, 0.1, 0.0);
                    w.spawnParticles(ParticleTypes.SQUID_INK, x, s.slashY + 1.2, z, 1, 0.2, 0.5, 0.2, 0.02);
                    if (((int) (r * 2)) % 3 == 0) w.spawnParticles(WIND, x, s.slashY + 1.2, z, 1, 0.2, 0.3, 0.2, 0.0);
                }
            }
        }
        double mid = Math.toRadians((a0 + a1) / 2.0) + s.slashYaw;
        for (double r = 2; r <= 8; r += 2) {
            w.spawnParticles(ParticleTypes.SWEEP_ATTACK, p.getX() - Math.sin(mid) * r, s.slashY + 1.2, p.getZ() + Math.cos(mid) * r, 1, 0, 0, 0, 0);
        }

        // creatures inside the current band are cut in two, poisoned and take huge damage
        Box area = new Box(p.getX() - SLASH_RANGE - 1, s.slashY - 1, p.getZ() - SLASH_RANGE - 1, p.getX() + SLASH_RANGE + 1, s.slashY + 4, p.getZ() + SLASH_RANGE + 1);
        for (Entity e : w.getOtherEntities(p, area, en -> en instanceof LivingEntity && en.isAlive())) {
            if (s.hit.contains(e.getId())) continue;
            double dx = e.getX() - p.getX(), dz = e.getZ() - p.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > SLASH_RANGE + 0.8) continue;
            double ang = Math.toDegrees(wrap(Math.atan2(-dx, dz) - s.slashYaw));
            if (ang > a0 + 6 || ang < a1 - 6) continue;
            s.hit.add(e.getId());
            e.damage(w.getDamageSources().playerAttack(p), 60.0f);
            if (e instanceof LivingEntity le) le.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 300, 2));
            cutVfx(w, e);
        }
    }

    /** "Cut in two" look: a black / white / red slice through the body, with the two halves flying apart. */
    private static void cutVfx(ServerWorld w, Entity e) {
        double y = e.getY() + e.getHeight() / 2.0;
        w.spawnParticles(VOID, e.getX(), y, e.getZ(), 14, 0.5, 0.05, 0.5, 0.0);
        w.spawnParticles(ParticleTypes.SWEEP_ATTACK, e.getX(), y, e.getZ(), 3, 0.5, 0.2, 0.5, 0.0);
        w.spawnParticles(ParticleTypes.CRIT, e.getX(), y + 0.3, e.getZ(), 25, 0.4, 0.3, 0.4, 0.5);
        w.spawnParticles(ParticleTypes.DAMAGE_INDICATOR, e.getX(), y, e.getZ(), 10, 0.3, 0.3, 0.3, 0.3);
        w.spawnParticles(new DustParticleEffect(new Vector3f(0.7f, 0.0f, 0.05f), 1.6f), e.getX(), y, e.getZ(), 20, 0.3, 0.4, 0.3, 0.0);
    }

    private static double wrap(double a) {
        while (a > Math.PI) a -= Math.PI * 2;
        while (a < -Math.PI) a += Math.PI * 2;
        return a;
    }

    /** Anything the blade touches is destroyed (logs, leaves, stone ...), except bedrock-like blocks. */
    private static void cut(ServerWorld w, ServerPlayerEntity p, BlockPos bp) {
        BlockState st = w.getBlockState(bp);
        if (st.isAir() || st.isOf(Blocks.BEDROCK) || st.getHardness(w, bp) < 0) return;
        if (st.getHardness(w, bp) > 70f) return;
        if (!w.canPlayerModifyAt(p, bp)) return;
        w.breakBlock(bp, false);
    }

    // ------------------------------------------------------------ M1 : quick slice with a black crescent
    private static void m1(ServerPlayerEntity p, St s, ServerWorld w) {
        s.combo = (s.combo + 1) % 3;
        s.slashType = 1;
        s.slashTicks = 1;
        Vec3d look = p.getRotationVec(1.0f);
        Vec3d f = new Vec3d(look.x, 0, look.z);
        f = f.lengthSquared() < 1.0E-4 ? new Vec3d(0, 0, 1) : f.normalize();
        Vec3d right = f.crossProduct(new Vec3d(0, 1, 0)).normalize();
        Vec3d up = new Vec3d(0, 1, 0);
        double tilt = s.combo == 0 ? -0.55 : (s.combo == 1 ? 0.55 : Math.PI / 2);
        double dirSign = s.combo == 1 ? -1 : 1;
        Vec3d c = p.getEyePos().add(0, -0.3, 0).add(look.multiply(0.4));
        double rad = 2.6;
        for (double t = -1.25; t <= 1.25; t += 0.1) {
            double sp = Math.sin(t) * dirSign;
            Vec3d q = c.add(f.multiply(Math.cos(t) * rad)).add(right.multiply(sp * rad * Math.cos(tilt))).add(up.multiply(sp * rad * Math.sin(tilt)));
            w.spawnParticles(VOID, q.x, q.y, q.z, 1, 0.03, 0.03, 0.03, 0.0);
            w.spawnParticles(VOID_SMALL, q.x, q.y, q.z, 2, 0.08, 0.08, 0.08, 0.0);
            if (((int) (t * 10)) % 3 == 0) w.spawnParticles(WIND, q.x, q.y, q.z, 1, 0.05, 0.05, 0.05, 0.0);
        }
        Vec3d mid = c.add(f.multiply(rad));
        w.spawnParticles(ParticleTypes.SWEEP_ATTACK, mid.x, mid.y, mid.z, 1, 0, 0, 0, 0);
        w.spawnParticles(ParticleTypes.CRIT, mid.x, mid.y, mid.z, 8, 0.6, 0.4, 0.6, 0.3);
        w.playSound(null, p.getBlockPos(), ModSounds.SLASH, SoundCategory.PLAYERS, 1.0f, 1.3f + 0.1f * s.combo);

        // small lunge forward + extra damage in front
        p.addVelocity(f.x * 0.35, 0.0, f.z * 0.35);
        p.velocityModified = true;
        Box box = p.getBoundingBox().expand(3.6, 1.0, 3.6);
        for (Entity e : w.getOtherEntities(p, box, en -> en instanceof LivingEntity && en.isAlive())) {
            Vec3d to = e.getPos().subtract(p.getPos());
            Vec3d tf = new Vec3d(to.x, 0, to.z);
            if (tf.lengthSquared() > 1.0E-4 && tf.normalize().dotProduct(f) < 0.35) continue;
            e.damage(w.getDamageSources().playerAttack(p), 10.0f);
            w.spawnParticles(ParticleTypes.SWEEP_ATTACK, e.getX(), e.getY() + e.getHeight() * 0.6, e.getZ(), 1, 0, 0, 0, 0);
        }
    }

    // ------------------------------------------------------------ R : two katanas
    public static void onR(ServerPlayerEntity p) {
        St s = st(p);
        if (s.cooldownR > 0 || s.throwTimer > 0 || away(p)) return;
        s.cooldownR = 40;
        s.throwTimer = THROW_TICKS;
        s.drawn = true;
        p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.ITEM_TRIDENT_RETURN, SoundCategory.PLAYERS, 1.5f, 1.8f);
    }

    private static void release(ServerPlayerEntity p, ServerWorld w) {
        Vec3d look = p.getRotationVec(1.0f);
        Vec3d eye = p.getEyePos();
        var hit = w.raycast(new RaycastContext(eye, eye.add(look.multiply(70)), RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, p));
        Vec3d target = hit.getType() == HitResult.Type.MISS ? eye.add(look.multiply(70)) : hit.getPos();
        Vec3d f = new Vec3d(look.x, 0, look.z);
        f = f.lengthSquared() < 1.0E-4 ? new Vec3d(0, 0, 1) : f.normalize();
        Vec3d right = f.crossProduct(new Vec3d(0, 1, 0)).normalize();
        for (int side = -1; side <= 1; side += 2) {
            Vec3d from = p.getPos().add(0, 1.2, 0).add(right.multiply(0.45 * side)).add(look.multiply(0.8));
            Vec3d dir = target.subtract(from);
            dir = dir.lengthSquared() < 1.0E-4 ? look : dir.normalize();
            Katana k = new Katana();
            k.owner = p.getUuid();
            k.pos = from;
            k.dir = dir;
            k.world = w;
            for (int i = 0; i < 2; i++) k.disp.add(makeDisplay(w));
            place(k, 0);
            for (DisplayEntity.ItemDisplayEntity d : k.disp) w.spawnEntity(d);
            KATANAS.add(k);
        }
        w.playSound(null, p.getBlockPos(), ModSounds.SLASH, SoundCategory.PLAYERS, 1.8f, 1.6f);
        w.spawnParticles(ParticleTypes.CRIT, p.getX(), p.getY() + 1.2, p.getZ(), 14, 0.5, 0.3, 0.5, 0.3);
        w.spawnParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 1.2, p.getZ(), 10, 0.5, 0.3, 0.5, 0.1);
    }

    // ---- flying / planted / returning katanas
    private static DisplayEntity.ItemDisplayEntity makeDisplay(ServerWorld w) {
        DisplayEntity.ItemDisplayEntity e = EntityType.ITEM_DISPLAY.create(w);
        e.setNoGravity(true);
        return e;
    }

    private static NbtCompound transform(Vec3d dir, float spinDeg, float scale) {
        Vector3f d = new Vector3f((float) dir.x, (float) dir.y, (float) dir.z).normalize();
        Quaternionf q = new Quaternionf().rotationTo(new Vector3f(1, 1, 0).normalize(), d);
        if (spinDeg != 0f) q = new Quaternionf().fromAxisAngleDeg(d, spinDeg).mul(q);
        NbtCompound t = new NbtCompound();
        NbtList l = new NbtList(); l.add(NbtFloat.of(q.x)); l.add(NbtFloat.of(q.y)); l.add(NbtFloat.of(q.z)); l.add(NbtFloat.of(q.w));
        NbtList r = new NbtList(); r.add(NbtFloat.of(0)); r.add(NbtFloat.of(0)); r.add(NbtFloat.of(0)); r.add(NbtFloat.of(1));
        NbtList sc = new NbtList(); sc.add(NbtFloat.of(scale)); sc.add(NbtFloat.of(scale)); sc.add(NbtFloat.of(scale));
        NbtList tr = new NbtList(); tr.add(NbtFloat.of(0)); tr.add(NbtFloat.of(0)); tr.add(NbtFloat.of(0));
        t.put("left_rotation", l); t.put("right_rotation", r); t.put("scale", sc); t.put("translation", tr);
        return t;
    }

    /** Moves the display entities (two crossed sprites, so the blade is visible from every side) to the katana's pose. */
    private static void place(Katana k, float spin) {
        if (k.itemTag == null) k.itemTag = new ItemStack(ModItems.KATANA).encode(k.world.getRegistryManager());
        for (int i = 0; i < k.disp.size(); i++) {
            DisplayEntity.ItemDisplayEntity d = k.disp.get(i);
            NbtCompound n = new NbtCompound();
            n.put("item", k.itemTag.copy());
            n.putString("item_display", "none");
            n.putFloat("view_range", 3.0f);
            NbtCompound br = new NbtCompound();
            br.putInt("block", 15); br.putInt("sky", 15);
            n.put("brightness", br);
            n.put("transformation", transform(k.dir, i * 90f + spin, 2.6f));
            n.putInt("interpolation_duration", 1);
            n.putInt("start_interpolation", 0);
            NbtList pos = new NbtList(); pos.add(NbtDouble.of(k.pos.x)); pos.add(NbtDouble.of(k.pos.y)); pos.add(NbtDouble.of(k.pos.z));
            NbtList mot = new NbtList(); mot.add(NbtDouble.of(0)); mot.add(NbtDouble.of(0)); mot.add(NbtDouble.of(0));
            NbtList rot = new NbtList(); rot.add(NbtFloat.of(0)); rot.add(NbtFloat.of(0));
            n.put("Pos", pos); n.put("Motion", mot); n.put("Rotation", rot);
            d.readNbt(n);
        }
    }

    private static void remove(Katana k) {
        for (DisplayEntity.ItemDisplayEntity d : k.disp) d.discard();
        k.disp.clear();
    }

    private static boolean soft(BlockState s) {
        return s.isIn(BlockTags.DIRT) || s.isIn(BlockTags.SAND) || s.isIn(BlockTags.LEAVES) || s.isIn(BlockTags.SNOW)
                || s.isOf(Blocks.GRAVEL) || s.isOf(Blocks.CLAY) || s.isOf(Blocks.SOUL_SAND) || s.isOf(Blocks.SOUL_SOIL)
                || s.isOf(Blocks.GRASS_BLOCK) || s.isOf(Blocks.DIRT_PATH) || s.isOf(Blocks.FARMLAND)
                || s.isReplaceable();
    }

    public static void tickProjectiles(MinecraftServer server) {
        for (int i = KATANAS.size() - 1; i >= 0; i--) {
            Katana k = KATANAS.get(i);
            ServerPlayerEntity owner = server.getPlayerManager().getPlayer(k.owner);
            if (owner == null || !owner.isAlive() || owner.getWorld() != k.world) {
                remove(k); KATANAS.remove(i); continue;
            }
            ServerWorld w = k.world;
            k.ticks++;
            switch (k.mode) {
                case 0 -> flyKatana(k, owner, w);
                case 1 -> {
                    if (k.pos.distanceTo(owner.getPos().add(0, 1, 0)) <= 4.5) recall(k, owner, w, null);
                }
                case 2 -> {
                    if (k.victim == null || k.victim.isRemoved() || !k.victim.isAlive()) { k.mode = 1; k.victim = null; }
                    else {
                        k.pos = k.victim.getPos().add(k.offset);
                        if (k.pos.distanceTo(owner.getPos().add(0, 1, 0)) <= 4.5) recall(k, owner, w, k.victim);
                    }
                }
                default -> {
                    Vec3d to = owner.getPos().add(0, 1.0, 0).subtract(k.pos);
                    double dist = to.length();
                    if (dist < 1.3) { remove(k); KATANAS.remove(i); w.playSound(null, owner.getBlockPos(), SoundEvents.ITEM_TRIDENT_RETURN, SoundCategory.PLAYERS, 1.0f, 1.8f); continue; }
                    k.dir = to.multiply(1.0 / dist);
                    k.pos = k.pos.add(k.dir.multiply(Math.min(dist, 2.2)));
                    w.spawnParticles(WIND, k.pos.x, k.pos.y, k.pos.z, 1, 0.1, 0.1, 0.1, 0.0);
                }
            }
            if (!k.disp.isEmpty()) place(k, k.mode == 0 || k.mode == 3 ? k.ticks * 40f : 0f);
        }
    }

    private static void recall(Katana k, ServerPlayerEntity owner, ServerWorld w, Entity victim) {
        k.mode = 3;
        k.victim = null;
        Box area = new Box(k.pos.x - 3.5, k.pos.y - 3.5, k.pos.z - 3.5, k.pos.x + 3.5, k.pos.y + 3.5, k.pos.z + 3.5);
        Set<Entity> poisoned = new HashSet<>();
        if (victim != null) poisoned.add(victim);
        poisoned.addAll(w.getOtherEntities(owner, area, en -> en instanceof LivingEntity && en.isAlive()));
        for (Entity e : poisoned) {
            if (e instanceof LivingEntity le) le.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 200, 2));
        }
        w.spawnParticles(ParticleTypes.SQUID_INK, k.pos.x, k.pos.y, k.pos.z, 15, 0.3, 0.3, 0.3, 0.05);
        w.spawnParticles(new DustParticleEffect(new Vector3f(0.4f, 0.9f, 0.2f), 1.6f), k.pos.x, k.pos.y, k.pos.z, 12, 0.4, 0.4, 0.4, 0.0);
        w.playSound(null, BlockPos.ofFloored(k.pos), SoundEvents.ENTITY_PHANTOM_BITE, SoundCategory.PLAYERS, 1.2f, 1.4f);
    }

    private static void flyKatana(Katana k, ServerPlayerEntity owner, ServerWorld w) {
        for (int sub = 0; sub < 4; sub++) {
            Vec3d next = k.pos.add(k.dir.multiply(0.6));
            // a living target: stab it
            Box box = new Box(next.x - 0.7, next.y - 0.7, next.z - 0.7, next.x + 0.7, next.y + 0.7, next.z + 0.7);
            for (Entity e : w.getOtherEntities(owner, box, en -> en instanceof LivingEntity && en.isAlive())) {
                e.damage(w.getDamageSources().playerAttack(owner), 80.0f);
                k.mode = 2;
                k.victim = e;
                k.pos = next;
                k.offset = next.subtract(e.getPos());
                w.spawnParticles(ParticleTypes.CRIT, next.x, next.y, next.z, 20, 0.3, 0.3, 0.3, 0.4);
                w.spawnParticles(VOID, next.x, next.y, next.z, 6, 0.3, 0.3, 0.3, 0.0);
                w.playSound(null, BlockPos.ofFloored(next), SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.6f, 0.8f);
                return;
            }
            BlockPos bp = BlockPos.ofFloored(next);
            BlockState st = w.getBlockState(bp);
            if (!st.isAir() && !st.getCollisionShape(w, bp).isEmpty()) {
                if (soft(st) && st.getHardness(w, bp) >= 0 && w.canPlayerModifyAt(owner, bp)
                        && w.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)) {
                    w.breakBlock(bp, false);                // dirt, grass, sand, gravel, leaves ... are cut away
                    w.spawnParticles(ParticleTypes.SQUID_INK, next.x, next.y, next.z, 3, 0.2, 0.2, 0.2, 0.02);
                } else {
                    k.mode = 1;                             // solid block: plant the katana in the face it hit
                    k.pos = next.add(k.dir.multiply(0.2));
                    w.spawnParticles(ParticleTypes.CRIT, next.x, next.y, next.z, 14, 0.2, 0.2, 0.2, 0.3);
                    w.spawnParticles(VOID, next.x, next.y, next.z, 4, 0.2, 0.2, 0.2, 0.0);
                    w.playSound(null, bp, SoundEvents.ENTITY_ARROW_HIT, SoundCategory.PLAYERS, 1.8f, 0.6f);
                    return;
                }
            }
            k.pos = next;
            w.spawnParticles(VOID_SMALL, next.x, next.y, next.z, 1, 0.05, 0.05, 0.05, 0.0);
            w.spawnParticles(WIND, next.x, next.y, next.z, 1, 0.1, 0.1, 0.1, 0.0);
        }
        if (k.ticks > 30) k.mode = 3;                       // missed everything: come back
    }

    // ------------------------------------------------------------ per-player tick (Wind Assassin, transformed)
    public static void tick(ServerPlayerEntity p, ServerWorld w) {
        St s = st(p);
        if (s.cooldownG > 0) s.cooldownG--;
        if (s.cooldownR > 0) s.cooldownR--;

        // M1: new swing with the katana in hand
        boolean sw = p.handSwinging;
        if (sw && !s.wasSwing && s.slashTicks == 0 && p.getMainHandStack().isOf(ModItems.KATANA)) m1(p, s, w);
        s.wasSwing = sw;

        if (s.slashTicks > 0) {
            if (s.slashType == 0) sweepStep(p, s, w);
            int max = s.slashType == 0 ? SLASH_G_TICKS : SLASH_M1_TICKS;
            if (++s.slashTicks > max) s.slashTicks = 0;
        }

        if (s.throwTimer > 0) {
            if (s.throwTimer == THROW_TICKS / 2) release(p, w);
            s.throwTimer--;
        }

        // wind aura + speed
        if (p.age % 5 == 0) {
            w.spawnParticles(WIND, p.getX(), p.getY() + 0.15, p.getZ(), 2, 0.5, 0.05, 0.5, 0.0);
            w.spawnParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 1, 0.4, 0.05, 0.4, 0.01);
        }
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 40, 2, true, false, false));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 40, 1, true, false, false));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 40, 1, true, false, false));
    }
}
