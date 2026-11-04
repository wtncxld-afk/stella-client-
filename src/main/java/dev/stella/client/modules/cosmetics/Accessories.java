package dev.stella.client.modules.cosmetics;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.render.Render3D;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.ColorSetting;
import dev.stella.client.setting.ModeSetting;
import dev.stella.client.util.ColorUtil;
import net.minecraft.util.math.Vec3d;

/** Halo, horns and a floating star pet (client-side; third person, except the pet which also shows in first person). */
public class Accessories extends Module {
    private final BoolSetting halo = add(new BoolSetting("Halo", "Glowing ring above your head", true));
    private final BoolSetting horns = add(new BoolSetting("Horns", "Curved horns on your head", false));
    private final BoolSetting pet = add(new BoolSetting("Star Pet", "A little star that floats by your shoulder", true));
    private final ModeSetting colorMode = add(new ModeSetting("Color", "Theme accent, rainbow or a custom colour", "Theme", "Theme", "Rainbow", "Custom"));
    private final ColorSetting custom = add(new ColorSetting("Custom Color", "", 0xFFFFD45A)).visibleWhen(() -> colorMode.is("Custom"));

    private final java.util.ArrayDeque<Vec3d> petTrail = new java.util.ArrayDeque<>();

    public Accessories() { super("Accessories", "Halo, horns and a floating star pet (only you see them)", Category.COSMETICS, false); }

    private int c(double t, int alpha) { return ColorUtil.withAlpha(Cosmetics.color(colorMode.get(), custom.get(), t), alpha); }

    @Override
    public void onRender3D(Render3D r) {
        if (mc.player == null) return;
        double t = Cosmetics.now();
        boolean visible = Cosmetics.selfVisible();
        Cosmetics.Head h = Cosmetics.Head.of(mc.player, r.tickDelta());
        if (halo.isOn() && visible) {
            int n = 40;
            double y = 0.5 + 0.28 + Math.sin(t * 2) * 0.03, rad = 0.32, w = 0.05;
            for (int i = 0; i < n; i++) {
                double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
                Vec3d o0 = h.at(Math.cos(a0) * rad, y, Math.sin(a0) * rad), o1 = h.at(Math.cos(a1) * rad, y, Math.sin(a1) * rad);
                Vec3d i0 = h.at(Math.cos(a0) * (rad - w), y, Math.sin(a0) * (rad - w)), i1 = h.at(Math.cos(a1) * (rad - w), y, Math.sin(a1) * (rad - w));
                int ca = c(i / (double) n, 0xF0), cb = c((i + 1) / (double) n, 0xF0);
                r.quadWorld(i0, o0, o1, i1, ColorUtil.lerp(ca, 0xFFFFFFFF, 0.5), ca, cb, ColorUtil.lerp(cb, 0xFFFFFFFF, 0.5), false);
                Vec3d g0 = h.at(Math.cos(a0) * (rad + 0.08), y, Math.sin(a0) * (rad + 0.08)), g1 = h.at(Math.cos(a1) * (rad + 0.08), y, Math.sin(a1) * (rad + 0.08));
                r.quadWorld(o0, g0, g1, o1, ColorUtil.withAlpha(ca, 0x90), ColorUtil.withAlpha(ca, 0), ColorUtil.withAlpha(cb, 0), ColorUtil.withAlpha(cb, 0x90), true);
            }
        }
        if (horns.isOn() && visible) {
            for (int side = -1; side <= 1; side += 2) {
                Vec3d prev = null; double prevR = 0;
                int seg = 8;
                for (int k = 0; k <= seg; k++) {
                    double s = k / (double) seg;
                    double x = side * (0.17 + 0.12 * s), y = 0.45 + 0.3 * s - 0.08 * s * s, z = 0.08 - 0.22 * s * s;
                    double rr = 0.07 * (1 - s) + 0.004;
                    Vec3d p = h.at(x, y, z);
                    if (prev != null) {
                        for (int q = 0; q < 6; q++) {
                            double a0 = q * Math.PI / 3, a1 = (q + 1) * Math.PI / 3;
                            Vec3d pa = prev.add(Math.cos(a0) * prevR, 0, Math.sin(a0) * prevR), pb = prev.add(Math.cos(a1) * prevR, 0, Math.sin(a1) * prevR);
                            Vec3d qa = p.add(Math.cos(a0) * rr, 0, Math.sin(a0) * rr), qb = p.add(Math.cos(a1) * rr, 0, Math.sin(a1) * rr);
                            int base = ColorUtil.lerp(0xFF1A1020, c(0.3, 255), s * 0.8);
                            int shade = ColorUtil.lerp(base, 0xFF000000, 0.25 * Math.abs(Math.sin(a0)));
                            r.quadWorld(pa, pb, qb, qa, shade | 0xFF000000, shade | 0xFF000000, base | 0xFF000000, base | 0xFF000000, false);
                        }
                    }
                    prev = p; prevR = rr;
                }
            }
        }
        if (pet.isOn()) {
            var pos = mc.player.getLerpedPos(r.tickDelta());
            double yaw = Math.toRadians(mc.player.getYaw(r.tickDelta()));
            double ox = -Math.cos(yaw) * 0.75 + Math.cos(t * 1.3) * 0.12, oz = -Math.sin(yaw) * 0.75 + Math.sin(t * 1.3) * 0.12;
            Vec3d p = pos.add(ox, 1.9 + Math.sin(t * 2.2) * 0.12, oz);
            petTrail.addFirst(p);
            while (petTrail.size() > 18) petTrail.removeLast();
            Vec3d cam = r.cameraPos();
            Vec3d toCam = cam.subtract(p).normalize();
            Vec3d right = toCam.crossProduct(new Vec3d(0, 1, 0)).normalize(), up = right.crossProduct(toCam).normalize();
            double s = 0.16 * (0.9 + 0.1 * Math.sin(t * 6));
            int core = 0xFFFFFFFF, glow = c(0.4, 0xD0);
            double spin = t * 1.5;
            for (int i = 0; i < 10; i++) {
                double a0 = spin + i * Math.PI / 5, a1 = spin + (i + 1) * Math.PI / 5;
                double r0 = i % 2 == 0 ? s : s * 0.42, r1 = i % 2 == 0 ? s * 0.42 : s;
                Vec3d q0 = p.add(right.multiply(Math.cos(a0) * r0)).add(up.multiply(Math.sin(a0) * r0));
                Vec3d q1 = p.add(right.multiply(Math.cos(a1) * r1)).add(up.multiply(Math.sin(a1) * r1));
                r.triWorld(p, q0, q1, core, glow, glow, false);
                r.triWorld(p, q0, q1, ColorUtil.withAlpha(glow, 0x60), ColorUtil.withAlpha(glow, 0x80), ColorUtil.withAlpha(glow, 0x80), true);
            }
            // sparkle trail
            int k = 0;
            Vec3d prev = null;
            for (Vec3d q : petTrail) {
                if (prev != null && k % 2 == 0) {
                    double a = 1 - k / (double) petTrail.size();
                    double w = 0.05 * a;
                    r.quadWorld(prev.add(up.multiply(w)), q.add(up.multiply(w * 0.8)), q.subtract(up.multiply(w * 0.8)), prev.subtract(up.multiply(w)),
                            c(0.4, (int) (0xA0 * a)), c(0.6, (int) (0x80 * a)), c(0.6, (int) (0x80 * a)), c(0.4, (int) (0xA0 * a)), true);
                }
                prev = q; k++;
            }
        }
    }
}
