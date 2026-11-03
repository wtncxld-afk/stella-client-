package dev.stella.client.modules.visuals;

import dev.stella.client.StellaClient;
import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.setting.NumberSetting;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Frame-accumulation motion blur: the previous blended frame is drawn over the
 * new world frame with partial opacity, then stored for the next frame. Applied
 * before the HUD renders, so HUD text stays sharp.
 */
public class MotionBlur extends Module {
    private static final Identifier HISTORY_ID = Identifier.of(StellaClient.MOD_ID, "motion_blur_history");
    private final NumberSetting strength = add(new NumberSetting("Strength", "Amount of trail kept from previous frames", 0.5, 0.05, 0.9, 0.05));

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

    /** Called at the start of InGameHud#render. */
    public void apply(DrawContext ctx) {
        Framebuffer main = mc.getFramebuffer();
        int w = main.textureWidth, h = main.textureHeight;
        if (history == null || history.textureWidth != w || history.textureHeight != h) {
            if (history != null) history.delete();
            history = new SimpleFramebuffer(w, h, false);
            fresh = true;
        }
        if (texture == null) {
            texture = new HistoryTexture();
            mc.getTextureManager().registerTexture(HISTORY_ID, texture);
        }
        texture.setId(history.getColorAttachment());

        if (!fresh) {
            int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
            int alpha = (int) (strength.get() * 255);
            ctx.getMatrices().push();
            ctx.getMatrices().translate(0, sh, 0);
            ctx.getMatrices().scale(1, -1, 1);
            ctx.drawTexture(RenderLayer::getGuiTextured, HISTORY_ID, 0, 0, 0f, 0f, sw, sh, w, h, w, h, (alpha << 24) | 0xFFFFFF);
            ctx.getMatrices().pop();
            ctx.draw();
        }
        fresh = false;

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.fbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, history.fbo);
        GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        main.beginWrite(false);
    }

    /** Wraps the history framebuffer's colour attachment so DrawContext can sample it. */
    private static final class HistoryTexture extends AbstractTexture {
        void setId(int id) { this.glId = id; }
        @Override public void close() { /* owned by the framebuffer */ }
    }
}
