package dev.stella.client.gui.menu;

import dev.stella.client.render.Render2D;
import dev.stella.client.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;

/**
 * The flag of Israel drawn with shapes: white field, two horizontal blue stripes and the blue Star of David
 * (proportions from the official 220 x 160 design: stripes 25 units tall, 15 units from the edges). The full-screen
 * version waves gently, with soft light and shade along the folds.
 */
public final class IsraelFlag {
    private IsraelFlag() {}

    public static final int BLUE = 0xFF0038B8;
    public static final int LIGHT_BLUE = 0xFF6FB6FF;
    public static final int WHITE = 0xFFFFFFFF;

    /** Full-screen waving flag. */
    public static void render(DrawContext ctx, int w, int h, double alpha) {
        double t = System.currentTimeMillis() / 1000.0;
        int cols = Math.max(48, Math.min(160, w / 4));
        float cw = w / (float) cols;
        float amp = h * 0.012f;
        float s1y = h * 15f / 160f, s2y = h * 120f / 160f, sh = h * 25f / 160f;
        for (int i = 0; i < cols; i++) {
            double u = i / (double) cols;
            double ph = u * 7.0 - t * 1.6;
            float off = (float) (Math.sin(ph) * amp * (0.35 + 0.65 * u)); // the pole side (left) moves less
            float x = i * cw;
            Render2D.rect(ctx, x, -amp * 2 + off, cw + 0.6f, h + amp * 4, ColorUtil.mulAlpha(WHITE, alpha));
            Render2D.rect(ctx, x, s1y + off, cw + 0.6f, sh, ColorUtil.mulAlpha(BLUE, alpha));
            Render2D.rect(ctx, x, s2y + off, cw + 0.6f, sh, ColorUtil.mulAlpha(BLUE, alpha));
            // light and shade along the folds
            double shade = Math.cos(ph);
            int a = (int) (Math.abs(shade) * 34 * (0.35 + 0.65 * u));
            int c = shade > 0 ? ColorUtil.withAlpha(0xFF000814, a) : ColorUtil.withAlpha(0xFFFFFFFF, a / 2);
            Render2D.rect(ctx, x, -amp * 2, cw + 0.6f, h + amp * 4, ColorUtil.mulAlpha(c, alpha));
        }
        // Star of David, moving with the cloth at the centre
        double ph = 0.5 * 7.0 - t * 1.6;
        float off = (float) (Math.sin(ph) * amp * (0.35 + 0.65 * 0.5));
        float r = h * 31f / 160f;
        star(ctx, w / 2f, h / 2f + off, r, h * 5.2f / 160f, ColorUtil.mulAlpha(BLUE, alpha));
    }

    /**
     * Star of David: two interlaced equilateral triangles drawn as outlines of the given stroke width, with
     * mitred corners (each edge is extended by width * cos(30 deg) so the outer edges meet in a point).
     * {@code r} = centre to tip.
     */
    public static void star(DrawContext ctx, float cx, float cy, float r, float width, int color) {
        for (int tri = 0; tri < 2; tri++) {
            double rot = tri == 0 ? -Math.PI / 2 : Math.PI / 2; // up-pointing, then down-pointing
            double[] xs = new double[3], ys = new double[3];
            for (int i = 0; i < 3; i++) {
                double a = rot + i * Math.PI * 2 / 3;
                xs[i] = cx + Math.cos(a) * (r - width * 0.5);
                ys[i] = cy + Math.sin(a) * (r - width * 0.5);
            }
            for (int i = 0; i < 3; i++) {
                int j = (i + 1) % 3;
                double dx = xs[j] - xs[i], dy = ys[j] - ys[i], len = Math.sqrt(dx * dx + dy * dy);
                double ext = width * 0.866 / len;
                Render2D.line(ctx, xs[i] - dx * ext, ys[i] - dy * ext, xs[j] + dx * ext, ys[j] + dy * ext, width, color);
            }
        }
    }

    /** Small logo: a Star of David with a soft glow (used where Stella shows its star). */
    public static void logo(DrawContext ctx, float cx, float cy, float r, int color, int glow, double alpha) {
        if ((glow >>> 24) != 0) Render2D.glow(ctx, cx, cy, r * 2.2f, ColorUtil.mulAlpha(glow, alpha));
        star(ctx, cx, cy, r, Math.max(1f, r * 0.17f), ColorUtil.mulAlpha(color, alpha));
    }
}
