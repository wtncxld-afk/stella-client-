package dev.stella.client.modules.cosmetics;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.render.Render3D;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.ColorSetting;
import dev.stella.client.setting.ModeSetting;
import dev.stella.client.setting.NumberSetting;
import dev.stella.client.util.ColorUtil;
import net.minecraft.util.math.Vec3d;

/** Animated wings on your back (client-side; visible in third person). */
public class Wings extends Module {
    private final ModeSetting style = add(new ModeSetting("Style", "Wing shape", "Angel", "Angel", "Demon", "Crystal"));
    private final ModeSetting colorMode = add(new ModeSetting("Color", "Theme accent, rainbow or a custom colour", "Theme", "Theme", "Rainbow", "Custom"));
    private final ColorSetting custom = add(new ColorSetting("Custom Color", "", 0xFFE8E0FF)).visibleWhen(() -> colorMode.is("Custom"));
    private final NumberSetting size = add(new NumberSetting("Size", "Wing span", 1.0, 0.5, 1.8, 0.05).suffix("x"));
    private final NumberSetting flap = add(new NumberSetting("Flap Speed", "Flaps per second (faster while in the air)", 0.8, 0.0, 3.0, 0.1));
    private final NumberSetting opacity = add(new NumberSetting("Opacity", "", 0.85, 0.2, 1.0, 0.05));
    private final BoolSetting glow = add(new BoolSetting("Glow", "Soft additive glow on the feathers", true));

    public Wings() { super("Wings", "Animated wings on your back (only you see them, third person)", Category.COSMETICS, false); }

    @Override
    public void onRender3D(Render3D r) {
        if (!Cosmetics.selfVisible()) return;
        var p = mc.player;
        float td = r.tickDelta();
        Cosmetics.Frame f = Cosmetics.Frame.body(p, td);
        double t = Cosmetics.now();
        boolean air = !p.isOnGround();
        double speed = flap.get() * (air ? 2.2 : 1.0);
        double phase = Math.sin(t * Math.PI * 2 * speed);
        // spread (sweep back angle): open when flapping down, folded back a bit when idle
        double sweep = Math.toRadians(air ? 18 + 26 * phase : 32 + 10 * phase);
        double lift = Math.toRadians(air ? 6 * phase : 3 * phase);
        for (int side = -1; side <= 1; side += 2) drawWing(r, f, side, sweep, lift);
    }

    /** Wing-local (u outward, v up) to world for one side. */
    private Vec3d w(Cosmetics.Frame f, int side, double u, double v, double sweep, double lift) {
        double s = size.get();
        u *= s; v *= s;
        double rootX = 0.12, rootY = 1.28, rootZ = -0.17;
        double x = side * (rootX + u * Math.cos(sweep));
        double z = rootZ - u * Math.sin(sweep);
        double y = rootY + v + u * Math.sin(lift);
        return f.at(x, y, z);
    }

    private int c(double t, double alpha) {
        int base = Cosmetics.color(colorMode.get(), custom.get(), t);
        return ColorUtil.withAlpha(base, (int) (255 * alpha * opacity.get()));
    }

    private void drawWing(Render3D r, Cosmetics.Frame f, int side, double sweep, double lift) {
        switch (style.get()) {
            case "Demon" -> demon(r, f, side, sweep, lift);
            case "Crystal" -> crystal(r, f, side, sweep, lift);
            default -> angel(r, f, side, sweep, lift);
        }
    }

