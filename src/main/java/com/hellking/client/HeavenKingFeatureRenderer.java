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

/** Heaven King: silver-ice armour, glowing blue eyes, crown + halo, flapping eagle wings, flowing light cape. */
public class HeavenKingFeatureRenderer extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    public static final EntityModelLayer ARMOR_LAYER = new EntityModelLayer(Identifier.of("hellking", "heaven_armor"), "main");
    public static final EntityModelLayer GEAR_LAYER = new EntityModelLayer(Identifier.of("hellking", "heaven_gear"), "main");

    private static final Identifier ARMOR_TEX = Identifier.of("hellking", "textures/entity/heaven_armor.png");
    private static final Identifier ARMOR_GLOW = Identifier.of("hellking", "textures/entity/heaven_armor_glow.png");
    private static final Identifier GEAR_TEX = Identifier.of("hellking", "textures/entity/heaven_gear.png");
    private static final Identifier GEAR_GLOW = Identifier.of("hellking", "textures/entity/heaven_gear_glow.png");

    private final PlayerEntityModel<AbstractClientPlayerEntity> armorModel;
    private final ModelPart crown, halo, shoulderL, shoulderR, cape, capeUp, capeMid, capeLow;
    private final ModelPart wingL, wingR, outerL, outerR;

    public HeavenKingFeatureRenderer(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> ctx, EntityModelLoader loader) {
        super(ctx);
        this.armorModel = new PlayerEntityModel<>(loader.getModelPart(ARMOR_LAYER), false);
        ModelPart gear = loader.getModelPart(GEAR_LAYER);
        this.crown = gear.getChild("crown");
        this.halo = gear.getChild("halo");
        this.shoulderL = gear.getChild("shoulder_l");
        this.shoulderR = gear.getChild("shoulder_r");
        this.cape = gear.getChild("cape");
        this.capeUp = cape.getChild("up");
        this.capeMid = capeUp.getChild("mid");
        this.capeLow = capeMid.getChild("low");
        this.wingL = gear.getChild("wing_l");
        this.wingR = gear.getChild("wing_r");
        this.outerL = wingL.getChild("outer");
        this.outerR = wingR.getChild("outer");
    }

    // ------------------------------------------------------------------ model data
    public static TexturedModelData createArmorLayer() {
        return TexturedModelData.of(PlayerEntityModel.getTexturedModelData(new Dilation(0.55f), false), 64, 64);
    }

    public static TexturedModelData createGearLayer() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();

        // ---- crown (attached to head): ring + 5 spikes, silver
        ModelPartData crown = root.addChild("crown", ModelPartBuilder.create(), ModelTransform.NONE);
        crown.addChild("ring_f", ModelPartBuilder.create().uv(0, 8).cuboid(-4.8f, -9.6f, -4.8f, 9.6f, 2.0f, 1.2f), ModelTransform.NONE);
        crown.addChild("ring_b", ModelPartBuilder.create().uv(0, 8).cuboid(-4.8f, -9.6f, 3.6f, 9.6f, 2.0f, 1.2f), ModelTransform.NONE);
        crown.addChild("ring_l", ModelPartBuilder.create().uv(0, 8).cuboid(-4.8f, -9.6f, -3.6f, 1.2f, 2.0f, 7.2f), ModelTransform.NONE);
        crown.addChild("ring_r", ModelPartBuilder.create().uv(0, 8).cuboid(3.6f, -9.6f, -3.6f, 1.2f, 2.0f, 7.2f), ModelTransform.NONE);
        float[][] sp = {{0f, -4.2f, 5.5f}, {-2.5f, -4.2f, 3.8f}, {2.5f, -4.2f, 3.8f}, {-4.2f, 0f, 3.2f}, {4.2f, 0f, 3.2f}};
        for (int i = 0; i < sp.length; i++) {
            crown.addChild("sp" + i, ModelPartBuilder.create().uv(0, 8).cuboid(-0.8f, -sp[i][2], -0.8f, 1.6f, sp[i][2], 1.6f),
                    ModelTransform.pivot(sp[i][0], -9.4f, sp[i][1]));
        }
        crown.addChild("gem", ModelPartBuilder.create().uv(0, 56).cuboid(-1.0f, -9.2f, -5.2f, 2.0f, 2.0f, 0.6f), ModelTransform.NONE);

        // ---- halo floating above the head (attached to head), pure glow
        ModelPartData halo = root.addChild("halo", ModelPartBuilder.create(), ModelTransform.pivot(0f, -15.5f, 0f));
        int n = 18;
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            halo.addChild("h" + i, ModelPartBuilder.create().uv(0, 56).cuboid(-0.9f, -0.3f, -1.2f, 1.8f, 0.6f, 2.4f),
                    ModelTransform.of((float) Math.cos(a) * 6.5f, 0f, (float) Math.sin(a) * 6.5f, 0f, (float) -a, 0f));
        }

        // ---- silver pauldrons (attached to arms)
        shoulder(root, "shoulder_r", -1f);
        shoulder(root, "shoulder_l", 1f);

        // ---- light cape (attached to body)
        ModelPartData cape = root.addChild("cape", ModelPartBuilder.create(), ModelTransform.NONE);
        ModelPartData up = cape.addChild("up",
                ModelPartBuilder.create().uv(0, 22).cuboid(-5f, 0f, 0f, 10f, 6f, 1f),
                ModelTransform.of(0f, 0.2f, 2.9f, 0.08f, 0f, 0f));
        ModelPartData mid = up.addChild("mid",
                ModelPartBuilder.create().uv(0, 30).cuboid(-8f, 0f, 0f, 16f, 8f, 1f),
                ModelTransform.pivot(0f, 5.8f, 0f));
        mid.addChild("low",
                ModelPartBuilder.create().uv(0, 40).cuboid(-12f, 0f, 0f, 24f, 14f, 1f),
                ModelTransform.pivot(0f, 7.8f, 0f));

        // ---- wings (attached to body): inner arm + feathers, and an outer segment with long primaries
        wing(root, "wing_l", 1f);
        wing(root, "wing_r", -1f);
        return TexturedModelData.of(data, 64, 64);
    }

    private static void shoulder(ModelPartData root, String name, float side) {
        ModelPartData sh = root.addChild(name, ModelPartBuilder.create(), ModelTransform.NONE);
        sh.addChild("pad", ModelPartBuilder.create().uv(0, 8).cuboid(side > 0 ? -2.4f : -5.6f, -3.6f, -3.4f, 8f, 2f, 6.8f), ModelTransform.NONE);
        sh.addChild("gem", ModelPartBuilder.create().uv(0, 56).cuboid(side > 0 ? 1.0f : -3.0f, -4.2f, -0.8f, 2f, 1f, 1.6f), ModelTransform.NONE);
    }

    private static void wing(ModelPartData root, String name, float side) {
        ModelPartData w = root.addChild(name, ModelPartBuilder.create(), ModelTransform.pivot(side * 2.5f, 2.0f, 2.8f));
        // inner arm bone
        w.addChild("bone", ModelPartBuilder.create().uv(0, 0).cuboid(side > 0 ? 0f : -9f, -0.6f, -0.9f, 9f, 1.2f, 1.8f), ModelTransform.NONE);
        // inner (secondary) feathers: short, fanned backwards
        for (int i = 0; i < 5; i++) {
            float len = 9.5f + i * 0.5f;
            w.addChild("a" + i, ModelPartBuilder.create().uv(0, 0).cuboid(side > 0 ? 0f : -len, -0.3f, -1.2f, len, 0.6f, 2.4f),
                    ModelTransform.of(side * (1.5f + i * 1.7f), 0.1f, 0.6f, 0f, side * (0.30f + i * 0.17f), 0f));
        }
        // outer segment (hand): pivots at the wrist, long primaries
        ModelPartData o = w.addChild("outer", ModelPartBuilder.create().uv(0, 0).cuboid(side > 0 ? 0f : -7f, -0.6f, -0.9f, 7f, 1.2f, 1.8f),
                ModelTransform.pivot(side * 9f, 0f, 0f));
        for (int i = 0; i < 6; i++) {
            float len = 15f - Math.abs(i - 1.5f) * 1.2f;
            o.addChild("p" + i, ModelPartBuilder.create().uv(0, 0).cuboid(side > 0 ? 0f : -len, -0.3f, -1.2f, len, 0.6f, 2.4f),
                    ModelTransform.of(side * 1.0f, 0f, -0.4f + i * 0.5f, 0f, side * (-0.12f + i * 0.17f), 0f));
        }
    }

    // ------------------------------------------------------------------ rendering
    @Override
    public void render(MatrixStack m, VertexConsumerProvider vcp, int light, AbstractClientPlayerEntity p,
                       float limbAngle, float limbDistance, float tickDelta, float animationProgress,
                       float headYaw, float headPitch) {
        int state = HellKingClient.stateOf(p);
        if (state == 0 || p.isInvisible() || HellKingClient.formOf(p) != 1) return;

        PlayerEntityModel<AbstractClientPlayerEntity> ctx = getContextModel();
        ctx.copyStateTo(armorModel);
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
            float pulse = 0.35f + 0.35f * (float) Math.sin((p.age + tickDelta) * 0.5f);
            int c = ((int) (pulse * 255) << 24) | 0xFFFFFF;
            armorModel.render(m, vcp.getBuffer(RenderLayer.getEyes(ARMOR_GLOW)), full, ov, c);
            return;
        }

        armorModel.render(m, vcp.getBuffer(RenderLayer.getArmorCutoutNoCull(ARMOR_TEX)), light, ov, -1);
        armorModel.render(m, vcp.getBuffer(RenderLayer.getEyes(ARMOR_GLOW)), full, ov, -1);

        float t = p.age + tickDelta;
        boolean flying = HellKingClient.isFlying(p);

        // ---- eagle wings: slow powerful flaps in flight (outer part lags behind the inner arm), gentle breathing on the ground
        float speed = flying ? 0.20f : 0.07f;
        float amp = flying ? 0.75f : 0.10f;
        float base = flying ? 0.10f : 0.75f;
        float ph = t * speed;
        float flap = (float) Math.sin(ph) * amp;
        float bend = (float) Math.sin(ph - 0.9f) * (flying ? 0.65f : 0.12f);
        wingL.roll = -(base + flap);
        wingR.roll = base + flap;
        outerL.roll = -(bend + (flying ? 0.15f : 0.35f));
        outerR.roll = bend + (flying ? 0.15f : 0.35f);
        wingL.yaw = flying ? -0.25f : -0.12f;
        wingR.yaw = flying ? 0.25f : 0.12f;
        outerL.yaw = (float) Math.sin(ph - 1.4f) * 0.12f;
        outerR.yaw = -(float) Math.sin(ph - 1.4f) * 0.12f;

        // ---- cape
        float lean = flying ? 0.05f : 0.12f + Math.min(limbDistance, 1f) * 0.55f;
        capeUp.pitch = 0.10f + lean * 0.55f + (float) Math.sin(t * 0.12f) * 0.03f;
        capeMid.pitch = lean * 0.35f + (float) Math.sin(t * 0.13f + 0.5f) * 0.04f;
        capeLow.pitch = lean * 0.5f + (float) Math.sin(t * 0.14f + 1.0f) * 0.07f;

        halo.yaw = t * 0.04f;

        drawPart(m, ctx.head, crown, vcp, light, full, ov);
        drawGlow(m, ctx.head, halo, vcp, full, ov);
        drawPart(m, ctx.rightArm, shoulderR, vcp, light, full, ov);
        drawPart(m, ctx.leftArm, shoulderL, vcp, light, full, ov);
        drawPart(m, ctx.body, cape, vcp, light, full, ov);
        drawPart(m, ctx.body, wingL, vcp, light, full, ov);
        drawPart(m, ctx.body, wingR, vcp, light, full, ov);
    }

    private static void drawPart(MatrixStack m, ModelPart bone, ModelPart gear, VertexConsumerProvider vcp,
                                 int light, int full, int ov) {
        m.push();
        bone.rotate(m);
        // fresh buffer for every draw (holding one while asking for another crashes with "Not building!")
        gear.render(m, vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(GEAR_TEX)), light, ov, -1);
        gear.render(m, vcp.getBuffer(RenderLayer.getEyes(GEAR_GLOW)), full, ov, -1);
        m.pop();
    }

    private static void drawGlow(MatrixStack m, ModelPart bone, ModelPart gear, VertexConsumerProvider vcp, int full, int ov) {
        m.push();
        bone.rotate(m);
        gear.render(m, vcp.getBuffer(RenderLayer.getEyes(GEAR_GLOW)), full, ov, -1);
        m.pop();
    }
}
