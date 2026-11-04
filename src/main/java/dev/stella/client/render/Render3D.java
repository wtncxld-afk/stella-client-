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

    /**
     * Beacon-style light beam from {@code y1} up toward {@code y2}: a soft additive halo, a translucent
     * coloured body and a white-hot core, all slowly spinning, with bright energy bands travelling up,
     * plus a pulsing ground ring and an expanding shockwave at the base. Drawn through walls.
     */
    public void beam(double x, double z, double y1, double y2, int argb, double radius) {
        Matrix4f m = matrices.peek().getPositionMatrix();
        VertexConsumer glow = consumers.getBuffer(StellaPipelines.FILLS_GLOW);
        float cx = (float) (x - cam.x), cz = (float) (z - cam.z), base = (float) (y1 - cam.y);
        double t = (System.currentTimeMillis() % 3_600_000L) / 1000.0;
        float a0 = ((argb >>> 24) & 0xFF) / 255f;
        int rgb = argb & 0xFFFFFF;
        int hot = ColorUtil2.whiten(argb, 0.7f) & 0xFFFFFF;
        float height = (float) Math.min(y2 - y1, 220);
        int segs = 28, sides = 14;
        float spin = (float) (t * 0.8);
        // pulsing ground ring + expanding shockwave
        float pulse = (float) (0.5 + 0.5 * Math.sin(t * 3));
        ring(glow, m, cx, base + 0.02f, cz, (float) radius * 1.2f, (float) radius * (2.6f + pulse * 0.6f), sides * 2, rgb, a0 * 0.55f, 0f);
        float wave = (float) ((t % 1.6) / 1.6);
        ring(glow, m, cx, base + 0.03f, cz, (float) radius * (1.5f + wave * 6f), (float) radius * (1.9f + wave * 6f), sides * 2, rgb, a0 * 0.6f * (1 - wave), a0 * 0.6f * (1 - wave));
        for (int i = 0; i < segs; i++) {
            float h0 = height * i / segs, h1 = height * (i + 1) / segs;
            float f0 = fade(h0, height), f1 = fade(h1, height);
            // energy bands moving upward
            float e0 = (float) (0.7 + 0.3 * Math.sin(h0 * 0.45 - t * 7)), e1 = (float) (0.7 + 0.3 * Math.sin(h1 * 0.45 - t * 7));
            float ya = base + h0, yb = base + h1;
            cylinder(glow, m, cx, cz, ya, yb, (float) radius * 2.6f, sides, spin, rgb, a0 * 0.16f * f0, a0 * 0.16f * f1);
            cylinder(glow, m, cx, cz, ya, yb, (float) radius, sides, -spin, rgb, a0 * 0.55f * f0 * e0, a0 * 0.55f * f1 * e1);
            cylinder(glow, m, cx, cz, ya, yb, (float) radius * 0.32f, 8, spin * 2, hot, Math.min(1f, a0 * 1.1f * f0 * e0), Math.min(1f, a0 * 1.1f * f1 * e1));
        }
    }

    private static float fade(float h, float height) {
        float k = Math.max(0f, 1f - h / height);
        return (float) Math.pow(k, 1.4);
    }

    private static int argbOf(int rgb, float alpha) {
        return ((int) (Math.max(0f, Math.min(1f, alpha)) * 255) << 24) | rgb;
    }

    private static void cylinder(VertexConsumer f, Matrix4f m, float cx, float cz, float ya, float yb, float r, int sides, float rot,
                                 int rgb, float alphaBottom, float alphaTop) {
        int cb = argbOf(rgb, alphaBottom), ct = argbOf(rgb, alphaTop);
        for (int s = 0; s < sides; s++) {
            double t0 = rot + Math.PI * 2 * s / sides, t1 = rot + Math.PI * 2 * (s + 1) / sides;
            float x0 = cx + (float) Math.cos(t0) * r, z0 = cz + (float) Math.sin(t0) * r;
            float x1 = cx + (float) Math.cos(t1) * r, z1 = cz + (float) Math.sin(t1) * r;
            vquad(f, m, x0, z0, x1, z1, ya, yb, cb, ct);
        }
    }

    /** Flat annulus; colour fades from the inner edge (alphaIn) to the outer edge (alphaOut). */
    private static void ring(VertexConsumer f, Matrix4f m, float cx, float y, float cz, float rIn, float rOut, int sides,
                             int rgb, float alphaIn, float alphaOut) {
        int ci = argbOf(rgb, alphaIn), co = argbOf(rgb, alphaOut);
        for (int s = 0; s < sides; s++) {
            double t0 = Math.PI * 2 * s / sides, t1 = Math.PI * 2 * (s + 1) / sides;
            float c0 = (float) Math.cos(t0), s0 = (float) Math.sin(t0), c1 = (float) Math.cos(t1), s1 = (float) Math.sin(t1);
            f.vertex(m, cx + c0 * rIn, y, cz + s0 * rIn).color(ci);
            f.vertex(m, cx + c0 * rOut, y, cz + s0 * rOut).color(co);
            f.vertex(m, cx + c1 * rOut, y, cz + s1 * rOut).color(co);
            f.vertex(m, cx + c1 * rIn, y, cz + s1 * rIn).color(ci);
        }
    }

    private static void vquad(VertexConsumer f, Matrix4f m, float x1, float z1, float x2, float z2, float ya, float yb, int bottom, int top) {
        f.vertex(m, x1, ya, z1).color(bottom);
        f.vertex(m, x2, ya, z2).color(bottom);
        f.vertex(m, x2, yb, z2).color(top);
        f.vertex(m, x1, yb, z1).color(top);
    }

    private static final class ColorUtil2 {
        static int whiten(int argb, float t) {
            int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
            r += (int) ((255 - r) * t); g += (int) ((255 - g) * t); b += (int) ((255 - b) * t);
            return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
        }
    }

    /**
     * Animated flat chunk square (used by Sus Chunk Finder and Player Detector): pulsing fill, glowing
     * edges, a sweeping scan line, a faint 4x4 grid and bright corner brackets. Drawn through walls.
     */
    public void chunkSquare(double x1, double z1, double x2, double z2, double y, int color, double a, boolean outline) {
        double t = (System.currentTimeMillis() % 3_600_000L) / 1000.0;
        int rgb = color & 0xFFFFFF;
        double pulse = 0.82 + 0.18 * Math.sin(t * 2.6);
        quadXZ(x1, z1, x2, z2, y, argbA(rgb, a * 0.55 * pulse));
        double e = 1.6;
        int in = argbA(rgb, 0), out = argbA(rgb, a * 0.75 * pulse);
        quadXZ4(x1, z1, x2, z1 + e, y + 0.005, out, out, in, in, true);
        quadXZ4(x1, z2 - e, x2, z2, y + 0.005, in, in, out, out, true);
        quadXZ4(x1, z1, x1 + e, z2, y + 0.005, out, in, in, out, true);
        quadXZ4(x2 - e, z1, x2, z2, y + 0.005, in, out, out, in, true);
        double sweep = (t % 2.4) / 2.4;
        double sx = x1 + (x2 - x1) * sweep, sw = 1.2;
        int lineC = argbA(rgb, a * 0.9), clear = argbA(rgb, 0);
        quadXZ4(Math.max(x1, sx - sw), z1, sx, z2, y + 0.01, clear, lineC, lineC, clear, true);
        quadXZ4(sx, z1, Math.min(x2, sx + 0.25), z2, y + 0.01, lineC, clear, clear, lineC, true);
        int grid = argbA(rgb, a * 0.35);
        float prev = lineWidth;
        setLineWidth(1f);
        for (int i = 1; i < 4; i++) {
            double gx = x1 + (x2 - x1) * i / 4, gz = z1 + (z2 - z1) * i / 4;
            line(new Vec3d(gx, y + 0.02, z1), new Vec3d(gx, y + 0.02, z2), grid);
            line(new Vec3d(x1, y + 0.02, gz), new Vec3d(x2, y + 0.02, gz), grid);
        }
        int bracket = argbA(ColorUtil2.whiten(0xFF000000 | rgb, 0.45f) & 0xFFFFFF, Math.min(1, a + 0.35));
        double L = 3.5, yy = y + 0.03;
        setLineWidth(3f);
        double[][] corners = {{x1, z1, 1, 1}, {x2, z1, -1, 1}, {x2, z2, -1, -1}, {x1, z2, 1, -1}};
        for (double[] c : corners) {
            line(new Vec3d(c[0], yy, c[1]), new Vec3d(c[0] + c[2] * L, yy, c[1]), bracket);
            line(new Vec3d(c[0], yy, c[1]), new Vec3d(c[0], yy, c[1] + c[3] * L), bracket);
        }
        if (outline) {
            setLineWidth(1.5f);
            boxOutline(new Box(x1, y, z1, x2, y, z2), argbA(rgb, Math.min(1, a + 0.2)));
        }
        setLineWidth(prev);
    }

    private static int argbA(int rgb, double alpha) {
        return ((int) (Math.max(0, Math.min(1, alpha)) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    /** Horizontal quad with a colour per corner (x1z1, x2z1, x2z2, x1z2); {@code glow} = additive light. */
    public void quadXZ4(double x1, double z1, double x2, double z2, double y, int c11, int c21, int c22, int c12, boolean glow) {
        Matrix4f m = matrices.peek().getPositionMatrix();
        VertexConsumer f = glow ? consumers.getBuffer(StellaPipelines.FILLS_GLOW) : fills();
        float ax = (float) (x1 - cam.x), az = (float) (z1 - cam.z), bx = (float) (x2 - cam.x), bz = (float) (z2 - cam.z), yy = (float) (y - cam.y);
        f.vertex(m, ax, yy, az).color(c11);
        f.vertex(m, bx, yy, az).color(c21);
        f.vertex(m, bx, yy, bz).color(c22);
        f.vertex(m, ax, yy, bz).color(c12);
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
        consumers.draw(StellaPipelines.FILLS_GLOW);
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