    /** Feathered wing: arm bone with layered feathers fanning down and out. */
    private void angel(Render3D r, Cosmetics.Frame f, int side, double sw, double li) {
        double[][] bone = {{0, 0}, {0.42, 0.30}, {0.95, 0.42}, {1.25, 0.30}};
        int n = 11;
        for (int layer = 0; layer < 2; layer++) {
            double lenMul = layer == 0 ? 1.0 : 0.55;
            for (int k = 0; k < n; k++) {
                double tt = k / (double) (n - 1);
                double[] a = along(bone, tt);
                // feathers point down near the body and outwards at the tip
                double ang = Math.toRadians(-100 + 78 * tt);
                double len = (0.42 + 0.38 * Math.sin(Math.PI * Math.min(1, tt * 1.15))) * lenMul;
                double wdt = 0.07;
                double tipU = a[0] + Math.cos(ang) * len, tipV = a[1] + Math.sin(ang) * len;
                double pu = -Math.sin(ang) * wdt, pv = Math.cos(ang) * wdt;
                Vec3d p0 = w(f, side, a[0] - pu, a[1] - pv, sw, li), p1 = w(f, side, a[0] + pu, a[1] + pv, sw, li);
                Vec3d p2 = w(f, side, tipU + pu * 0.4, tipV + pv * 0.4, sw, li), p3 = w(f, side, tipU - pu * 0.4, tipV - pv * 0.4, sw, li);
                int root = layer == 0 ? ColorUtil.lerp(c(tt, 0.95), 0xFFFFFFFF, 0.55) : c(tt, 0.95);
                int tip = layer == 0 ? c(tt, 0.85) : ColorUtil.lerp(c(tt, 0.9), 0xFFFFFFFF, 0.35);
                r.quadWorld(p0, p1, p2, p3, root, root, tip, tip, false);
                if (glow.isOn() && layer == 0) r.quadWorld(p0, p1, p2, p3, ColorUtil.withAlpha(root, 0x20), ColorUtil.withAlpha(root, 0x20), ColorUtil.withAlpha(tip, 0x50), ColorUtil.withAlpha(tip, 0x50), true);
            }
        }
        // arm bone ridge
        for (int k = 0; k < bone.length - 1; k++) {
            Vec3d a = w(f, side, bone[k][0], bone[k][1] + 0.03, sw, li), b = w(f, side, bone[k + 1][0], bone[k + 1][1] + 0.03, sw, li);
            Vec3d c2 = w(f, side, bone[k + 1][0], bone[k + 1][1] - 0.04, sw, li), d = w(f, side, bone[k][0], bone[k][1] - 0.04, sw, li);
            int col = ColorUtil.lerp(c(k / 3.0, 1), 0xFFFFFFFF, 0.7);
            r.quadWorld(a, b, c2, d, col, col, col, col, false);
        }
    }

    /** Bat-style membrane between finger bones, with glowing veins. */
    private void demon(Render3D r, Cosmetics.Frame f, int side, double sw, double li) {
        double[] wrist = {0.45, 0.38};
        double[][] tips = {{1.30, 0.42}, {1.22, -0.05}, {0.98, -0.42}, {0.62, -0.62}, {0.22, -0.55}};
        int skin = ColorUtil.withAlpha(ColorUtil.lerp(0xFF120818, c(0.2, 1), 0.25), (int) (235 * opacity.get()));
        int skin2 = ColorUtil.withAlpha(ColorUtil.lerp(0xFF1E0E2C, c(0.6, 1), 0.35), (int) (200 * opacity.get()));
        Vec3d W = w(f, side, wrist[0], wrist[1], sw, li), R = w(f, side, 0, 0, sw, li);
        // arm
        r.triWorld(R, W, w(f, side, 0.0, 0.12, sw, li), skin, skin, skin, false);
        double[] prev = {0.02, -0.05};
        for (double[] tp : tips) {
            // membrane triangle wrist -> previous tip -> scalloped midpoint -> tip
            double mu = (prev[0] + tp[0]) / 2, mv = (prev[1] + tp[1]) / 2;
            double su = mu + (wrist[0] - mu) * 0.18, sv = mv + (wrist[1] - mv) * 0.18;
            Vec3d a = w(f, side, prev[0], prev[1], sw, li), m = w(f, side, su, sv, sw, li), b = w(f, side, tp[0], tp[1], sw, li);
            r.quadWorld(W, a, m, b, skin, skin2, skin2, skin2, false);
            prev = tp;
        }
        r.triWorld(R, W, w(f, side, 0.02, -0.05, sw, li), skin, skin, skin2, false);
        // veins
        for (double[] tp : tips) bar(r, f, side, wrist, tp, 0.022, c(0.3, 1), sw, li);
        bar(r, f, side, new double[]{0, 0}, wrist, 0.035, c(0.1, 1), sw, li);
    }

