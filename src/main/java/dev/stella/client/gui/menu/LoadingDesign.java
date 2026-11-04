package dev.stella.client.gui.menu;

import dev.stella.client.module.ModuleManager;
import dev.stella.client.modules.client.MenusModule;
import dev.stella.client.render.Render2D;
import dev.stella.client.render.StellaFont;
import dev.stella.client.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;

/**
 * Stella loading design: cosmic background, a hand-drawn glowing star (shapes only, so it also works
 * during the very first resource load, before fonts and textures exist), orbiting sparks, and a smooth
 * progress bar with a moving shine (or an indeterminate sweep).
 */
public final class LoadingDesign {
    private static final CosmicBackground BG = new CosmicBackground(180);
    private static float shownProgress;

    private LoadingDesign() {}

    public static boolean enabled() {
        MenusModule m = ModuleManager.get(MenusModule.class);
        return m == null || m.loadingScreens.isOn();
    }

    /** Full-screen background with the logo (no text): used behind screens that draw their own text/buttons. */
    public static void background(DrawContext ctx, int w, int h, double alpha) {
        BG.render(ctx, w, h, alpha, true);
        star(ctx, w / 2f, h * 0.24f, Math.min(w, h) * 0.07f, alpha);
    }

    /**
     * The complete loading screen. {@code progress} 0..1, or negative for an indeterminate bar.
     * {@code text} is false during the first resource load (fonts not ready yet).
     */
    public static void full(DrawContext ctx, int w, int h, double alpha, float progress, String title, String status, boolean text) {
        BG.render(ctx, w, h, alpha, true);
        float cx = w / 2f, cy = h * 0.42f, size = Math.min(w, h) * 0.11f;
        star(ctx, cx, cy, size, alpha);
        float bw = Math.min(220, w * 0.5f), bx = cx - bw / 2f, by = cy + size * 1.9f;
        bar(ctx, bx, by, bw, alpha, progress);
        if (text) {
            if (title != null) {
                float s = 1.6f;
                Render2D.textScaled(ctx, title, cx - StellaFont.width(title) * s / 2f, by - 22, s, ColorUtil.mulAlpha(0xFFF3E8FF, alpha), false);
            }
            if (status != null && !status.isEmpty()) {
                Render2D.textScaled(ctx, status, cx - StellaFont.width(status) * 0.8f / 2f, by + 10, 0.8f, ColorUtil.mulAlpha(0xFFB9A8D6, alpha), false);
            }
        }
    }

    private static void bar(DrawContext ctx, float x, float y, float w, double alpha, float progress) {
        long now = System.currentTimeMillis();
        Render2D.roundRect(ctx, x - 1, y - 1, w + 2, 6, 3, ColorUtil.mulAlpha(0x60FFFFFF, alpha * 0.4));
        Render2D.roundRect(ctx, x, y, w, 4, 2, ColorUtil.mulAlpha(0xFF1B1030, alpha));
        if (progress < 0) {
            // indeterminate: a glowing segment sweeping back and forth
            double t = (Math.sin(now / 520.0) + 1) / 2;
            float sw = w * 0.28f, sx = (float) (x + (w - sw) * t);
            Render2D.roundRect(ctx, sx - 3, y - 2, sw + 6, 8, 4, ColorUtil.mulAlpha(0x40C084FC, alpha));
            Render2D.hGradient(ctx, sx, y, sw, 4, ColorUtil.mulAlpha(0xFFA855F7, alpha), ColorUtil.mulAlpha(0xFFF472B6, alpha));
            return;
        }
        shownProgress += (Math.max(0, Math.min(1, progress)) - shownProgress) * 0.12f; // smooth, never jumps
        float fw = Math.max(4, w * shownProgress);
        Render2D.roundRect(ctx, x - 2, y - 2, fw + 4, 8, 4, ColorUtil.mulAlpha(0x40C084FC, alpha)); // glow
        Render2D.hGradient(ctx, x, y, fw, 4, ColorUtil.mulAlpha(0xFF8B5CF6, alpha), ColorUtil.mulAlpha(0xFFF472B6, alpha));
        // shine travelling along the filled part
        float shine = (float) ((now % 1400) / 1400.0) * fw;
        Render2D.hGradient(ctx, x + Math.max(0, shine - 18), y, Math.min(18, shine), 4, 0, ColorUtil.mulAlpha(0x90FFFFFF, alpha));
        Render2D.roundRect(ctx, x + fw - 3, y - 1, 6, 6, 3, ColorUtil.mulAlpha(0xFFFFFFFF, alpha)); // head
    }

    /** Glowing five-point star with a soft halo, gentle rotation and orbiting sparks. */
    public static void star(DrawContext ctx, float cx, float cy, float r, double alpha) {
        long now = System.currentTimeMillis();
        double pulse = 0.85 + 0.15 * Math.sin(now / 420.0);
        // halo rings
        for (int i = 6; i >= 1; i--) {
            float rr = (float) (r * (1.2 + i * 0.32) * pulse);
            Render2D.roundRect(ctx, cx - rr, cy - rr, rr * 2, rr * 2, rr, ColorUtil.mulAlpha(0x18A855F7, alpha * (1.0 - i * 0.12)));
        }
        double rot = Math.sin(now / 2600.0) * 0.18;
        // filled star: nested outlines from the edge inward, lilac -> white
        int layers = Math.max(6, (int) (r / 2));
        for (int l = 0; l <= layers; l++) {
            double k = 1 - l / (double) layers;
            int c = ColorUtil.lerp(0xFFC084FC, 0xFFFFFFFF, Math.pow(1 - k, 1.4));
            starOutline(ctx, cx, cy, (float) (r * k * pulse), rot, Math.max(1.2f, r / 10f), ColorUtil.mulAlpha(c, alpha));
        }
        // orbiting sparks
        for (int i = 0; i < 3; i++) {
            double a = now / 900.0 + i * Math.PI * 2 / 3;
            float ox = cx + (float) Math.cos(a) * r * 1.9f, oy = cy + (float) Math.sin(a) * r * 0.75f;
            float s = Math.max(1.5f, r / 9f);
            Render2D.roundRect(ctx, ox - s * 2, oy - s * 2, s * 4, s * 4, s * 2, ColorUtil.mulAlpha(0x40F472B6, alpha));
            Render2D.roundRect(ctx, ox - s / 2, oy - s / 2, s, s, s / 2, ColorUtil.mulAlpha(0xFFFFFFFF, alpha));
        }
    }

    private static void starOutline(DrawContext ctx, float cx, float cy, float r, double rot, float width, int color) {
        if (r < 0.5f) return;
        double[] xs = new double[10], ys = new double[10];
        for (int i = 0; i < 10; i++) {
            double rad = (i % 2 == 0) ? r : r * 0.45;
            double a = rot - Math.PI / 2 + i * Math.PI / 5;
            xs[i] = cx + Math.cos(a) * rad;
            ys[i] = cy + Math.sin(a) * rad;
        }
        for (int i = 0; i < 10; i++) Render2D.line(ctx, xs[i], ys[i], xs[(i + 1) % 10], ys[(i + 1) % 10], width, color);
    }
}
