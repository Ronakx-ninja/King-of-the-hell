package com.hellking.client;

import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.render.entity.model.EntityModelLoader;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/** Wind Assassin: glowing white eyes over the void-black skin, two katanas with sheaths on the waist, drawn blade in the off hand. */
public class AssassinFeatureRenderer extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    public static final EntityModelLayer GEAR_LAYER = new EntityModelLayer(Identifier.of("hellking", "assassin_gear"), "main");

    private static final Identifier EYES = Identifier.of("hellking", "textures/entity/assassin_eyes_glow.png");
    private static final Identifier GEAR_TEX = Identifier.of("hellking", "textures/entity/assassin_gear.png");

    private final PlayerEntityModel<AbstractClientPlayerEntity> eyeModel;
    private final ModelPart waist, hiltL, hiltR, bladeL;

    public AssassinFeatureRenderer(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> ctx, EntityModelLoader loader) {
        super(ctx);
        this.eyeModel = new PlayerEntityModel<>(loader.getModelPart(HellKingFeatureRenderer.ARMOR_LAYER), false);
        ModelPart gear = loader.getModelPart(GEAR_LAYER);
        this.waist = gear.getChild("waist");
        this.hiltL = waist.getChild("kat_l").getChild("hilt");
        this.hiltR = waist.getChild("kat_r").getChild("hilt");
        this.bladeL = gear.getChild("blade_l");
    }

    // ------------------------------------------------------------------ model data (texture 64x32)
    public static TexturedModelData createGearLayer() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();

        ModelPartData waist = root.addChild("waist", ModelPartBuilder.create(), ModelTransform.NONE);
        for (int side = -1; side <= 1; side += 2) {
            // pivot on the hip; the sheath hangs down and tilts back, the hilt sticks out forward-up
            ModelPartData k = waist.addChild(side < 0 ? "kat_r" : "kat_l", ModelPartBuilder.create(),
                    ModelTransform.of(side * 4.3f, 10.0f, -1.0f, 1.15f, 0f, side * -0.12f));
            k.addChild("sheath", ModelPartBuilder.create().uv(32, 0).cuboid(-0.7f, 0f, -0.7f, 1.4f, 13f, 1.4f), ModelTransform.NONE);
            ModelPartData hilt = k.addChild("hilt", ModelPartBuilder.create().uv(40, 0).cuboid(-0.6f, -4.5f, -0.6f, 1.2f, 4.5f, 1.2f), ModelTransform.NONE);
            hilt.addChild("tsuba", ModelPartBuilder.create().uv(48, 0).cuboid(-1.4f, -0.4f, -1.4f, 2.8f, 0.5f, 2.8f), ModelTransform.NONE);
        }

        // drawn katana held in the left hand (attached to the left arm; hangs from the fist, pointing forward)
        ModelPartData bl = root.addChild("blade_l", ModelPartBuilder.create(), ModelTransform.NONE);
        bl.addChild("grip", ModelPartBuilder.create().uv(0, 16).cuboid(-0.55f, 9.0f, -1.2f, 1.1f, 1.1f, 3.6f), ModelTransform.NONE);
        bl.addChild("guard", ModelPartBuilder.create().uv(48, 0).cuboid(-1.2f, 8.6f, -1.6f, 2.4f, 1.9f, 0.4f), ModelTransform.NONE);
        bl.addChild("blade", ModelPartBuilder.create().uv(0, 0).cuboid(-0.3f, 9.1f, -15.5f, 0.6f, 1.2f, 14f), ModelTransform.NONE);
        return TexturedModelData.of(data, 64, 32);
    }

    // ------------------------------------------------------------------ rendering
    @Override
    public void render(MatrixStack m, VertexConsumerProvider vcp, int light, AbstractClientPlayerEntity p,
                       float limbAngle, float limbDistance, float tickDelta, float animationProgress,
                       float headYaw, float headPitch) {
        if (HellKingClient.stateOf(p) != 2 || HellKingClient.formOf(p) != 2 || p.isInvisible()) return;

        PlayerEntityModel<AbstractClientPlayerEntity> ctx = getContextModel();
        ctx.copyStateTo(eyeModel);
        eyeModel.head.copyTransform(ctx.head);
        eyeModel.body.copyTransform(ctx.body);
        eyeModel.rightArm.copyTransform(ctx.rightArm);
        eyeModel.leftArm.copyTransform(ctx.leftArm);
        eyeModel.rightLeg.copyTransform(ctx.rightLeg);
        eyeModel.leftLeg.copyTransform(ctx.leftLeg);
        eyeModel.setVisible(true);
        eyeModel.hat.visible = false; eyeModel.jacket.visible = false;
        eyeModel.leftSleeve.visible = false; eyeModel.rightSleeve.visible = false;
        eyeModel.leftPants.visible = false; eyeModel.rightPants.visible = false;

        int full = LightmapTextureManager.MAX_LIGHT_COORDINATE;
        int ov = OverlayTexture.DEFAULT_UV;

        // pure white glowing eyes (full bright, ignores light level)
        eyeModel.render(m, vcp.getBuffer(RenderLayer.getEyes(EYES)), full, ov, -1);

        boolean drawn = HellKingClient.isDrawn(p);
        boolean away = HellKingClient.katanasAway(p);
        hiltL.visible = !drawn && !away;
        hiltR.visible = !drawn && !away;
        bladeL.visible = drawn && !away;

        m.push();
        ctx.body.rotate(m);
        waist.render(m, vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(GEAR_TEX)), light, ov, -1);
        m.pop();

        if (bladeL.visible) {
            m.push();
            ctx.leftArm.rotate(m);
            bladeL.render(m, vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(GEAR_TEX)), light, ov, -1);
            m.pop();
        }
    }
}
