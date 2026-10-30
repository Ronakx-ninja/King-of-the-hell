package com.hellking;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public class ModSounds {
    public static final SoundEvent TRANSFORM = reg("transform");
    public static final SoundEvent FIREBALL = reg("fireball");
    public static final SoundEvent BEAM = reg("beam");
    public static final SoundEvent FLY = reg("fly");

    private static SoundEvent reg(String name) {
        Identifier id = Identifier.of(HellKingMod.MOD_ID, name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    public static void init() {}
}
