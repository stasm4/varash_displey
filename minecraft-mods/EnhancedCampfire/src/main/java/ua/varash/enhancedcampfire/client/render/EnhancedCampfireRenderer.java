package ua.varash.enhancedcampfire.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import ua.varash.enhancedcampfire.EnhancedCampfireMod;
import ua.varash.enhancedcampfire.block.EnhancedCampfireBlock;
import ua.varash.enhancedcampfire.block.EnhancedCampfireBlockEntity;
import ua.varash.enhancedcampfire.client.fx.CampfireFxState;
import ua.varash.enhancedcampfire.client.fx.CampfireLayouts;
import ua.varash.enhancedcampfire.client.fx.FireNoise;
import ua.varash.enhancedcampfire.client.fx.Wind;
import ua.varash.enhancedcampfire.config.ClientConfig;
import ua.varash.enhancedcampfire.config.ClientConfig.Quality;

/**
 * Renders the food on the campfire and all procedural fire visuals:
 * <ul>
 *   <li>flame tongues - every emitter of the log layout gets a bent, crossed pair of quads per layer; height,
 *   width, lean and sway come from per-campfire noise, the global wind and flare-ups; the texture is a
 *   16-frame turbulent flame sheet, each tongue running at its own speed and phase with smooth frame blending,
 *   so no visible loop exists.  Layers: a dark red/orange alpha-blended body (keeps the fire readable in
 *   daylight), an additive colour-ramped flame and a smaller additive hot core.</li>
 *   <li>a soft additive glow over the ember bed (also while smouldering)</li>
 *   <li>a flickering warm light pool projected on the tops of the surrounding ground blocks (stronger at night)</li>
 * </ul>
 * Nothing is allocated per frame.
 */
public class EnhancedCampfireRenderer implements BlockEntityRenderer<EnhancedCampfireBlockEntity> {
    private static final ResourceLocation FLAME_ALPHA = tex("flame_sheet");
    private static final ResourceLocation FLAME_ADD = tex("flame_sheet_add");
    private static final ResourceLocation GLOW = tex("glow_radial");

    private static final int MAX_TONGUES = 8;
    /** Per-tongue animation parameters, computed once per frame and reused by every layer. */
    private static final int P_X = 0, P_Z = 1, P_H = 2, P_W = 3, P_SX = 4, P_SZ = 5, P_YAW = 6, P_FRAME = 7, P_SIZE = 8;
    private final float[] params = new float[MAX_TONGUES * P_SIZE];

    private final ItemRenderer itemRenderer;

    public EnhancedCampfireRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    private static ResourceLocation tex(String name) {
        return new ResourceLocation(EnhancedCampfireMod.MODID, "textures/effect/" + name + ".png");
    }

    @Override
    public void render(EnhancedCampfireBlockEntity campfire, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        BlockState state = campfire.getBlockState();
        renderFood(campfire, state, pose, buffers, packedLight);

        Level level = campfire.getLevel();
        if (level == null || !(state.getBlock() instanceof EnhancedCampfireBlock)) {
            return;
        }
        boolean lit = state.getValue(CampfireBlock.LIT);
        boolean smouldering = state.getValue(EnhancedCampfireBlock.SMOULDERING);
        if (!lit && !smouldering) {
            return;
        }
        CampfireFxState fx = CampfireFxState.of(campfire);
        long gameTime = level.getGameTime();
        float time = ((gameTime % 1_200_000L) + partialTick) / 20.0F;
        BlockPos pos = campfire.getBlockPos();
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double distSq = cam.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        float glowMul = ClientConfig.GLOW_INTENSITY.get().floatValue();
        float night = nightFactor(level, pos, partialTick);

        if (!lit) {
            float pulse = 0.75F + 0.25F * FireNoise.fbm(time * 0.7F, fx.seed);
            renderEmberGlow(pose, buffers, 0.32F * glowMul * pulse * (0.5F + 0.5F * night), 0.62F);
            return;
        }

        float flare = fx.flare(gameTime, partialTick);
        float intensity = fx.intensity(time, flare);
        if (ClientConfig.GROUND_GLOW.get() && glowMul > 0.0F && distSq < 48.0D * 48.0D) {
            renderGroundGlow(pose, buffers, fx, 0.5F * glowMul * night * Mth.clamp(intensity, 0.6F, 1.6F));
        }
        renderEmberGlow(pose, buffers, (0.45F + 0.25F * night) * glowMul * Mth.clamp(intensity, 0.6F, 1.5F), 0.95F);

        Quality quality = ClientConfig.FLAME_QUALITY.get();
        if (distSq > 56.0D * 56.0D) {
            quality = Quality.LOW;
        } else if (distSq > 28.0D * 28.0D && quality == Quality.HIGH) {
            quality = Quality.MEDIUM;
        }
        renderFlames(state, fx, pose, buffers, time, intensity, flare, quality);
    }

