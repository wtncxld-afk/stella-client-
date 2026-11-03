package dev.stella.client.gui.menu;

import dev.stella.client.gui.theme.ThemeManager;
import dev.stella.client.render.Render2D;
import dev.stella.client.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;

import java.util.Random;

/** Animated starfield + drifting nebula glow used behind the Stella menus. */
public final class CosmicBackground {
    private final float[][] stars;
    private final float[][] blobs;
    private final long start = System.currentTimeMillis();

    public CosmicBackground(int starCount) {
        Random r = new Random(0x5751L);
        stars = new float[starCount][];
        for (int i = 0; i < starCount; i++) stars[i] = new float[]{r.nextFloat(), r.nextFloat(), 0.3f + r.nextFloat() * 1.2f, r.nextFloat() * 6.28f, 0.2f + r.nextFloat()};
        blobs = new float[6][];
        for (int i = 0; i < blobs.length; i++) blobs[i] = new float[]{r.nextFloat(), r.nextFloat(), 0.25f + r.nextFloat() * 0.35f, r.nextFloat() * 6.28f};
    }

    public void render(DrawContext ctx, int w, int h, double alpha, boolean opaque) {
        double t = (System.currentTimeMillis() - start) / 1000.0;
        int accent = ThemeManager.get().accent();
        if (opaque) ctx.fillGradient(0, 0, w, h, ColorUtil.mulAlpha(0xFF07040E, alpha), ColorUtil.mulAlpha(0xFF130A24, alpha));
        // drifting nebula glows (stacked soft circles)
        for (int i = 0; i < blobs.length; i++) {
            float[] b = blobs[i];
            double cx = (b[0] + 0.04 * Math.sin(t * 0.07 + b[3])) * w;
            double cy = (b[1] + 0.04 * Math.cos(t * 0.05 + b[3])) * h;
            float rad = b[2] * Math.max(w, h) * 0.5f;
            int c = i % 2 == 0 ? accent : ColorUtil.lerp(accent, 0xFFEC4899, 0.6);
            for (int k = 6; k >= 1; k--) {
                float rr = rad * k / 6f;
                Render2D.roundRect(ctx, (float) cx - rr, (float) cy - rr, rr * 2, rr * 2, rr, ColorUtil.mulAlpha(ColorUtil.withAlpha(c, 10), alpha));
            }
        }
        // stars with parallax drift and twinkle
        for (float[] s : stars) {
            double x = ((s[0] + t * 0.004 * s[4]) % 1.0) * w;
            double y = s[1] * h;
            double tw = 0.5 + 0.5 * Math.sin(t * 1.6 + s[3]);
            int a = (int) (40 + 200 * tw * s[4] / 1.2);
            float sz = s[2];
            Render2D.rect(ctx, (float) x, (float) y, sz, sz, ColorUtil.mulAlpha((Math.min(255, a) << 24) | 0xF3E8FF, alpha));
        }
        // vignette
        ctx.fillGradient(0, (int) (h * 0.6), w, h, 0x00000000, ColorUtil.mulAlpha(0x90000000, alpha));
    }
}
