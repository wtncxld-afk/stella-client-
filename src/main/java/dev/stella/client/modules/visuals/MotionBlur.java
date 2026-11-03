package dev.stella.client.modules.visuals;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.stella.client.StellaClient;
import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.NumberSetting;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.util.Identifier;

/**
 * Frame-accumulation motion blur. At the end of the main world pass the previous
 * blended frame is drawn over the new frame with partial opacity (a full-screen
 * quad in clip space), then the result is copied into the history texture for
 * the next frame. The hand and HUD render afterwards, so they stay sharp.
 */
public class MotionBlur extends Module {
    private static final Identifier HISTORY_ID = Identifier.of(StellaClient.MOD_ID, "motion_blur_history");

    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.of(StellaClient.MOD_ID, "pipeline/motion_blur"))
            .withVertexShader(Identifier.of(StellaClient.MOD_ID, "core/motion_blur"))
            .withFragmentShader(Identifier.of(StellaClient.MOD_ID, "core/motion_blur"))
            .withSampler("Sampler0")
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build();
    private static RenderLayer layer;

    private final NumberSetting strength = add(new NumberSetting("Strength", "Amount of trail kept from previous frames", 0.5, 0.05, 0.9, 0.05));
    private final BoolSetting pauseInMenus = add(new BoolSetting("Pause In Menus", "No blur while a screen (GUI, inventory) is open", true));
    private final BoolSetting onlyMoving = add(new BoolSetting("Only When Turning", "Blur only while the camera is turning", false));
    private float lastYaw, lastPitch;

    private Framebuffer history;
    private HistoryTexture texture;
    private boolean fresh = true;

    public MotionBlur() { super("Motion Blur", "Smooth frame-blending motion blur", Category.VISUALS); }

    @Override protected void onEnable() { fresh = true; }

    @Override
    protected void onDisable() {
        if (history != null) { history.delete(); history = null; }
        fresh = true;
    }

    /** Called from WorldRenderEvents.END_MAIN while the main framebuffer is bound. */
    public void apply(VertexConsumerProvider.Immediate consumers) {
        Framebuffer main = mc.getFramebuffer();
        int w = main.textureWidth, h = main.textureHeight;
        if (history == null || history.textureWidth != w || history.textureHeight != h) {
            if (history != null) history.delete();
            history = new SimpleFramebuffer("Stella motion blur", w, h, false);
            fresh = true;
        }
        if (texture == null) {
            texture = new HistoryTexture();
            mc.getTextureManager().registerTexture(HISTORY_ID, texture);
        }
        texture.wrap(history);
        if (layer == null) {
            layer = RenderLayer.of("stella_motion_blur", RenderSetup.builder(PIPELINE).texture("Sampler0", HISTORY_ID).expectedBufferSize(256).build());
        }

        boolean skip = pauseInMenus.isOn() && mc.currentScreen != null;
        if (onlyMoving.isOn() && mc.player != null) {
            float dy = Math.abs(mc.player.getYaw() - lastYaw) + Math.abs(mc.player.getPitch() - lastPitch);
            lastYaw = mc.player.getYaw();
            lastPitch = mc.player.getPitch();
            if (dy < 0.5f) skip = true;
        }
        if (!fresh && !skip) {
            int a = (int) (strength.get() * 255);
            VertexConsumer buf = consumers.getBuffer(layer);
            buf.vertex(-1f, -1f, 0f).texture(0f, 0f).color(255, 255, 255, a);
            buf.vertex(1f, -1f, 0f).texture(1f, 0f).color(255, 255, 255, a);
            buf.vertex(1f, 1f, 0f).texture(1f, 1f).color(255, 255, 255, a);
            buf.vertex(-1f, 1f, 0f).texture(0f, 1f).color(255, 255, 255, a);
            consumers.draw(layer);
        }
        fresh = false;

        GpuTexture src = main.getColorAttachment(), dst = history.getColorAttachment();
        if (src != null && dst != null) {
            RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(src, dst, 0, 0, 0, 0, 0, w, h);
        }
    }

    /** Exposes the history framebuffer's colour attachment as a texture for the render layer. */
    private final class HistoryTexture extends AbstractTexture {
        void wrap(Framebuffer fb) {
            this.glTexture = fb.getColorAttachment();
            this.glTextureView = fb.getColorAttachmentView();
            if (this.sampler == null) {
                this.sampler = mc.getTextureManager().getTexture(Identifier.of(StellaClient.MOD_ID, "textures/gui/star.png")).getSampler();
            }
        }

        @Override public void close() { /* owned by the framebuffer */ }
    }
}
