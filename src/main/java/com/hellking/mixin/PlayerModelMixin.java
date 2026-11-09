package com.hellking.mixin;

import com.hellking.client.HellKingClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Superman pose: arms stretched forward, legs straight, head looking along the flight direction. */
@Mixin(PlayerEntityModel.class)
public abstract class PlayerModelMixin<T extends LivingEntity> extends BipedEntityModel<T> {
    public PlayerModelMixin(ModelPart root) {
        super(root);
    }

    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void hellking$supermanPose(T entity, float limbAngle, float limbDistance, float animationProgress,
                                       float headYaw, float headPitch, CallbackInfo ci) {
        if (HellKingClient.stateOf(entity) != 2 || !HellKingClient.isFlying(entity)) return;
        this.head.pitch = -1.45f;
        this.head.yaw = 0f;
        this.body.pitch = 0f;
        this.rightArm.pitch = -3.05f;
        this.leftArm.pitch = -3.05f;
        this.rightArm.yaw = 0f;
        this.leftArm.yaw = 0f;
        this.rightArm.roll = 0.12f;
        this.leftArm.roll = -0.12f;
        this.rightLeg.pitch = 0.05f;
        this.leftLeg.pitch = 0.05f;
        this.rightLeg.yaw = 0f;
        this.leftLeg.yaw = 0f;
    }
}
