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

    private static final int STRIPS = 11;
    private static final int STRIPS2 = 10;

    private final PlayerEntityModel<AbstractClientPlayerEntity> armorModel;
    private final ModelPart crown, shoulderL, shoulderR, cape, halo;
    private final ModelPart[] strips = new ModelPart[STRIPS];
    private final ModelPart[] strips2 = new ModelPart[STRIPS2];

    public HellKingFeatureRenderer(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> ctx, EntityModelLoader loader) {
        super(ctx);
        this.armorModel = new PlayerEntityModel<>(loader.getModelPart(ARMOR_LAYER), false);
        ModelPart gear = loader.getModelPart(GEAR_LAYER);
        this.crown = gear.getChild("crown");
        this.shoulderL = gear.getChild("shoulder_l");
        this.shoulderR = gear.getChild("shoulder_r");
        this.cape = gear.getChild("cape");
        this.halo = gear.getChild("halo");
        for (int i = 0; i < STRIPS; i++) strips[i] = cape.getChild("s" + i);
        for (int i = 0; i < STRIPS2; i++) strips2[i] = cape.getChild("t" + i);
    }

    // ------------------------------------------------------------------ model data
    public static TexturedModelData createArmorLayer() {
        return TexturedModelData.of(PlayerEntityModel.getTexturedModelData(new Dilation(0.55f), false), 64, 64);
    }

    public static TexturedModelData createGearLayer() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();

        // ---- crown + horns (attached to head)
        ModelPartData crown = root.addChild("crown", ModelPartBuilder.create(), ModelTransform.NONE);
        crown.addChild("ring_f", ModelPartBuilder.create().uv(0, 0).cuboid(-4.8f, -10.0f, -4.8f, 9.6f, 2.8f, 1.4f), ModelTransform.NONE);
        crown.addChild("ring_b", ModelPartBuilder.create().uv(0, 0).cuboid(-4.8f, -10.0f, 3.4f, 9.6f, 2.8f, 1.4f), ModelTransform.NONE);
        crown.addChild("ring_l", ModelPartBuilder.create().uv(0, 0).cuboid(-4.8f, -10.0f, -3.4f, 1.4f, 2.8f, 6.8f), ModelTransform.NONE);
        crown.addChild("ring_r", ModelPartBuilder.create().uv(0, 0).cuboid(3.4f, -10.0f, -3.4f, 1.4f, 2.8f, 6.8f), ModelTransform.NONE);
        // crown spikes (x, z, height, roll, pitch)
        float[][] sp = {
                {0f, -4.3f, 11f, 0f, -0.10f}, {-2.4f, -4.3f, 8.5f, -0.12f, -0.10f}, {2.4f, -4.3f, 8.5f, 0.12f, -0.10f},
                {-4.3f, -2f, 7f, -0.22f, -0.05f}, {4.3f, -2f, 7f, 0.22f, -0.05f},
                {-4.3f, 2f, 7f, -0.22f, 0.05f}, {4.3f, 2f, 7f, 0.22f, 0.05f},
                {-2.4f, 4.3f, 8.5f, -0.12f, 0.10f}, {2.4f, 4.3f, 8.5f, 0.12f, 0.10f}, {0f, 4.3f, 10f, 0f, 0.10f}
        };
        for (int i = 0; i < sp.length; i++) {
            crown.addChild("spike" + i, ModelPartBuilder.create().uv(0, 0).cuboid(-0.9f, -sp[i][2], -0.9f, 1.8f, sp[i][2], 1.8f),
                    ModelTransform.of(sp[i][0], -9.6f, sp[i][1], sp[i][4], 0, sp[i][3]));
        }
        horn(crown, "horn_l", 1f);
        horn(crown, "horn_r", -1f);

        // ---- shoulder spikes (attached to arms)
        shoulder(root, "shoulder_r", -1f);
        shoulder(root, "shoulder_l", 1f);

        // ---- cape (attached to body): two ragged layers
        ModelPartData cape = root.addChild("cape", ModelPartBuilder.create(), ModelTransform.NONE);
        float[] len = {20f, 30f, 24f, 35f, 27f, 38f, 27f, 35f, 24f, 30f, 20f};
        for (int i = 0; i < STRIPS; i++) {
            float x = -10f + i * 2f;
            float roll = (i - 5) * 0.06f;
            float yaw = (i - 5) * 0.07f;
            cape.addChild("s" + i, ModelPartBuilder.create().uv(0, 0).cuboid(-1.1f, 0f, 0f, 2.2f, len[i], 1f),
                    ModelTransform.of(x, 0.5f, 2.3f, 0.1f, yaw, roll));
        }
        float[] len2 = {16f, 26f, 19f, 30f, 22f, 30f, 19f, 26f, 16f, 22f};
        for (int i = 0; i < STRIPS2; i++) {
            float x = -9f + i * 2f;
            cape.addChild("t" + i, ModelPartBuilder.create().uv(0, 0).cuboid(-1.1f, 0f, 0f, 2.2f, len2[i], 1f),
                    ModelTransform.of(x, 1.5f, 3.4f, 0.12f, (i - 4.5f) * 0.06f, (i - 4.5f) * 0.05f));
        }

        // ---- red eclipse halo behind the head (attached to body)
        ModelPartData halo = root.addChild("halo", ModelPartBuilder.create(), ModelTransform.pivot(0f, -5f, 6.5f));
        int n = 20;
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            halo.addChild("h" + i, ModelPartBuilder.create().uv(50, 56).cuboid(-1.3f, -1.3f, -0.5f, 2.6f, 2.6f, 1f),
                    ModelTransform.of((float) Math.cos(a) * 12f, (float) Math.sin(a) * 12f, 0f, 0f, 0f, (float) a));
        }
        return TexturedModelData.of(data, 64, 64);
    }

    private static void horn(ModelPartData parent, String name, float side) {
        float[] w = {4.0f, 3.4f, 2.8f, 2.2f, 1.5f};
        float[] h = {4.2f, 4.2f, 4.0f, 3.8f, 3.6f};
        float[] roll = {0.65f, -0.15f, -0.35f, -0.50f, -0.60f};
        ModelPartData cur = parent;
        for (int i = 0; i < 5; i++) {
            float py = i == 0 ? -8.0f : -h[i - 1] + 0.5f;
            float px = i == 0 ? side * 4.6f : 0f;
            cur = cur.addChild(name + i,
                    ModelPartBuilder.create().uv(0, 0).cuboid(-w[i] / 2f, -h[i], -w[i] / 2f, w[i], h[i], w[i]),
                    ModelTransform.of(px, py, 0f, 0f, 0f, side * roll[i]));
        }
    }

    private static void shoulder(ModelPartData root, String name, float side) {
        ModelPartData sh = root.addChild(name, ModelPartBuilder.create(), ModelTransform.NONE);
        float ox = side * 1.0f;
        sh.addChild("pad", ModelPartBuilder.create().uv(0, 0).cuboid(side > 0 ? -2.6f : -5.4f, -3.8f, -3.6f, 8f, 2.4f, 7.2f), ModelTransform.NONE);
        float[] h = {6f, 10f, 14f, 16f, 14f, 10f, 6f};
        for (int i = 0; i < 7; i++) {
            float z = -3.0f + i * 1.0f;
            float r = side * (0.50f + (i == 3 ? 0.30f : 0f) + 0.04f * Math.abs(i - 3));
            sh.addChild("sp" + i, ModelPartBuilder.create().uv(0, 0).cuboid(-0.9f, -h[i], -0.9f, 1.8f, h[i], 1.8f),
                    ModelTransform.of(ox + side * 1.4f, -3.4f, z, (i - 3) * 0.10f, 0f, r));
        }
        // back-swept feather spikes
        for (int i = 0; i < 4; i++) {
            sh.addChild("bk" + i, ModelPartBuilder.create().uv(0, 0).cuboid(-0.8f, -(8f + i * 2f), -0.8f, 1.6f, 8f + i * 2f, 1.6f),
                    ModelTransform.of(ox + side * (0.6f + i * 0.5f), -3.2f, 3.0f, 0.9f, 0f, side * (0.3f + i * 0.12f)));
        }
        // hanging spikes along the arm
        for (int i = 0; i < 5; i++) {
            sh.addChild("dn" + i, ModelPartBuilder.create().uv(0, 0).cuboid(-0.7f, 0f, -0.7f, 1.4f, 4f + i, 1.4f),
                    ModelTransform.of(side * 3.4f, -1f + i * 2.4f, -2.0f + i * 1.0f, 0f, 0f, side * 0.4f));
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
        float lean = 0.12f + Math.min(limbDistance, 1f) * 0.55f + (flying ? 0.55f : 0f);
        for (int i = 0; i < STRIPS; i++) {
            strips[i].pitch = lean + (float) Math.sin(t * 0.13f + i * 0.9f) * 0.08f
                    + (float) Math.sin(t * 0.31f + i * 1.7f) * 0.03f;
        }
        for (int i = 0; i < STRIPS2; i++) {
            strips2[i].pitch = lean + 0.06f + (float) Math.sin(t * 0.15f + i * 1.1f + 1.3f) * 0.09f
                    + (float) Math.sin(t * 0.37f + i * 2.1f) * 0.03f;
        }
        halo.roll = t * 0.03f;

        drawPart(m, ctx.head, crown, vcp, light, full, ov);
        drawPart(m, ctx.rightArm, shoulderR, vcp, light, full, ov);
        drawPart(m, ctx.leftArm, shoulderL, vcp, light, full, ov);
        drawPart(m, ctx.body, cape, vcp, light, full, ov);
        // halo is pure glow
        m.push();
        ctx.body.rotate(m);
        halo.render(m, vcp.getBuffer(RenderLayer.getEyes(GEAR_GLOW)), full, ov, -1);
        m.pop();
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
