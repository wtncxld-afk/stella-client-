package dev.stella.client.render;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * World-space renderer for ESP boxes, fills and tracers. Geometry is written into
 * Stella's see-through render layers and flushed once per frame by {@link #flush()}.
 * Coordinates are absolute world coordinates; the camera offset is applied here.
 */
public final class Render3D {
    private final MatrixStack matrices;
    private final VertexConsumerProvider.Immediate consumers;
    private final Vec3d cam;
    private final float tickDelta;
    private float lineWidth = 2f;
    private float tracerWidth = 1.5f;

    public Render3D(MatrixStack matrices, VertexConsumerProvider.Immediate consumers, Vec3d cameraPos, float tickDelta) {
        this.matrices = matrices;
        this.consumers = consumers;
        this.cam = cameraPos;
        this.tickDelta = tickDelta;
    }

    public float tickDelta() { return tickDelta; }
    public Vec3d cameraPos() { return cam; }
    public void setLineWidth(float w) { this.lineWidth = Math.max(1f, w); }

    private VertexConsumer lines() { return consumers.getBuffer(StellaPipelines.LINES); }
    private VertexConsumer fills() { return consumers.getBuffer(StellaPipelines.FILLS); }

    private void seg(VertexConsumer buf, float ax, float ay, float az, float bx, float by, float bz, int argb, float width) {
        float nx = bx - ax, ny = by - ay, nz = bz - az;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-5f) return;
        nx /= len; ny /= len; nz /= len;
        MatrixStack.Entry e = matrices.peek();
        Matrix4f m = e.getPositionMatrix();
        buf.vertex(m, ax, ay, az).color(argb).normal(e, nx, ny, nz).lineWidth(width);
        buf.vertex(m, bx, by, bz).color(argb).normal(e, nx, ny, nz).lineWidth(width);
    }

    public void boxOutline(Box box, int argb) {
        VertexConsumer b = lines();
        float x1 = (float) (box.minX - cam.x), y1 = (float) (box.minY - cam.y), z1 = (float) (box.minZ - cam.z);
        float x2 = (float) (box.maxX - cam.x), y2 = (float) (box.maxY - cam.y), z2 = (float) (box.maxZ - cam.z);
        float w = lineWidth;
        // bottom
        seg(b, x1, y1, z1, x2, y1, z1, argb, w); seg(b, x2, y1, z1, x2, y1, z2, argb, w);
        seg(b, x2, y1, z2, x1, y1, z2, argb, w); seg(b, x1, y1, z2, x1, y1, z1, argb, w);
        // top
        seg(b, x1, y2, z1, x2, y2, z1, argb, w); seg(b, x2, y2, z1, x2, y2, z2, argb, w);
        seg(b, x2, y2, z2, x1, y2, z2, argb, w); seg(b, x1, y2, z2, x1, y2, z1, argb, w);
        // verticals
        seg(b, x1, y1, z1, x1, y2, z1, argb, w); seg(b, x2, y1, z1, x2, y2, z1, argb, w);
        seg(b, x2, y1, z2, x2, y2, z2, argb, w); seg(b, x1, y1, z2, x1, y2, z2, argb, w);
    }

    public void boxFill(Box box, int argb) {
        VertexConsumer f = fills();
        Matrix4f m = matrices.peek().getPositionMatrix();
        float x1 = (float) (box.minX - cam.x), y1 = (float) (box.minY - cam.y), z1 = (float) (box.minZ - cam.z);
        float x2 = (float) (box.maxX - cam.x), y2 = (float) (box.maxY - cam.y), z2 = (float) (box.maxZ - cam.z);
        quad(f, m, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, argb);
        quad(f, m, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, argb);
        quad(f, m, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, argb);
        quad(f, m, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, argb);
        quad(f, m, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, argb);
        quad(f, m, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, argb);
    }

    private static void quad(VertexConsumer f, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, int c) {
        f.vertex(m, ax, ay, az).color(c);
        f.vertex(m, bx, by, bz).color(c);
        f.vertex(m, cx, cy, cz).color(c);
        f.vertex(m, dx, dy, dz).color(c);
    }

    /** Single horizontal translucent quad — used for flat chunk overlays. */
    public void quadXZ(double x1, double z1, double x2, double z2, double y, int argb) {
        Matrix4f m = matrices.peek().getPositionMatrix();
        float ax = (float) (x1 - cam.x), az = (float) (z1 - cam.z), bx = (float) (x2 - cam.x), bz = (float) (z2 - cam.z), yy = (float) (y - cam.y);
        quad(fills(), m, ax, yy, az, ax, yy, bz, bx, yy, bz, bx, yy, az, argb);
    }

    public void box(Box box, int outline, int fill, boolean drawOutline, boolean drawFill) {
        if (drawFill) boxFill(box, fill);
        if (drawOutline) boxOutline(box, outline);
    }

    public void line(Vec3d a, Vec3d b, int argb) {
        seg(lines(), (float) (a.x - cam.x), (float) (a.y - cam.y), (float) (a.z - cam.z),
                (float) (b.x - cam.x), (float) (b.y - cam.y), (float) (b.z - cam.z), argb, lineWidth);
    }

    /** Thin smooth tracer line (screen-pixel width). */
    public void tracer(Vec3d from, Vec3d to, int argb, float width) {
        seg(lines(), (float) (from.x - cam.x), (float) (from.y - cam.y), (float) (from.z - cam.z),
                (float) (to.x - cam.x), (float) (to.y - cam.y), (float) (to.z - cam.z), argb, Math.max(0.5f, width));
    }

    /** Draws everything written this frame. */
    public void flush() {
        consumers.draw(StellaPipelines.FILLS);
        consumers.draw(StellaPipelines.LINES);
    }
}
