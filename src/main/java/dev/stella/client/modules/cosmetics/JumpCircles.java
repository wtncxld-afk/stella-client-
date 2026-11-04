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

import java.util.ArrayList;
import java.util.List;

/**
 * A glowing ring spreads from your feet when you jump, and the Stella star blooms on the ground
 * where you land. Client-side only.
 */
public class JumpCircles extends Module {
    private final ModeSetting colorMode = add(new ModeSetting("Color", "Theme accent, rainbow or a custom colour", "Theme", "Theme", "Rainbow", "Custom"));
    private final ColorSetting custom = add(new ColorSetting("Custom Color", "", 0xFFB46CFF)).visibleWhen(() -> colorMode.is("Custom"));
    private final BoolSetting onJump = add(new BoolSetting("Jump Circle", "Ring when you jump", true));
    private final BoolSetting onLand = add(new BoolSetting("Landing Star", "Stella star where you land", true));
    private final NumberSetting size = add(new NumberSetting("Size", "", 1.0, 0.5, 2.0, 0.05).suffix("x"));
    private final NumberSetting duration = add(new NumberSetting("Duration", "Seconds each effect lasts", 1.2, 0.5, 3.0, 0.1).suffix("s"));

    private record Fx(Vec3d pos, double start, boolean star, double spin) {}

    private final List<Fx> fx = new ArrayList<>();
    private boolean wasOnGround = true, airborneFromJump;
    private double lastY;

    public JumpCircles() { super("Jump Circles", "Ring when you jump, Stella star when you land", Category.COSMETICS, false); }

    @Override protected void onEnable() { fx.clear(); }

    public int activeEffects() { return fx.size(); }

