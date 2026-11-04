package dev.stella.client.gui.menu;

import dev.stella.client.gui.theme.ThemeManager;
import dev.stella.client.module.ModuleManager;
import dev.stella.client.modules.client.MenusModule;
import dev.stella.client.render.Render2D;
import dev.stella.client.render.StellaFont;
import dev.stella.client.util.Animation;
import dev.stella.client.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;

/**
 * Stella loading design. A glowing star pops in at the centre, wrapped in a circular progress ring with a
 * comet head, two tilted orbit rings and pulsing shockwaves; lightning crackles around it, petals drift past,
 * and the whole thing bursts when loading finishes. Everything up to the text is drawn with plain shapes,
 * so it also works during the very first resource load, before fonts and textures exist.
 */
public final class LoadingDesign {
    private static final CosmicBackground BG = new CosmicBackground(220);
    private static final MenuFx FX = new MenuFx();
    private static final String[] TIPS = {
            "Right Shift opens the ClickGUI",
            "Middle-click a module to bind it to a key",
            "Switch between window and dropdown view at the bottom of the ClickGUI",
            "Every theme has its own sky - try Custom Sky",
            "Share your config with a share code from the Configs menu",
            "The HUD editor lives in the pause menu",
            "Press your knife's inspect key to show it off",
            "Region Map + Radar sit in the top-left by default",
    };
    private static float shownProgress;
    private static long sessionStart, lastFrame;
    private static boolean finished;
    private static float finishFlash;

    private LoadingDesign() {}

    public static boolean enabled() {
        MenusModule m = ModuleManager.get(MenusModule.class);
        return m == null || m.loadingScreens.isOn();
    }

    /** Restarts the intro animation when a new loading screen appears (after a pause of more than a second). */
    private static double intro() {
        long now = System.currentTimeMillis();
        if (now - lastFrame > 1000) { sessionStart = now; shownProgress = 0; finished = false; finishFlash = 0; }
        lastFrame = now;
        return (now - sessionStart) / 1000.0;
    }

    /** Full-screen background with the logo (no text): used behind screens that draw their own text/buttons. */
    public static void background(DrawContext ctx, int w, int h, double alpha) {
        double age = intro();
        BG.render(ctx, w, h, alpha, true);
        float cx = w / 2f, cy = h * 0.24f, r = Math.min(w, h) * 0.07f;
        float pop = (float) Animation.easeOutBack(Math.min(1, age / 0.6));
        FX.focus(cx, cy, r * 2.2f).density(0.7f).render(ctx, w, h, alpha);
        ring(ctx, cx, cy, r * 1.75f * pop, -1, alpha, Math.max(1.2f, r / 9f));
        star(ctx, cx, cy, r * pop, alpha);
    }