    // ------------------------------------------------------------------ food

    private void renderFood(EnhancedCampfireBlockEntity campfire, BlockState state, PoseStack pose, MultiBufferSource buffers,
                            int packedLight) {
        NonNullList<ItemStack> items = campfire.getItems();
        Direction facing = state.getValue(CampfireBlock.FACING);
        int seed = (int) campfire.getBlockPos().asLong();
        for (int i = 0; i < items.size(); ++i) {
            ItemStack stack = items.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            pose.pushPose();
            pose.translate(0.5F, 0.40F, 0.5F);
            Direction dir = Direction.from2DDataValue((i + facing.get2DDataValue()) % 4);
            pose.mulPose(Axis.YP.rotationDegrees(-dir.toYRot()));
            pose.mulPose(Axis.XP.rotationDegrees(90.0F));
            pose.translate(-0.3125F, -0.3125F, 0.0F);
            pose.scale(0.375F, 0.375F, 0.375F);
            itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, packedLight, OverlayTexture.NO_OVERLAY, pose, buffers,
                    campfire.getLevel(), seed + i);
            pose.popPose();
        }
    }

    // ------------------------------------------------------------------ flames

    private void renderFlames(BlockState state, CampfireFxState fx, PoseStack pose, MultiBufferSource buffers, float time,
                              float intensity, float flare, Quality quality) {
        float[][] emitters = CampfireLayouts.emitters(state.getValue(EnhancedCampfireBlock.VARIANT),
                state.getValue(CampfireBlock.FACING));
        int count = Math.min(emitters.length, MAX_TONGUES);
        float scale = ClientConfig.FLAME_SCALE.get().floatValue();
        float bright = ClientConfig.FLAME_INTENSITY.get().floatValue();
        float windX = Wind.x();
        float windZ = Wind.z();

        for (int i = 0; i < count; i++) {
            float[] e = emitters[i];
            int s = fx.seed + i * 131;
            float n1 = FireNoise.fbm(time * (1.25F + i * 0.07F), s);
            float n2 = FireNoise.noise(time * 8.5F, s + 7);
            int o = i * P_SIZE;
            params[o + P_X] = e[0] + FireNoise.noise(time * 0.6F, s + 3) * 0.03F;
            params[o + P_Z] = e[1] + FireNoise.noise(time * 0.6F, s + 5) * 0.03F;
            params[o + P_H] = e[2] * scale * intensity * (0.8F + 0.26F * n1 + 0.06F * n2) * (1.0F + flare * (i == 0 ? 0.6F : 0.35F));
            params[o + P_W] = e[3] * scale * (0.93F + 0.12F * FireNoise.noise(time * 2.1F, s + 11)) * (1.0F + flare * 0.25F);
            // lean: own turbulence + global wind, grows with height inside tongue()
            params[o + P_SX] = FireNoise.fbm(time * 0.9F, s + 21) * 0.17F + windX * 0.08F;
            params[o + P_SZ] = FireNoise.fbm(time * 0.85F, s + 29) * 0.17F + windZ * 0.08F;
            params[o + P_YAW] = i * 1.7F + (fx.seed % 628) * 0.01F + time * ((i & 1) == 0 ? 0.11F : -0.09F);
            params[o + P_FRAME] = time * (11.0F + (i % 3) * 2.5F) + i * 5.3F + (fx.seed & 15);
        }

        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        boolean blend = quality == Quality.HIGH;
        int segments = quality == Quality.LOW ? 2 : quality == Quality.MEDIUM ? 3 : 4;

        // Each layer fetches its buffer right before writing: a BufferSource ends the previous batch when a different
        // render type is requested.
        if (quality != Quality.LOW) {
            VertexConsumer body = buffers.getBuffer(RenderType.entityTranslucentEmissive(FLAME_ALPHA));
            for (int i = 0; i < count; i++) {
                layer(body, m, n, i, 1.18F, 1.0F, 0.0F, 1.0F, 0.36F, 0.09F, 0.48F * bright, blend, segments, false);
            }
        }
        VertexConsumer add = buffers.getBuffer(RenderType.eyes(FLAME_ADD));
        float glow = Mth.clamp(0.8F * bright * (0.85F + 0.25F * (intensity - 0.9F)), 0.0F, 1.0F);
        for (int i = 0; i < count; i++) {
            layer(add, m, n, i, 1.0F, 1.0F, 0.0F, glow, glow, glow, 1.0F, blend, segments, true);
        }
        if (quality == Quality.HIGH) {
            float core = 0.5F * bright;
            for (int i = 0; i < Math.min(count, 5); i++) {
                layer(add, m, n, i, 0.55F, 0.6F, 7.0F, core, core * 0.88F, core * 0.68F, 1.0F, false, 3, true);
            }
        }
    }

    /**
     * One layer of one tongue.  When {@code blend} is set, two consecutive animation frames are drawn with
     * complementary weights, which turns the 16-frame sheet into a smooth, continuous motion.
     */
    private void layer(VertexConsumer vc, Matrix4f m, Matrix3f n, int i, float widthMul, float heightMul, float frameOffset,
                       float r, float g, float b, float a, boolean blend, int segments, boolean additive) {
        int o = i * P_SIZE;
        float h = params[o + P_H] * heightMul;
        if (h < 0.04F) {
            return;
        }
        float w = params[o + P_W] * widthMul;
        float framePos = params[o + P_FRAME] + frameOffset;
        int f0 = Mth.floor(framePos);
        float t = framePos - f0;
        if (!blend) {
            tongue(vc, m, n, o, h, w, f0 & 15, r, g, b, a, segments);
            return;
        }
        // additive layers carry their weight in the colour, alpha-blended layers in the alpha
        float w0 = 1.0F - t;
        if (additive) {
            tongue(vc, m, n, o, h, w, f0 & 15, r * w0, g * w0, b * w0, a, segments);
            tongue(vc, m, n, o, h, w, (f0 + 1) & 15, r * t, g * t, b * t, a, segments);
        } else {
            tongue(vc, m, n, o, h, w, f0 & 15, r, g, b, a * w0, segments);
            tongue(vc, m, n, o, h, w, (f0 + 1) & 15, r, g, b, a * t, segments);
        }
    }

    private void tongue(VertexConsumer vc, Matrix4f m, Matrix3f n, int o, float h, float w, int frame,
                        float r, float g, float b, float a, int segments) {
        if (a <= 0.004F || r + g + b <= 0.004F) {
            return;
        }
        float cx = params[o + P_X];
        float cz = params[o + P_Z];
        float swayX = params[o + P_SX] * h;
        float swayZ = params[o + P_SZ] * h;
        float yaw = params[o + P_YAW];
        // frame rectangle in the 4x4 sheet (128x256 px), inset by half a texel against bleeding
        float u0 = (frame & 3) * 0.25F + 0.5F / 128.0F;
        float u1 = u0 + 0.25F - 1.0F / 128.0F;
        float vTop = (frame >> 2) * 0.25F + 0.5F / 256.0F;
        float vBottom = vTop + 0.25F - 1.0F / 256.0F;
        float y0base = CampfireLayouts.BASE_Y;
        for (int p = 0; p < 2; p++) {
            float ang = yaw + p * 1.5707964F;
            float dx = Mth.cos(ang) * w * 0.5F;
            float dz = Mth.sin(ang) * w * 0.5F;
            for (int k = 0; k < segments; k++) {
                float f0 = (float) k / segments;
                float f1 = (float) (k + 1) / segments;
                float y0 = y0base + h * f0;
                float y1 = y0base + h * f1;
                float s0 = f0 * f0 * (0.6F + 0.4F * f0);
                float s1 = f1 * f1 * (0.6F + 0.4F * f1);
                float ox0 = swayX * s0, oz0 = swayZ * s0;
                float ox1 = swayX * s1, oz1 = swayZ * s1;
                float v0 = Mth.lerp(f0, vBottom, vTop);
                float v1 = Mth.lerp(f1, vBottom, vTop);
                float ax = cx - dx + ox0, az = cz - dz + oz0;
                float bx = cx + dx + ox0, bz = cz + dz + oz0;
                float qx = cx + dx + ox1, qz = cz + dz + oz1;
                float px = cx - dx + ox1, pz = cz - dz + oz1;
                // front
                vertex(vc, m, n, ax, y0, az, u0, v0, r, g, b, a);
                vertex(vc, m, n, bx, y0, bz, u1, v0, r, g, b, a);
                vertex(vc, m, n, qx, y1, qz, u1, v1, r, g, b, a);
                vertex(vc, m, n, px, y1, pz, u0, v1, r, g, b, a);
                // back
                vertex(vc, m, n, px, y1, pz, u0, v1, r, g, b, a);
                vertex(vc, m, n, qx, y1, qz, u1, v1, r, g, b, a);
                vertex(vc, m, n, bx, y0, bz, u1, v0, r, g, b, a);
                vertex(vc, m, n, ax, y0, az, u0, v0, r, g, b, a);
            }
        }
    }

    // ------------------------------------------------------------------ glows

    private static void renderEmberGlow(PoseStack pose, MultiBufferSource buffers, float strength, float size) {
        if (strength <= 0.01F) {
            return;
        }
        VertexConsumer vc = buffers.getBuffer(RenderType.eyes(GLOW));
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float y = CampfireLayouts.BASE_Y + 0.03F;
        float lo = 0.5F - size * 0.5F;
        float hi = 0.5F + size * 0.5F;
        float r = strength;
        float g = strength * 0.42F;
        float b = strength * 0.1F;
        for (int k = 0; k < 2; k++) {
            float yy = y + k * 0.09F; // a second, higher sheet gives the glow some volume between the logs
            float rr = k == 0 ? r : r * 0.5F, gg = k == 0 ? g : g * 0.5F, bb = k == 0 ? b : b * 0.5F;
            vertex(vc, m, n, lo, yy, lo, 0, 0, rr, gg, bb, 1.0F);
            vertex(vc, m, n, lo, yy, hi, 0, 1, rr, gg, bb, 1.0F);
            vertex(vc, m, n, hi, yy, hi, 1, 1, rr, gg, bb, 1.0F);
            vertex(vc, m, n, hi, yy, lo, 1, 0, rr, gg, bb, 1.0F);
            vertex(vc, m, n, hi, yy, lo, 1, 0, rr, gg, bb, 1.0F);
            vertex(vc, m, n, hi, yy, hi, 1, 1, rr, gg, bb, 1.0F);
            vertex(vc, m, n, lo, yy, hi, 0, 1, rr, gg, bb, 1.0F);
            vertex(vc, m, n, lo, yy, lo, 0, 0, rr, gg, bb, 1.0F);
        }
    }

    /**
     * Warm light pool: one additive quad on the top face of every nearby ground block (cells found by
     * {@link CampfireFxState}), with UVs mapping a radial gradient centred on the fire, so the falloff is smooth
     * across block borders.  The quads lie 0.02 above the surface to avoid z-fighting.
     */
    private static void renderGroundGlow(PoseStack pose, MultiBufferSource buffers, CampfireFxState fx, float strength) {
        int[] cells = fx.glowCells();
        if (strength <= 0.01F || cells.length == 0) {
            return;
        }
        VertexConsumer vc = buffers.getBuffer(RenderType.eyes(GLOW));
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float span = CampfireFxState.GLOW_REACH * 2.0F;
        for (int c = 0; c < cells.length; c += 3) {
            int dx = cells[c];
            int dy = cells[c + 1];
            int dz = cells[c + 2];
            float k = dy == 0 ? 1.0F : dy == 1 ? 0.7F : dy == -1 ? 0.8F : 0.55F;
            float r = strength * k;
            float g = r * 0.5F;
            float b = r * 0.17F;
            float y = dy + 0.02F;
            float x0 = dx, x1 = dx + 1, z0 = dz, z1 = dz + 1;
            float u0 = (x0 - 0.5F) / span + 0.5F, u1 = (x1 - 0.5F) / span + 0.5F;
            float v0 = (z0 - 0.5F) / span + 0.5F, v1 = (z1 - 0.5F) / span + 0.5F;
            vertex(vc, m, n, x0, y, z0, u0, v0, r, g, b, 1.0F);
            vertex(vc, m, n, x0, y, z1, u0, v1, r, g, b, 1.0F);
            vertex(vc, m, n, x1, y, z1, u1, v1, r, g, b, 1.0F);
            vertex(vc, m, n, x1, y, z0, u1, v0, r, g, b, 1.0F);
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v,
                               float r, float g, float b, float a) {
        vc.vertex(m, x, y, z)
                .color(r, g, b, a)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(n, 0.0F, 1.0F, 0.0F)
                .endVertex();
    }

    /** 0 in full daylight under open sky, 1 at night or underground. */
    private static float nightFactor(Level level, BlockPos pos, float partialTick) {
        float daylight = 1.0F;
        if (level instanceof ClientLevel clientLevel) {
            daylight = Mth.clamp((clientLevel.getSkyDarken(partialTick) - 0.2F) / 0.8F, 0.0F, 1.0F);
        }
        float sky = level.getBrightness(LightLayer.SKY, pos.above()) / 15.0F;
        return 1.0F - daylight * sky;
    }

    @Override
    public boolean shouldRenderOffScreen(EnhancedCampfireBlockEntity campfire) {
        return false;
    }

    @Override
    public int getViewDistance() {
        return ClientConfig.FLAME_RENDER_DISTANCE.get();
    }
}
