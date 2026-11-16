package com.hellking.mixin;

import com.hellking.client.HellKingClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** While transformed, the Hell King / Wind Assassin skins replace the player's own skin. */
@Mixin(PlayerEntityRenderer.class)
public class PlayerSkinMixin {
    private static final Identifier HELL_SKIN = Identifier.of("hellking", "textures/entity/hell_skin.png");
    private static final Identifier ASSASSIN_SKIN = Identifier.of("hellking", "textures/entity/assassin_skin.png");

    @Inject(method = "getTexture(Lnet/minecraft/client/network/AbstractClientPlayerEntity;)Lnet/minecraft/util/Identifier;",
            at = @At("HEAD"), cancellable = true)
    private void hellking$skin(AbstractClientPlayerEntity player, CallbackInfoReturnable<Identifier> cir) {
        if (HellKingClient.stateOf(player) != 2) return;
        int form = HellKingClient.formOf(player);
        if (form == 0) cir.setReturnValue(HELL_SKIN);
        else if (form == 2) cir.setReturnValue(ASSASSIN_SKIN);
    }
}