    /**
     * The complete loading screen. {@code progress} 0..1, or negative for an indeterminate ring.
     * {@code text} is false during the first resource load (fonts not ready yet).
     */
    public static void full(DrawContext ctx, int w, int h, double alpha, float progress, String title, String status, boolean text) {
        double age = intro();
        long now = System.currentTimeMillis();
        BG.render(ctx, w, h, alpha, true);
        float cx = w / 2f, cy = h * 0.40f, size = Math.min(w, h) * 0.10f;
        float pop = (float) Animation.easeOutBack(Math.min(1, age / 0.7));
        float ringR = size * 1.85f * pop;
        FX.focus(cx, cy, ringR * 1.15f).density(1f).render(ctx, w, h, alpha);

        // pulsing shockwaves
        for (int i = 0; i < 2; i++) {
            double k = ((now / 2200.0) + i * 0.5) % 1.0;
            float rr = ringR * (float) (1 + k * 1.6);
            circle(ctx, cx, cy, rr, 1f, ColorUtil.mulAlpha(ColorUtil.withAlpha(ThemeManager.get().accent(), (int) (70 * (1 - k))), alpha), 64);
        }
        orbits(ctx, cx, cy, ringR, alpha);

        if (progress >= 0) shownProgress += (Math.max(0, Math.min(1, progress)) - shownProgress) * 0.12f;
        ring(ctx, cx, cy, ringR, progress < 0 ? -1 : shownProgress, alpha, Math.max(1.6f, size / 8f));
        star(ctx, cx, cy, size * pop, alpha);

        // finish: burst + white flash ring
        if (progress >= 0.995f && !finished) {
            finished = true;
            finishFlash = 1;
            FX.burst(cx, cy, 40, ThemeManager.get().accent());
            for (int i = 0; i < 4; i++) {
                double a = i * Math.PI / 2 + Math.random();
                FX.boltBetween(cx + (float) Math.cos(a) * ringR, cy + (float) Math.sin(a) * ringR,
                        cx + (float) Math.cos(a) * ringR * 2.1f, cy + (float) Math.sin(a) * ringR * 2.1f, 0xFFE9D5FF);
            }
        }
        if (finishFlash > 0) {
            circle(ctx, cx, cy, ringR * (2.4f - finishFlash * 1.2f), 2.5f * finishFlash, ColorUtil.mulAlpha(0xFFFFFFFF, alpha * finishFlash), 72);
            finishFlash = Math.max(0, finishFlash - 0.03f);
        }

        float bw = Math.min(200, w * 0.42f), bx = cx - bw / 2f, by = cy + ringR + size * 1.3f;
        bar(ctx, bx, by, bw, alpha, progress);
        if (!text) return;
        double tIn = Math.max(0, Math.min(1, (age - 0.25) / 0.5));
        double ta = alpha * Animation.easeOutCubic(tIn);
        float slide = (float) (1 - Animation.easeOutCubic(tIn)) * 8;
        if (progress >= 0) {
            String pct = Math.round(shownProgress * 100) + "%";
            float s = 1.1f;
            Render2D.textScaled(ctx, pct, cx - StellaFont.width(pct) * s / 2f, cy + ringR + 4, s, ColorUtil.mulAlpha(0xFFFFFFFF, ta), true);
        }
        if (title != null) {
            // letter-spaced title with a shimmer travelling across it
            float s = 1.5f;
            int tw = 0;
            for (char c : title.toCharArray()) tw += StellaFont.width(String.valueOf(c)) + 2;
            float x = cx - tw * s / 2f;
            float ty = h * 0.1f + slide;
            for (int i = 0; i < title.length(); i++) {
                String ch = String.valueOf(title.charAt(i));
                double p = i / (double) Math.max(1, title.length() - 1);
                double shimmer = Math.max(0, 1 - Math.abs(p - ((now % 2400) / 2400.0 * 1.6 - 0.3)) * 5);
                int c = ColorUtil.lerp(ThemeManager.accentAt(p * 0.5), 0xFFFFFFFF, 0.55 + 0.45 * shimmer);
                Render2D.textScaled(ctx, ch, x, ty, s, ColorUtil.mulAlpha(c, ta), true);
                x += (StellaFont.width(ch) + 2) * s;
            }
        }
        if (status != null && !status.isEmpty()) {
            String dots = ".".repeat((int) ((now / 400) % 4));
            String st = status + dots;
            float sw = StellaFont.width(status) * 0.85f;
            Render2D.textScaled(ctx, st, cx - sw / 2f, by + 9 + slide, 0.85f, ColorUtil.mulAlpha(0xFFC9B8E6, ta), false);
        }
        // rotating tip with crossfade
        long slot = (now - sessionStart) / 4500;
        double phase = ((now - sessionStart) % 4500) / 4500.0;
        double tipA = Math.min(1, Math.min(phase / 0.12, (1 - phase) / 0.12));
        String tip = "TIP  " + TIPS[(int) (slot % TIPS.length)];
        float ts = 0.75f;
        Render2D.textScaled(ctx, tip, cx - StellaFont.width(tip) * ts / 2f, h - 22, ts, ColorUtil.mulAlpha(0xFF9A8FB0, ta * tipA), false);
    }

