package dev.stella.client.gui.menu;

import dev.stella.client.gui.theme.ThemeManager;
import dev.stella.client.render.Render2D;
import dev.stella.client.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;

import java.util.Random;

/**
 * Animated space backdrop used behind the Stella menus and loading screens: deep gradient, drifting nebula
 * glows, theme-tinted aurora ribbons, three parallax star layers (the brightest stars get cross glints),
 * occasional shooting stars and a soft vignette. Layers can follow the mouse for a parallax effect.
 */
public final class CosmicBackground {
    private final float[][] stars;
    private final float[][] blobs;
    private final long start = System.currentTimeMillis();
    private final Random rnd = new Random();
    private float px, py;                      // smoothed parallax offset (-1..1)
    private float shootX, shootY, shootVX, shootVY, shootAge = 99, shootLife;
    private double nextShoot = 2.5;
    private long last;

    public CosmicBackground(int starCount) {
        Random r = new Random(0x5751L);
        stars = new float[starCount][];
        // x, y, size, twinkle phase, brightness, depth layer (0 far .. 2 near)
        for (int i = 0; i < starCount; i++)
            stars[i] = new float[]{r.nextFloat(), r.nextFloat(), 0.3f + r.nextFloat() * 1.2f, r.nextFloat() * 6.28f, 0.2f + r.nextFloat(), r.nextInt(3)};
        blobs = new float[6][];
        for (int i = 0; i < blobs.length; i++) blobs[i] = new float[]{r.nextFloat(), r.nextFloat(), 0.25f + r.nextFloat() * 0.35f, r.nextFloat() * 6.28f};
    }

    public void render(DrawContext ctx, int w, int h, double alpha, boolean opaque) { render(ctx, w, h, alpha, opaque, 0, 0, true); }

    /** @param mx,my mouse position as -1..1 from the centre (0 = no parallax) */
    public void render(DrawContext ctx, int w, int h, double alpha, boolean opaque, float mx, float my, boolean aurora) {
        long nowMs = System.currentTimeMillis();
        float dt = last == 0 ? 0.016f : Math.min(0.05f, (nowMs - last) / 1000f);
        last = nowMs;
        double t = (nowMs - start) / 1000.0;
        px += (mx - px) * Math.min(1, dt * 4);
        py += (my - py) * Math.min(1, dt * 4);
        int accent = ThemeManager.get().accent();
        int accent2 = ThemeManager.accentAt(0.5);
        if (opaque) {
            ctx.fillGradient(0, 0, w, h, ColorUtil.mulAlpha(ColorUtil.lerp(0xFF07040E, accent, 0.04), alpha),
                    ColorUtil.mulAlpha(ColorUtil.lerp(0xFF130A24, accent, 0.10), alpha));
        }
        // drifting nebula glows (stacked soft circles), far layer
        for (int i = 0; i < blobs.length; i++) {
            float[] b = blobs[i];
            double cx = (b[0] + 0.04 * Math.sin(t * 0.07 + b[3])) * w - px * 6;
            double cy = (b[1] + 0.04 * Math.cos(t * 0.05 + b[3])) * h - py * 4;
            float rad = b[2] * Math.max(w, h) * 0.5f * (float) (1 + 0.06 * Math.sin(t * 0.3 + b[3]));
            int c = i % 2 == 0 ? accent : ColorUtil.lerp(accent2, 0xFFEC4899, 0.35);
            for (int k = 6; k >= 1; k--) {
                float rr = rad * k / 6f;
                Render2D.roundRect(ctx, (float) cx - rr, (float) cy - rr, rr * 2, rr * 2, rr, ColorUtil.mulAlpha(ColorUtil.withAlpha(c, 10), alpha));
            }
        }
        if (aurora) aurora(ctx, w, h, t, alpha, accent, accent2);
        // stars: three depth layers, nearer ones drift faster and move more with the mouse
        for (float[] s : stars) {
            float depth = s[5];
            double speed = 0.002 + depth * 0.003;
            double x = (((s[0] + t * speed * s[4]) % 1.0) * w) - px * (3 + depth * 7);
            double y = s[1] * h - py * (2 + depth * 5);
            double tw = 0.5 + 0.5 * Math.sin(t * (1.2 + depth * 0.5) + s[3]);
            int a = Math.min(255, (int) (40 + 200 * tw * s[4] / 1.2));
            float sz = s[2] * (0.8f + depth * 0.25f);
            int col = ColorUtil.mulAlpha((a << 24) | 0xF3E8FF, alpha);
            Render2D.rect(ctx, (float) x, (float) y, sz, sz, col);
            if (s[4] > 1.05f && depth == 2 && tw > 0.6) { // glint on the brightest near stars
                float g = (float) ((tw - 0.6) / 0.4) * 3.2f;
                int gc = ColorUtil.mulAlpha(ColorUtil.withAlpha(0xFFFFFFFF, (int) (a * 0.55)), alpha);
                Render2D.rect(ctx, (float) x - g + sz / 2, (float) y + sz / 2 - 0.25f, g * 2, 0.5f, gc);
                Render2D.rect(ctx, (float) x + sz / 2 - 0.25f, (float) y - g + sz / 2, 0.5f, g * 2, gc);
            }
        }
        shootingStar(ctx, w, h, dt, alpha, accent);
        // vignette
        ctx.fillGradient(0, (int) (h * 0.6), w, h, 0x00000000, ColorUtil.mulAlpha(0x90000000, alpha));
        ctx.fillGradient(0, 0, w, (int) (h * 0.18), ColorUtil.mulAlpha(0x50000000, alpha), 0x00000000);
    }