    /** Faceted shards that shimmer. */
    private void crystal(Render3D r, Cosmetics.Frame f, int side, double sw, double li) {
        double t = Cosmetics.now();
        int n = 7;
        for (int k = 0; k < n; k++) {
            double tt = k / (double) (n - 1);
            double ang = Math.toRadians(-70 + 85 * tt);
            double len = 0.55 + 0.6 * Math.sin(Math.PI * (0.25 + tt * 0.6));
            double bu = 0.08 + tt * 0.35, bv = 0.12 + tt * 0.18;
            double tu = bu + Math.cos(ang) * len, tv = bv + Math.sin(ang) * len;
            double wid = 0.09;
            double pu = -Math.sin(ang) * wid, pv = Math.cos(ang) * wid;
            Vec3d base = w(f, side, bu, bv, sw, li), tip = w(f, side, tu, tv, sw, li);
            Vec3d l = w(f, side, bu + Math.cos(ang) * len * 0.35 + pu, bv + Math.sin(ang) * len * 0.35 + pv, sw, li);
            Vec3d rr = w(f, side, bu + Math.cos(ang) * len * 0.35 - pu, bv + Math.sin(ang) * len * 0.35 - pv, sw, li);
            double shimmer = 0.5 + 0.5 * Math.sin(t * 3 + k);
            int cb = c(tt, 0.55), ct = ColorUtil.lerp(c(tt, 0.9), 0xFFFFFFFF, 0.3 + 0.4 * shimmer);
            r.quadWorld(base, l, tip, rr, cb, ColorUtil.lerp(cb, 0xFFFFFFFF, 0.25), ct, cb, false);
            if (glow.isOn()) r.quadWorld(base, l, tip, rr, ColorUtil.withAlpha(cb, 0x18), ColorUtil.withAlpha(cb, 0x30), ColorUtil.withAlpha(ct, 0x70), ColorUtil.withAlpha(cb, 0x30), true);
        }
    }

    private void bar(Render3D r, Cosmetics.Frame f, int side, double[] a, double[] b, double wdt, int col, double sw, double li) {
        double du = b[0] - a[0], dv = b[1] - a[1], len = Math.hypot(du, dv);
        double pu = -dv / len * wdt, pv = du / len * wdt;
        Vec3d p0 = w(f, side, a[0] + pu, a[1] + pv, sw, li), p1 = w(f, side, b[0] + pu * 0.3, b[1] + pv * 0.3, sw, li);
        Vec3d p2 = w(f, side, b[0] - pu * 0.3, b[1] - pv * 0.3, sw, li), p3 = w(f, side, a[0] - pu, a[1] - pv, sw, li);
        r.quadWorld(p0, p1, p2, p3, col, col, col, col, false);
        if (glow.isOn()) r.quadWorld(p0, p1, p2, p3, ColorUtil.withAlpha(col, 0x60), ColorUtil.withAlpha(col, 0x60), ColorUtil.withAlpha(col, 0x60), ColorUtil.withAlpha(col, 0x60), true);
    }

    private static double[] along(double[][] pts, double t) {
        double seg = t * (pts.length - 1);
        int i = Math.min(pts.length - 2, (int) seg);
        double k = seg - i;
        return new double[]{pts[i][0] + (pts[i + 1][0] - pts[i][0]) * k, pts[i][1] + (pts[i + 1][1] - pts[i][1]) * k};
    }
}