    private static void bar(DrawContext ctx, float x, float y, float w, double alpha, float progress) {
        long now = System.currentTimeMillis();
        Render2D.roundRect(ctx, x, y, w, 3, 1.5f, ColorUtil.mulAlpha(0xFF1B1030, alpha));
        int a1 = ThemeManager.accentAt(0), a2 = ThemeManager.accentAt(0.5);
        if (progress < 0) {
            double t = (Math.sin(now / 520.0) + 1) / 2;
            float sw = w * 0.28f, sx = (float) (x + (w - sw) * t);
            Render2D.roundRect(ctx, sx - 3, y - 2, sw + 6, 7, 3.5f, ColorUtil.mulAlpha(ColorUtil.withAlpha(a1, 0x40), alpha));
            Render2D.hGradient(ctx, sx, y, sw, 3, ColorUtil.mulAlpha(a1, alpha), ColorUtil.mulAlpha(a2, alpha));
            return;
        }
        float fw = Math.max(3, w * shownProgress);
        Render2D.roundRect(ctx, x - 2, y - 2, fw + 4, 7, 3.5f, ColorUtil.mulAlpha(ColorUtil.withAlpha(a1, 0x40), alpha));
        Render2D.hGradient(ctx, x, y, fw, 3, ColorUtil.mulAlpha(a1, alpha), ColorUtil.mulAlpha(a2, alpha));
        float shine = (float) ((now % 1400) / 1400.0) * fw;
        Render2D.hGradient(ctx, x + Math.max(0, shine - 18), y, Math.min(18, shine), 3, 0, ColorUtil.mulAlpha(0x90FFFFFF, alpha));
        Render2D.roundRect(ctx, x + fw - 2.5f, y - 1, 5, 5, 2.5f, ColorUtil.mulAlpha(0xFFFFFFFF, alpha));
    }

    /** Circular progress ring with a gradient arc and a glowing comet head. progress < 0 = spinning arc. */
    private static void ring(DrawContext ctx, float cx, float cy, float r, float progress, double alpha, float width) {
        if (r < 2) return;
        long now = System.currentTimeMillis();
        int segs = 90;
        circle(ctx, cx, cy, r, width, ColorUtil.mulAlpha(0x40FFFFFF, alpha * 0.35), segs);
        double start, sweep;
        if (progress < 0) {
            start = now / 380.0;
            sweep = Math.PI * (0.55 + 0.35 * Math.sin(now / 700.0));
        } else {
            start = -Math.PI / 2;
            sweep = Math.PI * 2 * progress;
        }
        int n = Math.max(1, (int) (segs * sweep / (Math.PI * 2)));
        double prevX = cx + Math.cos(start) * r, prevY = cy + Math.sin(start) * r;
        for (int i = 1; i <= n; i++) {
            double k = i / (double) n;
            double a = start + sweep * k;
            double x = cx + Math.cos(a) * r, y = cy + Math.sin(a) * r;
            int c = ThemeManager.accentAt(k * 0.5);
            Render2D.line(ctx, prevX, prevY, x, y, width * 2.6f, ColorUtil.mulAlpha(ColorUtil.withAlpha(c, 0x30), alpha * k));
            Render2D.line(ctx, prevX, prevY, x, y, width, ColorUtil.mulAlpha(ColorUtil.lerp(c, 0xFFFFFFFF, 0.15 * k), alpha * (0.35 + 0.65 * k)));
            prevX = x; prevY = y;
        }
        // comet head
        float hs = width * 1.8f;
        Render2D.roundRect(ctx, (float) prevX - hs * 2, (float) prevY - hs * 2, hs * 4, hs * 4, hs * 2, ColorUtil.mulAlpha(ColorUtil.withAlpha(ThemeManager.accentAt(0.5), 0x50), alpha));
        Render2D.roundRect(ctx, (float) prevX - hs / 2, (float) prevY - hs / 2, hs, hs, hs / 2, ColorUtil.mulAlpha(0xFFFFFFFF, alpha));
    }