    /** Spawns a landing star at your feet (also used by the preview / smoke test). */
    public void spawnStar() { if (mc.player != null) fx.add(new Fx(mc.player.getEntityPos(), Cosmetics.now(), true, Math.random() * Math.PI)); }
    public void spawnRing() { if (mc.player != null) fx.add(new Fx(mc.player.getEntityPos(), Cosmetics.now(), false, 0)); }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        boolean ground = mc.player.isOnGround();
        double vy = mc.player.getY() - lastY;
        if (wasOnGround && !ground && vy > 0.05 && !mc.player.getAbilities().flying) {
            airborneFromJump = true;
            if (onJump.isOn()) spawnRing();
        }
        if (!wasOnGround && ground && airborneFromJump) {
            airborneFromJump = false;
            if (onLand.isOn()) spawnStar();
        }
        if (mc.player.getAbilities().flying || mc.player.isTouchingWater()) airborneFromJump = false;
        wasOnGround = ground;
        lastY = mc.player.getY();
        double now = Cosmetics.now();
        fx.removeIf(e -> now - e.start > duration.get() + 0.1);
    }

    @Override
    public void onRender3D(Render3D r) {
        double now = Cosmetics.now();
        for (Fx e : fx) {
            double t = (now - e.start) / duration.get();
            if (t < 0 || t > 1) continue;
            if (e.star) star(r, e, t); else ring(r, e.pos, t, 1.0, 1.0);
        }
    }

    private int col(double t, double alpha) {
        return ColorUtil.withAlpha(Cosmetics.color(colorMode.get(), custom.get(), t), (int) Math.max(0, Math.min(255, 255 * alpha)));
    }

    /** Expanding glowing annulus on the ground. */
    private void ring(Render3D r, Vec3d c, double t, double scale, double alphaMul) {
        double e = 1 - Math.pow(1 - t, 3);
        double rad = (0.25 + 1.15 * e) * size.get() * scale, w = 0.10 * size.get() * scale * (1 - t * 0.6);
        double a = (1 - t) * alphaMul;
        int seg = 56;
        double y = c.y + 0.03;
        for (int i = 0; i < seg; i++) {
            double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
            double h0 = Math.abs((i / (double) seg) - 0.5) * 2, h1 = Math.abs(((i + 1) / (double) seg) - 0.5) * 2;
            Vec3d o0 = new Vec3d(c.x + Math.cos(a0) * rad, y, c.z + Math.sin(a0) * rad), o1 = new Vec3d(c.x + Math.cos(a1) * rad, y, c.z + Math.sin(a1) * rad);
            Vec3d i0 = new Vec3d(c.x + Math.cos(a0) * (rad - w), y, c.z + Math.sin(a0) * (rad - w)), i1 = new Vec3d(c.x + Math.cos(a1) * (rad - w), y, c.z + Math.sin(a1) * (rad - w));
            r.quadWorld(i0, o0, o1, i1, col(h0, a * 0.5), col(h0, a), col(h1, a), col(h1, a * 0.5), false);
            r.quadWorld(i0, o0, o1, i1, col(h0, a * 0.25), col(h0, a * 0.7), col(h1, a * 0.7), col(h1, a * 0.25), true);
            // soft inner fill fading to the centre
            Vec3d f0 = new Vec3d(c.x + Math.cos(a0) * rad * 0.2, y - 0.002, c.z + Math.sin(a0) * rad * 0.2), f1 = new Vec3d(c.x + Math.cos(a1) * rad * 0.2, y - 0.002, c.z + Math.sin(a1) * rad * 0.2);
            r.quadWorld(f0, i0, i1, f1, col(h0, 0), col(h0, a * 0.18), col(h1, a * 0.18), col(h1, 0), false);
        }
    }

    /** The Stella star: five points bloom out (overshoot), slowly spin, then fade; plus a shockwave ring. */
    private void star(Render3D r, Fx e, double t) {
        Vec3d c = e.pos;
        double grow = t < 0.35 ? backOut(t / 0.35) : 1;
        double fade = t < 0.55 ? 1 : 1 - (t - 0.55) / 0.45;
        double outer = 1.05 * size.get() * grow, inner = outer * 0.42;
        double spin = e.spin + t * 0.9;
        double y = c.y + 0.04;
        Vec3d center = new Vec3d(c.x, y, c.z);
        int core = ColorUtil.withAlpha(ColorUtil.lerp(col(0.5, 1), 0xFFFFFFFF, 0.65), (int) (240 * fade));
        for (int i = 0; i < 10; i++) {
            double a0 = spin + i * Math.PI / 5 - Math.PI / 2, a1 = spin + (i + 1) * Math.PI / 5 - Math.PI / 2;
            double r0 = i % 2 == 0 ? outer : inner, r1 = i % 2 == 0 ? inner : outer;
            Vec3d p0 = new Vec3d(c.x + Math.cos(a0) * r0, y, c.z + Math.sin(a0) * r0);
            Vec3d p1 = new Vec3d(c.x + Math.cos(a1) * r1, y, c.z + Math.sin(a1) * r1);
            int c0 = col(i / 10.0, fade * (i % 2 == 0 ? 0.95 : 0.8)), c1 = col((i + 1) / 10.0, fade * (i % 2 == 0 ? 0.8 : 0.95));
            r.triWorld(center, p0, p1, core, c0, c1, false);
            r.triWorld(center, p0, p1, ColorUtil.withAlpha(core, (int) (0x50 * fade)), ColorUtil.withAlpha(c0, (int) (0x90 * fade)), ColorUtil.withAlpha(c1, (int) (0x90 * fade)), true);
            // glowing outline just outside the star edge
            double gw = 0.05 * size.get();
            Vec3d q0 = new Vec3d(c.x + Math.cos(a0) * (r0 + gw), y + 0.001, c.z + Math.sin(a0) * (r0 + gw));
            Vec3d q1 = new Vec3d(c.x + Math.cos(a1) * (r1 + gw), y + 0.001, c.z + Math.sin(a1) * (r1 + gw));
            r.quadWorld(p0, q0, q1, p1, ColorUtil.withAlpha(c0, (int) (0xC0 * fade)), ColorUtil.withAlpha(c0, 0), ColorUtil.withAlpha(c1, 0), ColorUtil.withAlpha(c1, (int) (0xC0 * fade)), true);
        }
        // sparkle twinkle at the centre
        double tw = (0.18 + 0.08 * Math.sin(t * 25)) * size.get() * grow;
        int sp = ColorUtil.withAlpha(0xFFFFFFFF, (int) (220 * fade));
        r.quadWorld(new Vec3d(c.x - tw, y + 0.002, c.z), new Vec3d(c.x, y + 0.002, c.z - tw * 0.25), new Vec3d(c.x + tw, y + 0.002, c.z), new Vec3d(c.x, y + 0.002, c.z + tw * 0.25), sp, sp, sp, sp, true);
        r.quadWorld(new Vec3d(c.x, y + 0.002, c.z - tw), new Vec3d(c.x + tw * 0.25, y + 0.002, c.z), new Vec3d(c.x, y + 0.002, c.z + tw), new Vec3d(c.x - tw * 0.25, y + 0.002, c.z), sp, sp, sp, sp, true);
        ring(r, c, Math.min(1, t * 1.3), 1.25, 0.8);
    }

    private static double backOut(double x) {
        double c1 = 1.70158, c3 = c1 + 1;
        return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2);
    }
}
