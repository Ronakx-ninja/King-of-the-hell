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
import net.minecraft.client.render.VertexConsumer;
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

    private static final int STRIPS = 9;

    private final PlayerEntityModel<AbstractClientPlayerEntity> armorModel;
    private final ModelPart crown, shoulderL, shoulderR, cape;
    private final ModelPart[] strips = new ModelPart[STRIPS];

    public HellKingFeatureRenderer(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> ctx, EntityModelLoader loader) {
        super(ctx);
        this.armorModel = new PlayerEntityModel<>(loader.getModelPart(ARMOR_LAYER), false);
        ModelPart gear = loader.getModelPart(GEAR_LAYER);
        this.crown = gear.getChild("crown");
        this.shoulderL = gear.getChild("shoulder_l");
        this.shoulderR = gear.getChild("shoulder_r");
        this.cape = gear.getChild("cape");
        for (int i = 0; i < STRIPS; i++) strips[i] = cape.getChild("s" + i);
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
        crown.addChild("ring_f", ModelPartBuilder.create().uv(0, 0).cuboid(-4.6f, -9.6f, -4.6f, 9.2f, 2.2f, 1.2f), ModelTransform.NONE);
        crown.addChild("ring_b", ModelPartBuilder.create().uv(0, 0).cuboid(-4.6f, -9.6f, 3.4f, 9.2f, 2.2f, 1.2f), ModelTransform.NONE);
        crown.addChild("ring_l", ModelPartBuilder.create().uv(0, 0).cuboid(-4.6f, -9.6f, -3.4f, 1.2f, 2.2f, 6.8f), ModelTransform.NONE);
        crown.addChild("ring_r", ModelPartBuilder.create().uv(0, 0).cuboid(3.4f, -9.6f, -3.4f, 1.2f, 2.2f, 6.8f), ModelTransform.NONE);
        // spikes on the crown (x, z, height, lean-roll, lean-pitch)
        float[][] sp = {
                {0f, -4.1f, 8f, 0f, -0.10f}, {-2.6f, -4.1f, 6f, -0.15f, -0.10f}, {2.6f, -4.1f, 6f, 0.15f, -0.10f},
                {-4.1f, 0f, 5.5f, -0.2f, 0f}, {4.1f, 0f, 5.5f, 0.2f, 0f},
                {-2.6f, 4.1f, 6f, -0.15f, 0.10f}, {2.6f, 4.1f, 6f, 0.15f, 0.10f}, {0f, 4.1f, 7.5f, 0f, 0.10f}
        };
        for (int i = 0; i < sp.length; i++) {
            crown.addChild("spike" + i, ModelPartBuilder.create().uv(0, 0).cuboid(-0.7f, -sp[i][2], -0.7f, 1.4f, sp[i][2], 1.4f),
                    ModelTransform.of(sp[i][0], -9.3f, sp[i][1], sp[i][4], 0, sp[i][3]));
        }
        horn(crown, "horn_l", 1f);
        horn(crown, "horn_r", -1f);

        // ---- shoulder spikes (attached to arms)
        shoulder(root, "shoulder_r", -1f);
        shoulder(root, "shoulder_l", 1f);

        // ---- cape (attached to body)
        ModelPartData cape = root.addChild("cape", ModelPartBuilder.create(), ModelTransform.NONE);
        float[] len = {13f, 18f, 23f, 27f, 31f, 27f, 23f, 18f, 13f};
        for (int i = 0; i < STRIPS; i++) {
            float x = -8f + i * 2f;
            float roll = (i - 4) * 0.07f;
            float yaw = (i - 4) * 0.05f;
            cape.addChild("s" + i, ModelPartBuilder.create().uv(0, 0).cuboid(-1.05f, 0f, 0f, 2.1f, len[i], 1f),
                    ModelTransform.of(x, 0.5f, 2.3f, 0.1f, yaw, roll));
        }
        return TexturedModelData.of(data, 64, 64);
    }

    private static void horn(ModelPartData parent, String name, float side) {
        float[] w = {3.4f, 2.8f, 2.2f, 1.5f};
        float[] h = {3.6f, 3.6f, 3.4f, 3.2f};
        float[] roll = {0.60f, -0.30f, -0.50f, -0.60f};
        ModelPartData cur = parent;
        for (int i = 0; i < 4; i++) {
            float py = i == 0 ? -7.5f : -h[i - 1] + 0.5f;
            float px = i == 0 ? side * 4.4f : 0f;
            cur = cur.addChild(name + i,
                    ModelPartBuilder.create().uv(0, 0).cuboid(-w[i] / 2f, -h[i], -w[i] / 2f, w[i], h[i], w[i]),
                    ModelTransform.of(px, py, 0f, 0f, 0f, side * roll[i]));
        }
    }

    private static void shoulder(ModelPartData root, String name, float side) {
        ModelPartData sh = root.addChild(name, ModelPartBuilder.create(), ModelTransform.NONE);
        float ox = side * 1.0f;
        sh.addChild("pad", ModelPartBuilder.create().uv(0, 0).cuboid(side > 0 ? -2.3f : -4.7f, -3.6f, -3.3f, 7f, 2f, 6.6f), ModelTransform.NONE);
        float[] h = {5f, 8f, 10f, 8f, 5f};
        for (int i = 0; i < 5; i++) {
            float z = -2.6f + i * 1.3f;
            float r = side * (0.55f + (i == 2 ? 0.25f : 0f) + 0.05f * i);
            sh.addChild("sp" + i, ModelPartBuilder.create().uv(0, 0).cuboid(-0.8f, -h[i], -0.8f, 1.6f, h[i], 1.6f),
                    ModelTransform.of(ox + side * 1.2f, -3.2f, z, (i - 2) * 0.08f, 0f, r));
        }
        // lower hanging spikes along the arm
        for (int i = 0; i < 3; i++) {
            sh.addChild("dn" + i, ModelPartBuilder.create().uv(0, 0).cuboid(-0.7f, 0f, -0.7f, 1.4f, 4f + i, 1.4f),
                    ModelTransform.of(side * 3.3f, -1f + i * 2.5f, -1.5f + i * 1.5f, 0f, 0f, side * 0.35f));
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
            strips[i].pitch = lean + (float) Math.sin(t * 0.13f + i * 0.9f) * 0.07f
                    + (float) Math.sin(t * 0.31f + i * 1.7f) * 0.025f;
        }

        VertexConsumer solid = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(GEAR_TEX));
        VertexConsumer glow = vcp.getBuffer(RenderLayer.getEyes(GEAR_GLOW));

        drawPart(m, ctx.head, crown, solid, glow, light, full, ov);
        drawPart(m, ctx.rightArm, shoulderR, solid, glow, light, full, ov);
        drawPart(m, ctx.leftArm, shoulderL, solid, glow, light, full, ov);
        drawPart(m, ctx.body, cape, solid, glow, light, full, ov);
    }

    private static void drawPart(MatrixStack m, ModelPart bone, ModelPart gear, VertexConsumer solid, VertexConsumer glow,
                                 int light, int full, int ov) {
        m.push();
        bone.rotate(m);
        gear.render(m, solid, light, ov, -1);
        gear.render(m, glow, full, ov, -1);
        m.pop();
    }
}
