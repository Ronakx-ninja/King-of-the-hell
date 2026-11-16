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
        if (HellKingClient.stateOf(entity) != 2) return;
        float aim = -1.5707f + headPitch * 0.017453292f;      // arm points where the player looks
        if (HellKingClient.formOf(entity) == 2 && !HellKingClient.isFlying(entity)) {
            hellking$assassinPose(entity, aim);
            hellking$syncOverlays();
            return;
        }
        if (!HellKingClient.isFlying(entity)) {
            if (HellKingClient.isCasting(entity)) {           // empty (left) hand thrust forward to throw hellfire
                this.leftArm.pitch = aim;
                this.leftArm.yaw = 0.0f;
                this.leftArm.roll = 0.0f;
            }
            if (HellKingClient.isBeaming(entity)) {           // scythe (right) hand pushed forward while the beam fires
                this.rightArm.pitch = aim;
                this.rightArm.yaw = 0.0f;
                this.rightArm.roll = 0.0f;
            }
            hellking$syncOverlays();
            return;
        }
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
        this.rightLeg.roll = 0.04f;
        this.leftLeg.roll = -0.04f;
        hellking$syncOverlays();
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    /** Wind Assassin: M1 slices, horizontal G slash, crossed-arms R throw and the drawn-blade stance. */
    private void hellking$assassinPose(T entity, float aim) {
        int slash = HellKingClient.slashProgress(entity);
        int type = HellKingClient.slashType(entity);
        int throwP = HellKingClient.throwProgress(entity);
        if (throwP > 0) {
            if (throwP <= 10) {                       // both katanas are pulled out and crossed in front of the chest
                float t = throwP / 10.0f;
                this.rightArm.pitch = lerp(0f, -1.25f, t); this.rightArm.yaw = lerp(0f, -1.0f, t);
                this.leftArm.pitch = lerp(0f, -1.25f, t);  this.leftArm.yaw = lerp(0f, 1.0f, t);
                this.body.yaw = lerp(0f, 0.25f, t);
            } else {                                  // ...and thrown forward with force
                float t = Math.min(1f, (throwP - 10) / 5.0f);
                this.rightArm.pitch = lerp(-1.25f, aim, t); this.rightArm.yaw = lerp(-1.0f, -0.12f, t);
                this.leftArm.pitch = lerp(-1.25f, aim, t);  this.leftArm.yaw = lerp(1.0f, 0.12f, t);
                this.body.yaw = lerp(0.25f, -0.2f, t);
            }
            this.rightArm.roll = 0f; this.leftArm.roll = 0f;
        } else if (slash > 0 && type == 0) {          // G: wide horizontal samurai slash, right to left
            float t = (slash - 1) / 7.0f;
            this.rightArm.pitch = aim;
            this.rightArm.yaw = lerp(1.35f, -1.35f, t);
            this.rightArm.roll = 0f;
            this.leftArm.pitch = -0.5f; this.leftArm.yaw = 0.9f;
            this.body.yaw = lerp(-0.5f, 0.55f, t) * -1f;
        } else if (slash > 0) {                       // M1: quick diagonal / overhead cuts, alternating
            float t = (slash - 1) / 4.0f;
            if (HellKingClient.comboParity(entity) == 0) {
                this.rightArm.pitch = lerp(-2.7f, -0.7f, t); this.rightArm.yaw = lerp(0.5f, -0.6f, t);
            } else {
                this.rightArm.pitch = lerp(-1.4f, -1.2f, t); this.rightArm.yaw = lerp(-0.95f, 0.9f, t);
            }
            this.rightArm.roll = 0f;
            this.body.yaw = lerp(0.25f, -0.3f, t);
        } else if (HellKingClient.isDrawn(entity)) { // ready stance with the blades out
            this.rightArm.pitch = -0.55f; this.rightArm.yaw = -0.25f;
            this.leftArm.pitch = -0.7f; this.leftArm.yaw = 0.35f;
        }
    }

    /** The second skin layer (pants, sleeves, jacket, hat) was copied before our pose, so it still "walked in the air". */
    private void hellking$syncOverlays() {
        PlayerEntityModel<?> pm = (PlayerEntityModel<?>) (Object) this;
        pm.leftPants.copyTransform(pm.leftLeg);
        pm.rightPants.copyTransform(pm.rightLeg);
        pm.leftSleeve.copyTransform(pm.leftArm);
        pm.rightSleeve.copyTransform(pm.rightArm);
        pm.jacket.copyTransform(pm.body);
        pm.hat.copyTransform(pm.head);
    }
}