    /** Two slow ribbons of light waving across the upper half, tinted with the theme. */
    private void aurora(DrawContext ctx, int w, int h, double t, double alpha, int c1, int c2) {
        int step = Math.max(3, w / 160);
        for (int band = 0; band < 2; band++) {
            int col = band == 0 ? c1 : c2;
            double base = h * (0.22 + band * 0.12);
            for (int x = 0; x < w; x += step) {
                double u = x / (double) w;
                double y = base + Math.sin(u * 5.5 + t * 0.35 + band * 2) * h * 0.05 + Math.sin(u * 13 + t * 0.6) * h * 0.012 - py * 5;
                double bright = 0.5 + 0.5 * Math.sin(u * 9 - t * 0.8 + band * 1.7);
                int a = (int) (16 + 34 * bright);
                int hgt = (int) (h * (0.07 + 0.05 * bright));
                int xx = (int) (x - px * 5);
                ctx.fillGradient(xx, (int) y - hgt, xx + step, (int) y, 0x00000000, ColorUtil.mulAlpha(ColorUtil.withAlpha(col, a), alpha));
                ctx.fillGradient(xx, (int) y, xx + step, (int) y + 3, ColorUtil.mulAlpha(ColorUtil.withAlpha(ColorUtil.lerp(col, 0xFFFFFFFF, 0.4), a + 20), alpha), 0x00000000);
            }
        }
    }

    private void shootingStar(DrawContext ctx, int w, int h, float dt, double alpha, int accent) {
        nextShoot -= dt;
        if (nextShoot <= 0 && shootAge >= shootLife) {
            shootX = rnd.nextFloat() * w * 0.8f + w * 0.1f;
            shootY = rnd.nextFloat() * h * 0.35f;
            double ang = Math.toRadians(150 + rnd.nextFloat() * 25);
            float sp = Math.max(w, h) * (0.9f + rnd.nextFloat() * 0.5f);
            shootVX = (float) Math.cos(ang) * sp; shootVY = (float) Math.sin(ang) * sp; // down and to the left
            shootAge = 0; shootLife = 0.7f + rnd.nextFloat() * 0.4f;
            nextShoot = 3 + rnd.nextFloat() * 5;
        }
        if (shootAge >= shootLife) return;
        shootAge += dt;
        float k = shootAge / shootLife;
        float x = shootX + shootVX * shootAge, y = shootY + shootVY * shootAge;
        double a = alpha * Math.sin(Math.PI * Math.min(1, k));
        float tail = 0.09f;
        Render2D.line(ctx, x, y, x - shootVX * tail, y - shootVY * tail, 2.4f, ColorUtil.mulAlpha(ColorUtil.withAlpha(accent, 0x40), a));
        Render2D.line(ctx, x, y, x - shootVX * tail * 0.7, y - shootVY * tail * 0.7, 0.9f, ColorUtil.mulAlpha(0xD0FFFFFF, a));
        Render2D.rect(ctx, x - 1, y - 1, 2, 2, ColorUtil.mulAlpha(0xFFFFFFFF, a));
    }
}
