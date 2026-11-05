package dev.stella.client.gui.menu;

import dev.stella.client.gui.theme.ThemeManager;
import dev.stella.client.module.ModuleManager;
import dev.stella.client.modules.client.MenusModule;
import dev.stella.client.render.Render2D;
import dev.stella.client.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Screen-space particle effects for the Stella menus and loading screens: drifting sakura petals that
 * flutter in 3D, small crackling lightning bolts (with branches, glow and flicker), twinkling sparkles
 * and click bursts. Everything is drawn with plain shapes, so it also works during the first resource
 * load before textures and fonts exist. One instance per screen; call {@link #render} every frame.
 */
public final class MenuFx {
    private static final int[] PETAL_COLORS = {0xFFFFB7D5, 0xFFF9A8D4, 0xFFFBCFE8, 0xFFFFC4DD, 0xFFF472B6};

    private final Random rnd = new Random();
    private final List<Petal> petals = new ArrayList<>();
    private final List<Bolt> bolts = new ArrayList<>();
    private final List<Spark> sparks = new ArrayList<>();
    private long last;
    private double boltTimer = 1.2;
    private float density = 1f;

    /** Region bolts like to strike around (e.g. the logo); null = anywhere. */
    private float focusX = -1, focusY = -1, focusR;

    private static final class Petal { float x, y, vx, vy, rot, vrot, flip, vflip, size, sway, phase; int color; }
    private static final class Bolt { float[] xs, ys; float[][] branch; float age, life, width; int color; }
    private static final class Spark { float x, y, vx, vy, age, life, size; int color; boolean star; }

    public MenuFx() {}

    /** Overall amount multiplier (0.5 = half). */
    public MenuFx density(float d) { this.density = d; return this; }

    /** Bolts prefer striking around this point. */
    public MenuFx focus(float x, float y, float r) { focusX = x; focusY = y; focusR = r; return this; }

    // ------------------------------------------------------------------ settings

    private static MenusModule cfg() {
        try { return ModuleManager.get(MenusModule.class); } catch (Throwable t) { return null; }
    }

    static boolean petalsOn() { MenusModule m = cfg(); return m == null || m.petals.isOn(); }
    static boolean lightningOn() { MenusModule m = cfg(); return m == null || m.lightning.isOn(); }
    static boolean sparklesOn() { MenusModule m = cfg(); return m == null || m.sparkles.isOn(); }
    static float amount() { MenusModule m = cfg(); return m == null ? 1f : m.particleAmount.getFloat(); }

    // ------------------------------------------------------------------ main loop

    public void render(DrawContext ctx, int w, int h, double alpha) {
        long now = System.currentTimeMillis();
        float dt = last == 0 ? 0.016f : Math.min(0.05f, (now - last) / 1000f);
        last = now;
        float amt = amount() * density;
        int accent = ThemeManager.get().accent();

        // ---- petals: keep a steady population drifting down and to the right
        int wantPetals = petalsOn() ? Math.round(Math.max(w, 1) * Math.max(h, 1) / 14000f * amt) : 0;
        wantPetals = Math.min(wantPetals, 60);
        boolean seed = petals.isEmpty();
        while (petals.size() < wantPetals) petals.add(newPetal(w, h, seed)); // first frame: spread over the screen
        for (int i = petals.size() - 1; i >= 0; i--) {
            Petal p = petals.get(i);
            p.phase += dt;
            p.x += (p.vx + (float) Math.sin(p.phase * p.sway) * 14f) * dt;
            p.y += p.vy * dt;
            p.rot += p.vrot * dt;
            p.flip += p.vflip * dt;
            if (p.y > h + 12 || p.x > w + 14 || p.x < -30) {
                if (petals.size() > wantPetals) { petals.remove(i); continue; }
                Petal n = newPetal(w, h, false);
                petals.set(i, n);
                continue;
            }
            drawPetal(ctx, p, alpha);
        }

        // ---- sparkles
        if (sparklesOn() && rnd.nextFloat() < dt * 9f * amt) {
            Spark s = new Spark();
            s.x = rnd.nextFloat() * w; s.y = rnd.nextFloat() * h;
            s.vy = -4 - rnd.nextFloat() * 6; s.vx = rnd.nextFloat() * 4 - 2;
            s.life = 1.2f + rnd.nextFloat() * 1.6f; s.size = 1.5f + rnd.nextFloat() * 2.5f;
            s.color = rnd.nextBoolean() ? 0xFFFFFFFF : ColorUtil.lerp(accent, 0xFFFFFFFF, 0.5);
            s.star = true;
            sparks.add(s);
        }

        // ---- lightning: a small bolt every couple of seconds (sometimes a quick double strike)
        if (lightningOn() && amt > 0) {
            boltTimer -= dt;
            if (boltTimer <= 0) {
                strike(w, h, accent);
                if (rnd.nextFloat() < 0.3f) boltTimer = 0.12 + rnd.nextFloat() * 0.15;
                else boltTimer = (1.4 + rnd.nextFloat() * 2.6) / Math.max(0.3f, amt);
            }
        }
        for (int i = bolts.size() - 1; i >= 0; i--) {
            Bolt b = bolts.get(i);
            b.age += dt;
            if (b.age >= b.life) { bolts.remove(i); continue; }
            drawBolt(ctx, b, alpha);
        }

        for (int i = sparks.size() - 1; i >= 0; i--) {
            Spark s = sparks.get(i);
            s.age += dt;
            if (s.age >= s.life) { sparks.remove(i); continue; }
            s.x += s.vx * dt; s.y += s.vy * dt;
            if (!s.star) { s.vy += 60 * dt; s.vx *= 1 - dt * 2; }
            drawSpark(ctx, s, alpha);
        }
    }

    // ------------------------------------------------------------------ public triggers

    /** Burst of sparks (and a tiny bolt) at a point: used for button clicks and the loading finish. */
    public void burst(float x, float y, int count, int color) {
        for (int i = 0; i < count; i++) {
            Spark s = new Spark();
            double a = rnd.nextDouble() * Math.PI * 2;
            float sp = 30 + rnd.nextFloat() * 90;
            s.x = x; s.y = y; s.vx = (float) Math.cos(a) * sp; s.vy = (float) Math.sin(a) * sp - 20;
            s.life = 0.45f + rnd.nextFloat() * 0.5f; s.size = 1f + rnd.nextFloat() * 1.8f;
            s.color = rnd.nextInt(3) == 0 ? 0xFFFFFFFF : color;
            sparks.add(s);
        }
    }

    /** A lightning bolt between two points (e.g. arcing from the logo). */
    public void boltBetween(float x1, float y1, float x2, float y2, int color) {
        if (!lightningOn()) return;
        bolts.add(makeBolt(x1, y1, x2, y2, color, 1f));
    }

    // ------------------------------------------------------------------ petals

    private Petal newPetal(int w, int h, boolean anywhere) {
        Petal p = new Petal();
        boolean fromLeft = rnd.nextFloat() < 0.35f;
        p.x = anywhere ? rnd.nextFloat() * w : fromLeft ? -10 : rnd.nextFloat() * (w + 40) - 40;
        p.y = anywhere ? rnd.nextFloat() * h : fromLeft ? rnd.nextFloat() * h * 0.6f : -10 - rnd.nextFloat() * 30;
        p.vx = 10 + rnd.nextFloat() * 22;
        p.vy = 12 + rnd.nextFloat() * 20;
        p.rot = rnd.nextFloat() * 6.28f;
        p.vrot = (rnd.nextFloat() - 0.5f) * 2.4f;
        p.flip = rnd.nextFloat() * 6.28f;
        p.vflip = 1.5f + rnd.nextFloat() * 3f;
        p.size = 2.2f + rnd.nextFloat() * 2.8f;
        p.sway = 0.8f + rnd.nextFloat() * 1.4f;
        p.phase = rnd.nextFloat() * 10;
        p.color = PETAL_COLORS[rnd.nextInt(PETAL_COLORS.length)];
        return p;
    }

    /** A petal is an ellipse (a few stacked strokes) whose width follows cos(flip) so it tumbles in 3D. */
    private static void drawPetal(DrawContext ctx, Petal p, double alpha) {
        float flat = (float) Math.abs(Math.cos(p.flip));
        float len = p.size * 1.6f, wid = Math.max(0.5f, p.size * (0.25f + 0.75f * flat));
        double ca = Math.cos(p.rot), sa = Math.sin(p.rot);
        // light side when facing, darker when edge-on
        int c = ColorUtil.lerp(ColorUtil.lerp(p.color, 0xFFB0507A, 0.35), p.color, flat);
        float ax = (float) (ca * len), ay = (float) (sa * len);
        Render2D.line(ctx, p.x - ax, p.y - ay, p.x + ax * 0.9, p.y + ay * 0.9, wid * 0.55f, ColorUtil.mulAlpha(ColorUtil.withAlpha(c, 0xE0), alpha));
        Render2D.line(ctx, p.x - ax * 0.7, p.y - ay * 0.7, p.x + ax * 0.55, p.y + ay * 0.55, wid, ColorUtil.mulAlpha(ColorUtil.withAlpha(c, 0xF0), alpha));
        // highlight vein + a notch at the tip
        Render2D.line(ctx, p.x - ax * 0.5, p.y - ay * 0.5, p.x + ax * 0.4, p.y + ay * 0.4, Math.max(0.4f, wid * 0.22f),
                ColorUtil.mulAlpha(ColorUtil.withAlpha(0xFFFFF1F7, (int) (0x70 * flat)), alpha));
    }

    // ------------------------------------------------------------------ lightning

    private void strike(int w, int h, int accent) {
        float x1, y1;
        if (focusX >= 0) {
            double a = rnd.nextDouble() * Math.PI * 2;
            float r = focusR * (0.9f + rnd.nextFloat() * 0.5f);
            x1 = focusX + (float) Math.cos(a) * r; y1 = focusY + (float) Math.sin(a) * r * 0.7f;
        } else { x1 = rnd.nextFloat() * w; y1 = rnd.nextFloat() * h * 0.7f; }
        double dir = rnd.nextDouble() * Math.PI * 2;
        float len = 26 + rnd.nextFloat() * 46;
        if (focusX >= 0) dir = Math.atan2(y1 - focusY, x1 - focusX) + (rnd.nextDouble() - 0.5) * 0.9; // arcs outward
        float x2 = x1 + (float) Math.cos(dir) * len, y2 = y1 + (float) Math.sin(dir) * len;
        int c = rnd.nextFloat() < 0.5f ? ColorUtil.lerp(accent, 0xFFFFFFFF, 0.35) : ColorUtil.lerp(0xFF93C5FD, accent, 0.4);
        bolts.add(makeBolt(x1, y1, x2, y2, c, 0.8f + rnd.nextFloat() * 0.5f));
        for (int i = 0; i < 6; i++) {
            Spark s = new Spark();
            s.x = x2; s.y = y2;
            double a = rnd.nextDouble() * Math.PI * 2;
            s.vx = (float) Math.cos(a) * 40; s.vy = (float) Math.sin(a) * 40;
            s.life = 0.3f + rnd.nextFloat() * 0.3f; s.size = 1f; s.color = 0xFFFFFFFF;
            sparks.add(s);
        }
    }

    private Bolt makeBolt(float x1, float y1, float x2, float y2, int color, float width) {
        Bolt b = new Bolt();
        int n = 7 + rnd.nextInt(4);
        b.xs = new float[n]; b.ys = new float[n];
        float dx = x2 - x1, dy = y2 - y1, len = (float) Math.sqrt(dx * dx + dy * dy);
        float nx = -dy / Math.max(1, len), ny = dx / Math.max(1, len);
        for (int i = 0; i < n; i++) {
            float t = i / (float) (n - 1);
            float off = (i == 0 || i == n - 1) ? 0 : (rnd.nextFloat() - 0.5f) * len * 0.28f;
            b.xs[i] = x1 + dx * t + nx * off;
            b.ys[i] = y1 + dy * t + ny * off;
        }
        // one or two short forks off the main channel
        int forks = 1 + rnd.nextInt(2);
        b.branch = new float[forks][];
        for (int f = 0; f < forks; f++) {
            int from = 1 + rnd.nextInt(n - 2);
            float bl = len * (0.25f + rnd.nextFloat() * 0.25f);
            double a = Math.atan2(dy, dx) + (rnd.nextBoolean() ? 1 : -1) * (0.5 + rnd.nextDouble() * 0.6);
            float[] pts = new float[8];
            float px = b.xs[from], py = b.ys[from];
            for (int k = 0; k < 4; k++) {
                pts[k * 2] = px; pts[k * 2 + 1] = py;
                px += (float) Math.cos(a) * bl / 3 + (rnd.nextFloat() - 0.5f) * bl * 0.3f;
                py += (float) Math.sin(a) * bl / 3 + (rnd.nextFloat() - 0.5f) * bl * 0.3f;
            }
            b.branch[f] = pts;
        }
        b.life = 0.28f + rnd.nextFloat() * 0.22f;
        b.width = width;
        b.color = color;
        return b;
    }

    private static void drawBolt(DrawContext ctx, Bolt b, double alpha) {
        float k = b.age / b.life;
        // bright flash, a flicker, then fade
        double f = k < 0.15 ? 1 : k < 0.3 ? 0.35 : k < 0.45 ? 0.9 : (1 - k) / 0.55;
        double a = alpha * Math.max(0, f);
        if (a <= 0.01) return;
        int glow = ColorUtil.mulAlpha(ColorUtil.withAlpha(b.color, 0x38), a);
        int mid = ColorUtil.mulAlpha(ColorUtil.withAlpha(b.color, 0xC0), a);
        int core = ColorUtil.mulAlpha(0xFFFFFFFF, a);
        for (int pass = 0; pass < 3; pass++) {
            float wdt = b.width * (pass == 0 ? 4.5f : pass == 1 ? 1.8f : 0.7f);
            int col = pass == 0 ? glow : pass == 1 ? mid : core;
            for (int i = 0; i + 1 < b.xs.length; i++) Render2D.line(ctx, b.xs[i], b.ys[i], b.xs[i + 1], b.ys[i + 1], wdt, col);
            for (float[] br : b.branch)
                for (int i = 0; i + 3 < br.length; i += 2) Render2D.line(ctx, br[i], br[i + 1], br[i + 2], br[i + 3], wdt * 0.6f, col);
        }
        // flash at the strike point
        float ex = b.xs[b.xs.length - 1], ey = b.ys[b.ys.length - 1];
        float r = 5 * b.width * (float) (1 - k);
        if (r > 0.6f) Render2D.glow(ctx, ex, ey, r * 1.6f, ColorUtil.mulAlpha(ColorUtil.withAlpha(b.color, 0xA0), a));
    }

    // ------------------------------------------------------------------ sparks

    private static void drawSpark(DrawContext ctx, Spark s, double alpha) {
        float k = s.age / s.life;
        double a = alpha * (s.star ? Math.sin(Math.PI * k) : 1 - k);
        if (s.star) {
            float r = s.size * (float) (0.6 + 0.4 * Math.sin(Math.PI * k));
            int c = ColorUtil.mulAlpha(s.color, a);
            Render2D.line(ctx, s.x - r * 1.6, s.y, s.x + r * 1.6, s.y, 0.6f, c);
            Render2D.line(ctx, s.x, s.y - r * 1.6, s.x, s.y + r * 1.6, 0.6f, c);
            Render2D.rect(ctx, s.x - 0.6f, s.y - 0.6f, 1.2f, 1.2f, c);
        } else {
            float sz = s.size * (1 - k * 0.5f);
            Render2D.line(ctx, s.x, s.y, s.x - s.vx * 0.03, s.y - s.vy * 0.03, sz, ColorUtil.mulAlpha(s.color, a)); // streak
        }
    }
}
