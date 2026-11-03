package dev.stella.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Batched world-space renderer. All modules push boxes/lines into one batch per
 * frame, which is drawn with a handful of draw calls (outlines, fills).
 * Coordinates are absolute world coordinates; the camera offset is applied here.
 */
public final class Render3D {
    private static final BufferAllocator LINE_ALLOC = new BufferAllocator(1 << 20);
    private static final BufferAllocator FILL_ALLOC = new BufferAllocator(1 << 20);
    private static final BufferAllocator TRACER_ALLOC = new BufferAllocator(1 << 18);
    private BufferBuilder tracers;
    private float tracerWidth = 1f;

    private final MatrixStack matrices;
    private final Vec3d cam;
    private BufferBuilder lines;
    private BufferBuilder fills;
    private float lineWidth = 2f;
    private final float tickDelta;

    public Render3D(MatrixStack matrices, Vec3d cameraPos, float tickDelta) {
        this.matrices = matrices;
        this.cam = cameraPos;
        this.tickDelta = tickDelta;
    }

    public float tickDelta() { return tickDelta; }
    public Vec3d cameraPos() { return cam; }

    public void setLineWidth(float w) { this.lineWidth = Math.max(lineWidth, w); }

    private BufferBuilder lines() {
        if (lines == null) lines = new BufferBuilder(LINE_ALLOC, VertexFormat.DrawMode.LINES, VertexFormats.LINES);
        return lines;
    }

    private BufferBuilder fills() {
        if (fills == null) fills = new BufferBuilder(FILL_ALLOC, VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        return fills;
    }

    public void boxOutline(Box box, int argb) {
        Box b = box.offset(-cam.x, -cam.y, -cam.z);
        VertexRendering.drawBox(matrices, lines(), b,
                ((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f, ((argb >>> 24) & 0xFF) / 255f);
    }

    public void boxFill(Box box, int argb) {
        Box b = box.offset(-cam.x, -cam.y, -cam.z);
        Matrix4f m = matrices.peek().getPositionMatrix();
        BufferBuilder f = fills();
        float x1 = (float) b.minX, y1 = (float) b.minY, z1 = (float) b.minZ;
        float x2 = (float) b.maxX, y2 = (float) b.maxY, z2 = (float) b.maxZ;
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, bl = argb & 0xFF, a = (argb >>> 24) & 0xFF;
        // bottom
        f.vertex(m, x1, y1, z1).color(r, g, bl, a); f.vertex(m, x2, y1, z1).color(r, g, bl, a);
        f.vertex(m, x2, y1, z2).color(r, g, bl, a); f.vertex(m, x1, y1, z2).color(r, g, bl, a);
        // top
        f.vertex(m, x1, y2, z1).color(r, g, bl, a); f.vertex(m, x1, y2, z2).color(r, g, bl, a);
        f.vertex(m, x2, y2, z2).color(r, g, bl, a); f.vertex(m, x2, y2, z1).color(r, g, bl, a);
        // north
        f.vertex(m, x1, y1, z1).color(r, g, bl, a); f.vertex(m, x1, y2, z1).color(r, g, bl, a);
        f.vertex(m, x2, y2, z1).color(r, g, bl, a); f.vertex(m, x2, y1, z1).color(r, g, bl, a);
        // south
        f.vertex(m, x1, y1, z2).color(r, g, bl, a); f.vertex(m, x2, y1, z2).color(r, g, bl, a);
        f.vertex(m, x2, y2, z2).color(r, g, bl, a); f.vertex(m, x1, y2, z2).color(r, g, bl, a);
        // west
        f.vertex(m, x1, y1, z1).color(r, g, bl, a); f.vertex(m, x1, y1, z2).color(r, g, bl, a);
        f.vertex(m, x1, y2, z2).color(r, g, bl, a); f.vertex(m, x1, y2, z1).color(r, g, bl, a);
        // east
        f.vertex(m, x2, y1, z1).color(r, g, bl, a); f.vertex(m, x2, y2, z1).color(r, g, bl, a);
        f.vertex(m, x2, y2, z2).color(r, g, bl, a); f.vertex(m, x2, y1, z2).color(r, g, bl, a);
    }

    public void box(Box box, int outline, int fill, boolean drawOutline, boolean drawFill) {
        if (drawFill) boxFill(box, fill);
        if (drawOutline) boxOutline(box, outline);
    }

    /** Thin anti-blocky tracer line (drawn in real screen pixels, not GUI pixels). */
    public void tracer(Vec3d from, Vec3d to, int argb, float width) {
        if (tracers == null) tracers = new BufferBuilder(TRACER_ALLOC, VertexFormat.DrawMode.LINES, VertexFormats.LINES);
        tracerWidth = Math.max(tracerWidth, width);
        emitLine(tracers, from, to, argb);
    }

    public void line(Vec3d a, Vec3d b, int argb) { emitLine(lines(), a, b, argb); }

    private void emitLine(BufferBuilder buf, Vec3d a, Vec3d b, int argb) {
        Matrix4f m = matrices.peek().getPositionMatrix();
        float ax = (float) (a.x - cam.x), ay = (float) (a.y - cam.y), az = (float) (a.z - cam.z);
        float bx = (float) (b.x - cam.x), by = (float) (b.y - cam.y), bz = (float) (b.z - cam.z);
        float nx = bx - ax, ny = by - ay, nz = bz - az;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-5f) return;
        nx /= len; ny /= len; nz /= len;
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, bl = argb & 0xFF, al = (argb >>> 24) & 0xFF;
        MatrixStack.Entry e = matrices.peek();
        buf.vertex(m, ax, ay, az).color(r, g, bl, al).normal(e, nx, ny, nz);
        buf.vertex(m, bx, by, bz).color(r, g, bl, al).normal(e, nx, ny, nz);
    }

    /** Flushes all batched geometry. Rendered through walls (no depth test). */
    public void flush() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        if (fills != null) {
            BuiltBuffer built = fills.endNullable();
            fills = null;
            if (built != null) {
                RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
                BufferRenderer.drawWithGlobalProgram(built);
            }
        }
        if (lines != null) {
            BuiltBuffer built = lines.endNullable();
            lines = null;
            if (built != null) {
                RenderSystem.setShader(ShaderProgramKeys.RENDERTYPE_LINES);
                RenderSystem.lineWidth(lineWidth);
                BufferRenderer.drawWithGlobalProgram(built);
                RenderSystem.lineWidth(1f);
            }
        }

        if (tracers != null) {
            BuiltBuffer built = tracers.endNullable();
            tracers = null;
            if (built != null) {
                RenderSystem.setShader(ShaderProgramKeys.RENDERTYPE_LINES);
                RenderSystem.lineWidth(tracerWidth);
                BufferRenderer.drawWithGlobalProgram(built);
                RenderSystem.lineWidth(1f);
            }
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
}
