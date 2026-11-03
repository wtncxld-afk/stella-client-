package dev.stella.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

/**
 * Tracers are drawn as real 3D lines (RENDERTYPE_LINES) from a point just in
 * front of the camera under the chosen screen position to the target, so they
 * are thin and smooth at any GUI scale instead of blocky GUI-pixel rectangles.
 */
public final class Tracers {
    public static final String[] ORIGINS = {"Crosshair", "Top", "Bottom", "Custom"};

    private Tracers() {}

    /** @return {x, y} in scaled GUI pixels. */
    public static double[] origin(String mode, double xPercent, double yPercent) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        return switch (mode) {
            case "Top" -> new double[]{sw / 2.0, 0};
            case "Bottom" -> new double[]{sw / 2.0, sh};
            case "Custom" -> new double[]{sw * xPercent / 100.0, sh * yPercent / 100.0};
            default -> new double[]{sw / 2.0, sh / 2.0};
        };
    }

    /** World-space start point for tracers from the given screen origin. */
    public static Vec3d start(String mode, double xPercent, double yPercent) {
        double[] o = origin(mode, xPercent, yPercent);
        return Projection.unproject(o[0], o[1], 0.6);
    }

    public static void draw(Render3D r, Vec3d start, Vec3d target, float width, int color) {
        r.tracer(start, target, color, width);
    }
}
