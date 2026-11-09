package com.hellking.mixin;

import com.hellking.client.HellKingClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Superman flight: tilts the whole player horizontally and points the body where the player looks. */
@Mixin(PlayerEntityRenderer.class)
public class PlayerRendererMixin {
    @Inject(method = "setupTransforms(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/util/math/MatrixStack;FFFF)V", at = @At("TAIL"))
    private void hellking$superman(AbstractClientPlayerEntity player, MatrixStack matrices, float animationProgress,
                                   float bodyYaw, float tickDelta, float scale, CallbackInfo ci) {
        if (HellKingClient.stateOf(player) != 2 || !HellKingClient.isFlying(player) || player.isFallFlying()) return;
        float pitch = MathHelper.clamp(player.getPitch(tickDelta), -60.0f, 60.0f);
        matrices.translate(0.0f, 0.9f, 0.0f);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90.0f - pitch));
        matrices.translate(0.0f, -0.9f, 0.0f);
    }
}
