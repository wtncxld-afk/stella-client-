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
        seg(buf, ax, ay, az, bx, by, bz, argb, argb, width);
    }

    private void seg(VertexConsumer buf, float ax, float ay, float az, float bx, float by, float bz, int ca, int cb, float width) {
        float nx = bx - ax, ny = by - ay, nz = bz - az;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-5f) return;
        nx /= len; ny /= len; nz /= len;
        MatrixStack.Entry e = matrices.peek();
        Matrix4f m = e.getPositionMatrix();
        buf.vertex(m, ax, ay, az).color(ca).normal(e, nx, ny, nz).lineWidth(width);
        buf.vertex(m, bx, by, bz).color(cb).normal(e, nx, ny, nz).lineWidth(width);
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

    /** Tracer look: 0 = Neon (glow + bright core), 1 = Gradient, 2 = Solid. */
    public record TracerLook(int mode, float glow, boolean fadeIn, boolean endMarker) {
        public static final TracerLook SOLID = new TracerLook(2, 0f, false, false);
    }

    private record Tracer(float ax, float ay, float az, float bx, float by, float bz, int argb, float width, TracerLook look) {}
    private final java.util.List<Tracer> tracers = new java.util.ArrayList<>();

    /** Plain tracer (kept for callers without style settings). */
    public void tracer(Vec3d from, Vec3d to, int argb, float width) {
        tracer(from, to, argb, width, TracerLook.SOLID);
    }

    /** Smooth screen-pixel-width tracer; drawn in {@link #flush()} so all glow sits under all cores. */
    public void tracer(Vec3d from, Vec3d to, int argb, float width, TracerLook look) {
        tracers.add(new Tracer((float) (from.x - cam.x), (float) (from.y - cam.y), (float) (from.z - cam.z),
                (float) (to.x - cam.x), (float) (to.y - cam.y), (float) (to.z - cam.z), argb, Math.max(0.5f, width), look));
    }

    private static int alpha(int argb, float mul) {
        int a = Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, mul)));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    /** Mixes the colour toward white (hot neon core). */
    private static int whiten(int argb, float t) {
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        r += (int) ((255 - r) * t); g += (int) ((255 - g) * t); b += (int) ((255 - b) * t);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private void drawTracerGlow(VertexConsumer glow) {
        for (Tracer t : tracers) {
            TracerLook l = t.look;
            if (l.mode == 2 || l.glow <= 0f) continue;
            float start = l.fadeIn ? 0.0f : 1f;
            int c = t.argb;
            if (l.mode == 0) {
                seg(glow, t.ax, t.ay, t.az, t.bx, t.by, t.bz, alpha(c, 0.10f * l.glow * start), alpha(c, 0.10f * l.glow), t.width * 7f);
                seg(glow, t.ax, t.ay, t.az, t.bx, t.by, t.bz, alpha(c, 0.22f * l.glow * start), alpha(c, 0.22f * l.glow), t.width * 3.4f);
            } else {
                seg(glow, t.ax, t.ay, t.az, t.bx, t.by, t.bz, alpha(c, 0.18f * l.glow * start), alpha(c, 0.18f * l.glow), t.width * 3.2f);
            }
            if (l.endMarker) marker(glow, t.bx, t.by, t.bz, 0.30f, alpha(c, 0.35f * l.glow), t.width * 5f);
        }
    }

    private void drawTracerCores(VertexConsumer lines) {
        for (Tracer t : tracers) {
            TracerLook l = t.look;
            int c = t.argb;
            int startC = l.fadeIn ? alpha(c, 0.06f) : c;
            if (l.mode == 0) {
                seg(lines, t.ax, t.ay, t.az, t.bx, t.by, t.bz, alpha(startC, 0.85f), alpha(c, 0.85f), t.width * 1.6f);
                int hot = whiten(c, 0.6f);
                seg(lines, t.ax, t.ay, t.az, t.bx, t.by, t.bz, l.fadeIn ? alpha(hot, 0.05f) : hot, hot, Math.max(0.5f, t.width * 0.7f));
            } else {
                seg(lines, t.ax, t.ay, t.az, t.bx, t.by, t.bz, startC, c, t.width);
            }
            if (l.endMarker) marker(lines, t.bx, t.by, t.bz, 0.22f, l.mode == 0 ? whiten(c, 0.4f) : c, Math.max(1f, t.width));
        }
    }

    /** Small octahedron at the tracer's end point. */
    private void marker(VertexConsumer buf, float x, float y, float z, float r, int c, float w) {
        float[][] tips = {{r, 0, 0}, {-r, 0, 0}, {0, 0, r}, {0, 0, -r}};
        for (int i = 0; i < 4; i++) {
            float[] p = tips[i], q = tips[(i == 0) ? 2 : (i == 2) ? 1 : (i == 1) ? 3 : 0];
            seg(buf, x + p[0], y, z + p[2], x + q[0], y, z + q[2], c, w);
            seg(buf, x + p[0], y, z + p[2], x, y + r, z, c, w);
            seg(buf, x + p[0], y, z + p[2], x, y - r, z, c, w);
        }
    }

    /** Draws everything written this frame. */
    public void flush() {
        consumers.draw(StellaPipelines.FILLS);
        consumers.draw(StellaPipelines.LINES);
        if (!tracers.isEmpty()) {
            drawTracerGlow(consumers.getBuffer(StellaPipelines.GLOW));
            consumers.draw(StellaPipelines.GLOW);
            drawTracerCores(consumers.getBuffer(StellaPipelines.LINES));
            consumers.draw(StellaPipelines.LINES);
            tracers.clear();
        }
    }
}
