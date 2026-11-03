package dev.stella.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.Vec3d;

/** 2D tracers drawn on the HUD layer from a configurable screen point to a projected world position. */
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

    /** Draws one tracer; returns false if the target could not be projected. */
    public static boolean draw(DrawContext ctx, double[] origin, Vec3d target, float width, int color) {
        Vec3d s = Projection.toScreen(target);
        if (s == null) return false;
        Render2D.line(ctx, origin[0], origin[1], s.x, s.y, width, color);
        return true;
    }
}