    /** Two tilted, counter-rotating dashed orbit ellipses with a moon riding each. */
    private static void orbits(DrawContext ctx, float cx, float cy, float r, double alpha) {
        double t = System.currentTimeMillis() / 1000.0;
        for (int o = 0; o < 2; o++) {
            double tilt = o == 0 ? 0.45 : -0.6;
            float rx = r * (o == 0 ? 1.45f : 1.3f), ry = rx * 0.32f;
            double spin = (o == 0 ? 1 : -1.3) * t * 0.9;
            double ct = Math.cos(tilt), st = Math.sin(tilt);
            int segs = 48;
            for (int i = 0; i < segs; i += 2) { // dashed
                double a1 = i * Math.PI * 2 / segs + spin * 0.15, a2 = (i + 1) * Math.PI * 2 / segs + spin * 0.15;
                double x1 = Math.cos(a1) * rx, y1 = Math.sin(a1) * ry, x2 = Math.cos(a2) * rx, y2 = Math.sin(a2) * ry;
                double front = Math.sin(a1) > 0 ? 1 : 0.45;
                Render2D.line(ctx, cx + x1 * ct - y1 * st, cy + x1 * st + y1 * ct, cx + x2 * ct - y2 * st, cy + x2 * st + y2 * ct,
                        0.8f, ColorUtil.mulAlpha(ColorUtil.withAlpha(ThemeManager.accentAt(o * 0.4), 0x70), alpha * front));
            }
            double mx = Math.cos(spin) * rx, my = Math.sin(spin) * ry;
            float px = (float) (cx + mx * ct - my * st), py = (float) (cy + mx * st + my * ct);
            float s = Math.max(1.5f, r / 14f) * (Math.sin(spin) > 0 ? 1.2f : 0.8f);
            Render2D.roundRect(ctx, px - s * 2, py - s * 2, s * 4, s * 4, s * 2, ColorUtil.mulAlpha(ColorUtil.withAlpha(ThemeManager.accentAt(0.5), 0x45), alpha));
            Render2D.roundRect(ctx, px - s / 2, py - s / 2, s, s, s / 2, ColorUtil.mulAlpha(0xFFFFFFFF, alpha));
        }
    }

    private static void circle(DrawContext ctx, float cx, float cy, float r, float width, int color, int segs) {
        if (r < 1 || (color >>> 24) == 0) return;
        double px = cx + r, py = cy;
        for (int i = 1; i <= segs; i++) {
            double a = i * Math.PI * 2 / segs;
            double x = cx + Math.cos(a) * r, y = cy + Math.sin(a) * r;
            Render2D.line(ctx, px, py, x, y, width, color);
            px = x; py = y;
        }
    }

    /** Glowing five-point star with a soft halo, gentle rotation and orbiting sparks. */
    public static void star(DrawContext ctx, float cx, float cy, float r, double alpha) {
        if (r < 1) return;
        long now = System.currentTimeMillis();
        double pulse = 0.88 + 0.12 * Math.sin(now / 420.0);
        int accent = ThemeManager.get().accent();
        // halo rings
        for (int i = 6; i >= 1; i--) {
            float rr = (float) (r * (1.1 + i * 0.28) * pulse);
            Render2D.roundRect(ctx, cx - rr, cy - rr, rr * 2, rr * 2, rr, ColorUtil.mulAlpha(ColorUtil.withAlpha(accent, 0x16), alpha * (1.0 - i * 0.12)));
        }
        // light rays rotating behind the star
        double rot = Math.sin(now / 2600.0) * 0.18;
        for (int i = 0; i < 8; i++) {
            double a = now / 6000.0 + i * Math.PI / 4;
            float len = r * (1.5f + 0.3f * (float) Math.sin(now / 500.0 + i));
            Render2D.line(ctx, cx, cy, cx + Math.cos(a) * len, cy + Math.sin(a) * len, r * 0.16f,
                    ColorUtil.mulAlpha(ColorUtil.withAlpha(ColorUtil.lerp(accent, 0xFFFFFFFF, 0.5), 0x22), alpha));
        }
        // filled star: nested outlines from the edge inward, theme colour -> white
        int edge = ColorUtil.lerp(accent, 0xFFFFFFFF, 0.25);
        int layers = Math.max(6, (int) (r / 2));
        for (int l = 0; l <= layers; l++) {
            double k = 1 - l / (double) layers;
            int c = ColorUtil.lerp(edge, 0xFFFFFFFF, Math.pow(1 - k, 1.4));
            starOutline(ctx, cx, cy, (float) (r * k * pulse), rot, Math.max(1.2f, r / 10f), ColorUtil.mulAlpha(c, alpha));
        }
        // a glint sweeping across the star
        double g = (now % 3000) / 3000.0;
        if (g < 0.35) {
            double k = g / 0.35;
            float gx = cx - r + (float) (k * r * 2);
            Render2D.line(ctx, gx - r * 0.25, cy + r * 0.5, gx + r * 0.25, cy - r * 0.5, r * 0.12f, ColorUtil.mulAlpha(0x80FFFFFF, alpha * Math.sin(Math.PI * k)));
        }
        // orbiting sparks
        for (int i = 0; i < 3; i++) {
            double a = now / 900.0 + i * Math.PI * 2 / 3;
            float ox = cx + (float) Math.cos(a) * r * 1.5f, oy = cy + (float) Math.sin(a) * r * 0.6f;
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
