package com.hellking.client;

import net.minecraft.client.model.Dilation;
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

/** Draws the Hell King transformation: dark-lava armour, glowing cracks/eyes, crown + horns, shoulder spikes, flowing cape. */
public class HellKingFeatureRenderer extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    public static final EntityModelLayer ARMOR_LAYER = new EntityModelLayer(Identifier.of("hellking", "armor"), "main");
    public static final EntityModelLayer GEAR_LAYER = new EntityModelLayer(Identifier.of("hellking", "gear"), "main");

    private static final Identifier ARMOR_TEX = Identifier.of("hellking", "textures/entity/hell_armor.png");
    private static final Identifier ARMOR_GLOW = Identifier.of("hellking", "textures/entity/hell_armor_glow.png");
    private static final Identifier GEAR_TEX = Identifier.of("hellking", "textures/entity/hell_gear.png");
    private static final Identifier GEAR_GLOW = Identifier.of("hellking", "textures/entity/hell_gear_glow.png");


    private final PlayerEntityModel<AbstractClientPlayerEntity> armorModel;
    private final ModelPart horns, shoulderL, shoulderR, cape, capeUp, capeMid, capeLow;

    public HellKingFeatureRenderer(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> ctx, EntityModelLoader loader) {
        super(ctx);
        this.armorModel = new PlayerEntityModel<>(loader.getModelPart(ARMOR_LAYER), false);
        ModelPart gear = loader.getModelPart(GEAR_LAYER);
        this.horns = gear.getChild("horns");
        this.shoulderL = gear.getChild("shoulder_l");
        this.shoulderR = gear.getChild("shoulder_r");
        this.cape = gear.getChild("cape");
        this.capeUp = cape.getChild("up");
        this.capeMid = capeUp.getChild("mid");
        this.capeLow = capeMid.getChild("low");
    }

    // ------------------------------------------------------------------ model data
    public static TexturedModelData createArmorLayer() {
        return TexturedModelData.of(PlayerEntityModel.getTexturedModelData(new Dilation(0.55f), false), 64, 64);
    }

    public static TexturedModelData createGearLayer() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();

        // ---- exactly two horns (attached to head)
        ModelPartData horns = root.addChild("horns", ModelPartBuilder.create(), ModelTransform.NONE);
        horn(horns, "horn_l", 1f);
        horn(horns, "horn_r", -1f);

        // ---- small pauldrons with a few spikes (attached to arms)
        shoulder(root, "shoulder_r", -1f);
        shoulder(root, "shoulder_l", 1f);

        // ---- ragged cloak (attached to body); shape (tatters, holes, flare) comes from the texture's transparency
        ModelPartData cape = root.addChild("cape", ModelPartBuilder.create(), ModelTransform.NONE);
        ModelPartData up = cape.addChild("up",
                ModelPartBuilder.create().uv(0, 0).cuboid(-5f, 0f, 0f, 10f, 6f, 1f),
                ModelTransform.of(0f, 0.2f, 2.9f, 0.08f, 0f, 0f));
        ModelPartData mid = up.addChild("mid",
                ModelPartBuilder.create().uv(0, 8).cuboid(-8f, 0f, 0f, 16f, 8f, 1f),
                ModelTransform.pivot(0f, 5.8f, 0f));
        mid.addChild("low",
                ModelPartBuilder.create().uv(0, 18).cuboid(-12f, 0f, 0f, 24f, 14f, 1f),
                ModelTransform.pivot(0f, 7.8f, 0f));
        return TexturedModelData.of(data, 64, 64);
    }

    private static void horn(ModelPartData parent, String name, float side) {
        float[] w = {2.4f, 2.0f, 1.6f, 1.2f, 0.9f};
        float[] h = {2.2f, 2.2f, 2.0f, 1.8f, 1.6f};
        float[] roll = {0.70f, -0.10f, -0.40f, -0.60f, -0.70f};
        ModelPartData cur = parent;
        for (int i = 0; i < 5; i++) {
            float py = i == 0 ? -7.6f : -h[i - 1] + 0.4f;
            float px = i == 0 ? side * 3.6f : 0f;
            cur = cur.addChild(name + i,
                    ModelPartBuilder.create().uv(46, 0).cuboid(-w[i] / 2f, -h[i], -w[i] / 2f, w[i], h[i], w[i]),
                    ModelTransform.of(px, py, 0f, 0f, 0f, side * roll[i]));
        }
    }

    private static void shoulder(ModelPartData root, String name, float side) {
        ModelPartData sh = root.addChild(name, ModelPartBuilder.create(), ModelTransform.NONE);
        sh.addChild("pad", ModelPartBuilder.create().uv(32, 48).cuboid(side > 0 ? -2.4f : -5.6f, -3.6f, -3.4f, 8f, 2f, 6.8f), ModelTransform.NONE);
        float[] h = {3f, 4.5f, 3f};
        for (int i = 0; i < 3; i++) {
            sh.addChild("sp" + i, ModelPartBuilder.create().uv(46, 0).cuboid(-0.8f, -h[i], -0.8f, 1.6f, h[i], 1.6f),
                    ModelTransform.of(side * 2.2f, -3.4f, -2.0f + i * 2.0f, (i - 1) * 0.12f, 0f, side * 0.55f));
        }
    }

    // ------------------------------------------------------------------ rendering
    @Override
    public void render(MatrixStack m, VertexConsumerProvider vcp, int light, AbstractClientPlayerEntity p,
                       float limbAngle, float limbDistance, float tickDelta, float animationProgress,
                       float headYaw, float headPitch) {
        int state = HellKingClient.stateOf(p);
        if (state == 0 || p.isInvisible()) return;

        PlayerEntityModel<AbstractClientPlayerEntity> ctx = getContextModel();
        ctx.copyStateTo(armorModel);
        // copy the live pose (walking, arm swing, head turn, sneaking) onto the armour model
        armorModel.head.copyTransform(ctx.head);
        armorModel.body.copyTransform(ctx.body);
        armorModel.rightArm.copyTransform(ctx.rightArm);
        armorModel.leftArm.copyTransform(ctx.leftArm);
        armorModel.rightLeg.copyTransform(ctx.rightLeg);
        armorModel.leftLeg.copyTransform(ctx.leftLeg);
        armorModel.sneaking = ctx.sneaking;
        armorModel.setVisible(true);
        armorModel.hat.visible = false;
        armorModel.jacket.visible = false;
        armorModel.leftSleeve.visible = false;
        armorModel.rightSleeve.visible = false;
        armorModel.leftPants.visible = false;
        armorModel.rightPants.visible = false;

        int full = LightmapTextureManager.MAX_LIGHT_COORDINATE;
        int ov = OverlayTexture.DEFAULT_UV;

        if (state == 1) {
            // charging up: only the glowing lava veins fade in
            float pulse = 0.35f + 0.35f * (float) Math.sin((p.age + tickDelta) * 0.5f);
            int c = ((int) (pulse * 255) << 24) | 0xFFFFFF;
            armorModel.render(m, vcp.getBuffer(RenderLayer.getEyes(ARMOR_GLOW)), full, ov, c);
            return;
        }

        armorModel.render(m, vcp.getBuffer(RenderLayer.getArmorCutoutNoCull(ARMOR_TEX)), light, ov, -1);
        armorModel.render(m, vcp.getBuffer(RenderLayer.getEyes(ARMOR_GLOW)), full, ov, -1);

        // ---- animate cape
        float t = p.age + tickDelta;
        boolean flying = HellKingClient.isFlying(p);
        float lean = flying ? 0.05f : 0.12f + Math.min(limbDistance, 1f) * 0.55f;
        capeUp.pitch = 0.10f + lean * 0.55f + (float) Math.sin(t * 0.12f) * 0.03f;
        capeMid.pitch = lean * 0.35f + (float) Math.sin(t * 0.13f + 0.5f) * 0.04f;
        capeLow.pitch = lean * 0.5f + (float) Math.sin(t * 0.14f + 1.0f) * 0.07f + (float) Math.sin(t * 0.33f) * 0.025f;

        drawPart(m, ctx.head, horns, vcp, light, full, ov);
        drawPart(m, ctx.rightArm, shoulderR, vcp, light, full, ov);
        drawPart(m, ctx.leftArm, shoulderL, vcp, light, full, ov);
        drawPart(m, ctx.body, cape, vcp, light, full, ov);
    }

    private static void drawPart(MatrixStack m, ModelPart bone, ModelPart gear, VertexConsumerProvider vcp,
                                 int light, int full, int ov) {
        m.push();
        bone.rotate(m);
        // IMPORTANT: ask for a fresh buffer every time. Holding one buffer while requesting another
        // closes the first one and crashes with "Not building!".
        gear.render(m, vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(GEAR_TEX)), light, ov, -1);
        gear.render(m, vcp.getBuffer(RenderLayer.getEyes(GEAR_GLOW)), full, ov, -1);
        m.pop();
    }
}
