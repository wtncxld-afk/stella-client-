package dev.stella.client.render;

import dev.stella.client.gui.theme.ThemeManager;
import dev.stella.client.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;

/**
 * "Liquid Glass" surfaces: a frosted translucent body, a bright specular rim (strong at the top-left,
 * fading round the edge), a soft sheen across the top half, a slow sliding light band and a gentle
 * shadow. Used by every GUI panel and HUD card while the Liquid Glass theme is active.
 */
public final class Glass {
    private Glass() {}

    public static boolean active() { return ThemeManager.isGlass(); }

    /** A glass panel; {@code header} > 0 adds a slightly brighter header strip of that height. */
    public static void panel(DrawContext ctx, float x, float y, float w, float h, float r, float header, double alpha) {
        panel(ctx, x, y, w, h, r, header, alpha, false);
    }

    /** {@code dense}: more frosted body for windows that sit on top of other panels (settings, popups). */
    public static void panel(DrawContext ctx, float x, float y, float w, float h, float r, float header, double alpha, boolean dense) {
        int a = (int) (255 * Math.max(0, Math.min(1, alpha)));
        if (a <= 2) return;
        // soft shadow under the glass
        Render2D.shadow(ctx, x, y + 1, w, h, r, 5, (Math.min(255, a * 70 / 255) << 24));
        // rim: a hair larger, bright and cool
        Render2D.roundRect(ctx, x - 0.75f, y - 0.75f, w + 1.5f, h + 1.5f, r + 0.75f, mul(0x66F2F8FF, a));
        // frosted body: cool translucent tint, a little darker toward the bottom for depth
        Render2D.roundRect(ctx, x, y, w, h, r, mul(dense ? 0xD81C2434 : 0x58222C3E, a));
        verticalFade(ctx, x + 0.5f, y + h * 0.45f, w - 1, h * 0.55f - r * 0.6f, 0x00000000, mul(0x30060A14, a));
        // top sheen (the "liquid" highlight)
        verticalFade(ctx, x + r * 0.6f, y + 1, w - r * 1.2f, Math.min(h * 0.5f, 26), mul(0x2EFFFFFF, a), 0x00FFFFFF);
        if (header > 0) verticalFade(ctx, x + 1, y + 1, w - 2, header - 1, mul(0x22FFFFFF, a), mul(0x0AFFFFFF, a));
        // specular edge lines: bright top edge, softer left edge, faint bottom glint
        Render2D.hGradient(ctx, x + r, y + 0.5f, w - 2 * r, 1, mul(0xD0FFFFFF, a), mul(0x40FFFFFF, a));
        verticalFade(ctx, x + 0.5f, y + r, 1, h - 2 * r, mul(0x80FFFFFF, a), mul(0x10FFFFFF, a));
        Render2D.hGradient(ctx, x + r, y + h - 1.5f, w - 2 * r, 1, mul(0x10FFFFFF, a), mul(0x50FFFFFF, a));
        verticalFade(ctx, x + w - 1.5f, y + r, 1, h - 2 * r, mul(0x08FFFFFF, a), mul(0x38FFFFFF, a));
        // slow sliding light band (refraction glint), clipped to the panel
        double t = (System.currentTimeMillis() % 7000L) / 7000.0;
        float bw = Math.max(18, w * 0.22f);
        float bx = (float) (x - bw + (w + bw * 2) * t);
        float x0 = Math.max(x + 1, bx), x1 = Math.min(x + w - 1, bx + bw);
        if (x1 > x0) {
            float mid = bx + bw / 2;
            if (mid > x0) Render2D.hGradient(ctx, x0, y + r, Math.min(mid, x1) - x0, h - 2 * r, mul(0x00FFFFFF, a), mul(0x14FFFFFF, a));
            if (x1 > mid) Render2D.hGradient(ctx, Math.max(mid, x0), y + r, x1 - Math.max(mid, x0), h - 2 * r, mul(0x14FFFFFF, a), mul(0x00FFFFFF, a));
        }
        // faint accent glow in the bottom-right corner, like light bending through the glass
        int acc = ThemeManager.get().accent();
        verticalFade(ctx, x + w * 0.55f, y + h - Math.min(14, h * 0.4f), w * 0.45f - r, Math.min(14, h * 0.4f) - 1,
                0x00000000, mul(ColorUtil.withAlpha(acc, 0x22), a));
    }

    /** Glass row / button (hover = brighter). */
    public static void row(DrawContext ctx, float x, float y, float w, float h, boolean hover, double alpha) {
        int a = (int) (255 * Math.max(0, Math.min(1, alpha)));
        Render2D.roundRect(ctx, x, y, w, h, Math.min(3, h / 2), mul(hover ? 0x26FFFFFF : 0x0CFFFFFF, a));
        if (hover) Render2D.hGradient(ctx, x + 2, y, w - 4, 1, mul(0x60FFFFFF, a), mul(0x10FFFFFF, a));
    }

    private static void verticalFade(DrawContext ctx, float x, float y, float w, float h, int top, int bottom) {
        if (w <= 0 || h <= 0) return;
        int steps = Math.max(1, Math.min(24, (int) h));
        for (int i = 0; i < steps; i++) {
            float y0 = y + h * i / steps, y1 = y + h * (i + 1) / steps;
            Render2D.rect(ctx, x, y0, w, y1 - y0, ColorUtil.lerp(top, bottom, i / (double) Math.max(1, steps - 1)));
        }
    }

    private static int mul(int argb, int a) {
        int base = (argb >>> 24) & 0xFF;
        return ((base * a / 255) << 24) | (argb & 0xFFFFFF);
    }
}
