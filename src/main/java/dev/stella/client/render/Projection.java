package dev.stella.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Captures the world projection each frame so 2D overlays (labels, tracers) can
 * be placed at world positions with crisp GUI text.
 */
public final class Projection {
    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f PROJ = new Matrix4f();
    private static Vec3d camPos = Vec3d.ZERO;
    private static boolean valid;

    private Projection() {}

    public static void capture(Matrix4f positionMatrix, Matrix4f projectionMatrix, Vec3d cameraPos) {
        VIEW.set(positionMatrix);
        PROJ.set(projectionMatrix);
        camPos = cameraPos;
        valid = true;
    }

    public static Vec3d cameraPos() { return camPos; }

    /**
     * @return screen position in scaled GUI pixels, with z = 1 when the point is
     *         in front of the camera and z = -1 when it is behind (x/y mirrored
     *         and pushed off-screen so tracers still point the right way).
     */
    public static Vec3d toScreen(Vec3d world) {
        if (!valid) return null;
        MinecraftClient mc = MinecraftClient.getInstance();
        Vector4f v = new Vector4f((float) (world.x - camPos.x), (float) (world.y - camPos.y), (float) (world.z - camPos.z), 1f);
        VIEW.transform(v);
        PROJ.transform(v);
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        if (Math.abs(v.w) < 1e-6f) return null;
        boolean behind = v.w < 0;
        float nx = v.x / v.w, ny = v.y / v.w;
        if (behind) {
            nx = -nx; ny = -ny;
            float m = Math.max(Math.abs(nx), Math.abs(ny));
            if (m < 1e-4f) { nx = 0; ny = -1; m = 1; }
            nx = nx / m * 2f; ny = ny / m * 2f;
        }
        double sx = (nx * 0.5 + 0.5) * sw;
        double sy = (1.0 - (ny * 0.5 + 0.5)) * sh;
        return new Vec3d(sx, sy, behind ? -1 : 1);
    }

    /**
     * World-space point a short distance in front of the camera that lies under
     * the given screen pixel (scaled GUI coordinates). Used as the start of 3D tracers.
     */
    public static Vec3d unproject(double sx, double sy, double distance) {
        if (!valid) return camPos;
        MinecraftClient mc = MinecraftClient.getInstance();
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        float nx = (float) (sx / sw * 2 - 1), ny = (float) (1 - sy / sh * 2);
        Matrix4f inv = new Matrix4f(PROJ).mul(VIEW).invert();
        Vector4f near = new Vector4f(nx, ny, -1f, 1f);
        Vector4f far = new Vector4f(nx, ny, 1f, 1f);
        inv.transform(near);
        inv.transform(far);
        near.div(near.w);
        far.div(far.w);
        Vec3d dir = new Vec3d(far.x - near.x, far.y - near.y, far.z - near.z).normalize();
        return camPos.add(dir.multiply(distance));
    }

    public static boolean onScreen(Vec3d s) {
        if (s == null || s.z < 0) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        return s.x > -50 && s.y > -50 && s.x < mc.getWindow().getScaledWidth() + 50 && s.y < mc.getWindow().getScaledHeight() + 50;
    }
}
