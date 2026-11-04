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

/** Animated cape that swings with your movement (client-side; visible in third person). */
public class Cape extends Module {
    private final ModeSetting style = add(new ModeSetting("Style", "Cape design", "Galaxy", "Galaxy", "Theme", "Stella", "Aurora"));
    private final ModeSetting colorMode = add(new ModeSetting("Color", "Colour for Theme / Stella styles", "Theme", "Theme", "Rainbow", "Custom"));
    private final ColorSetting custom = add(new ColorSetting("Custom Color", "", 0xFF7B3BFF)).visibleWhen(() -> colorMode.is("Custom"));
    private final NumberSetting length = add(new NumberSetting("Length", "Cape length", 1.0, 0.6, 1.4, 0.05));
    private final BoolSetting glowEdge = add(new BoolSetting("Glowing Edge", "Soft glow along the hem", true));

    private double swing, lastX, lastZ;

    public Cape() { super("Cape", "Animated cape (Galaxy, Theme, Stella, Aurora) - only you see it, third person", Category.COSMETICS, false); }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        double dx = mc.player.getX() - lastX, dz = mc.player.getZ() - lastZ;
        lastX = mc.player.getX(); lastZ = mc.player.getZ();
        double yaw = Math.toRadians(mc.player.bodyYaw);
        double forward = -dx * Math.sin(yaw) + dz * Math.cos(yaw); // speed along facing
        double target = Math.min(1.2, Math.max(0, forward * 4.5)) + (mc.player.isOnGround() ? 0 : 0.25);
        swing += (target - swing) * 0.25;
    }

    @Override
    public void onRender3D(Render3D r) {
        if (!Cosmetics.selfVisible()) return;
        Cosmetics.Frame f = Cosmetics.Frame.body(mc.player, r.tickDelta());
        double t = Cosmetics.now();
        int rows = 10, cols = 6;
        double len = length.get(), half = 0.3;
        Vec3d[][] grid = new Vec3d[rows + 1][cols + 1];
        for (int i = 0; i <= rows; i++) {
            double v = i / (double) rows;
            // hangs from the shoulders, swings back with speed, flutters at the hem
            double ang = 0.12 + swing * 0.9 * v + Math.sin(t * 6 + v * 3) * 0.04 * (0.3 + swing) * v;
            double y = 1.42 - Math.cos(ang) * len * v, z = -0.16 - Math.sin(ang) * len * v;
            for (int j = 0; j <= cols; j++) {
                double u = j / (double) cols, x = (u - 0.5) * 2 * half;
                double wave = Math.sin(t * 5 + u * 4 + v * 2) * 0.025 * v;
                grid[i][j] = f.at(x, y, z - wave);
            }
        }
        for (int i = 0; i < rows; i++)
            for (int j = 0; j < cols; j++) {
                int c00 = col(j / (double) cols, i / (double) rows, t), c10 = col((j + 1) / (double) cols, i / (double) rows, t);
                int c11 = col((j + 1) / (double) cols, (i + 1) / (double) rows, t), c01 = col(j / (double) cols, (i + 1) / (double) rows, t);
                r.quadWorld(grid[i][j], grid[i][j + 1], grid[i + 1][j + 1], grid[i + 1][j], c00, c10, c11, c01, false);
            }
        // stars / emblem (additive)
        if (style.is("Galaxy") || style.is("Stella")) {
            java.util.Random rnd = new java.util.Random(11);
            int n = style.is("Galaxy") ? 14 : 0;
            for (int k = 0; k < n; k++) star(r, grid, rnd.nextDouble(), rnd.nextDouble(), 0.02 + rnd.nextDouble() * 0.025 * (0.6 + 0.4 * Math.sin(t * 3 + k)));
            if (style.is("Stella")) star(r, grid, 0.5, 0.4, 0.16);
        }
        if (glowEdge.isOn()) {
            for (int j = 0; j < cols; j++) {
                Vec3d a = grid[rows][j], b = grid[rows][j + 1], a2 = grid[rows - 1][j], b2 = grid[rows - 1][j + 1];
                int g = ColorUtil.withAlpha(Cosmetics.color(colorMode.get(), custom.get(), j / (double) cols), 0x90);
                r.quadWorld(a2, b2, b, a, ColorUtil.withAlpha(g, 0), ColorUtil.withAlpha(g, 0), g, g, true);
            }
        }
    }

    private int col(double u, double v, double t) {
        return switch (style.get()) {
            case "Galaxy" -> {
                double neb = 0.5 + 0.5 * Math.sin(u * 5 + v * 7 + t * 0.6);
                yield ColorUtil.lerp(ColorUtil.lerp(0xF0140A2E, 0xF02A1460, v), 0xF04A2A9A, neb * 0.6);
            }
            case "Aurora" -> {
                double w = 0.5 + 0.5 * Math.sin(u * 6 + t * 1.2 + v * 2);
                yield ColorUtil.lerp(ColorUtil.lerp(0xF00A1E2A, 0xF01EE6A0, w * (1 - v * 0.5)), 0xF07B3BFF, v * 0.6);
            }
            default -> ColorUtil.withAlpha(ColorUtil.lerp(Cosmetics.color(colorMode.get(), custom.get(), u), 0xFF0E0A16, v * 0.55), 0xF0);
        };
    }

    /** Four-point star on the cape surface at (u, v). */
    private void star(Render3D r, Vec3d[][] g, double u, double v, double size) {
        int rows = g.length - 1, cols = g[0].length - 1;
        int i = Math.min(rows - 1, (int) (v * rows)), j = Math.min(cols - 1, (int) (u * cols));
        Vec3d c = g[i][j].add(g[i + 1][j + 1]).multiply(0.5);
        Vec3d right = g[i][j + 1].subtract(g[i][j]).normalize().multiply(size), down = g[i + 1][j].subtract(g[i][j]).normalize().multiply(size);
        Vec3d out = right.crossProduct(down).normalize().multiply(-0.01);
        Vec3d cc = c.add(out);
        int w = 0xE0FFFFFF, clr = 0x00FFFFFF;
        r.quadWorld(cc.subtract(right), cc.subtract(down.multiply(0.2)), cc.add(right), cc.add(down.multiply(0.2)), clr, w, clr, w, true);
        r.quadWorld(cc.subtract(down), cc.add(right.multiply(0.2)), cc.add(down), cc.subtract(right.multiply(0.2)), clr, w, clr, w, true);
    }
}
