package dev.stella.client.modules.cosmetics;

import dev.stella.client.util.ColorUtil;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Tiny mesh builder for the knife models: opaque quads (per-vertex colour) plus an additive glow list.
 * Units are blocks; the grip sits at the origin and the blade points up +Y, flat faces toward ±Z.
 */
final class KnifeMesh {
    /** 4 vertices (x,y,z) + 4 colours. */
    record Quad(float[] p, int[] c) {}

    final List<Quad> solid = new ArrayList<>();
    final List<Quad> glow = new ArrayList<>();
    /** Applied to every opaque colour (world light level). */
    float light = 1f;

    // ------------------------------------------------------------------ emit

    void emitSolid(MatrixStack.Entry e, VertexConsumer vc) { emit(e, vc, solid); }
    void emitGlow(MatrixStack.Entry e, VertexConsumer vc) { emit(e, vc, glow); }

    private static void emit(MatrixStack.Entry e, VertexConsumer vc, List<Quad> list) {
        for (Quad q : list)
            for (int i = 0; i < 4; i++) vc.vertex(e, q.p[i * 3], q.p[i * 3 + 1], q.p[i * 3 + 2]).color(q.c[i]);
    }

    // ------------------------------------------------------------------ colour helpers

    static int shade(int c, float f) {
        int a = c >>> 24;
        int r = (int) (((c >> 16) & 0xFF) * f), g = (int) (((c >> 8) & 0xFF) * f), b = (int) ((c & 0xFF) * f);
        return (a << 24) | (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
    }

    int lit(int c, float f) { return shade(c, f * light) | 0xFF000000; }

    // ------------------------------------------------------------------ primitives

    void quad(float[] a, float[] b, float[] c, float[] d, int ca, int cb, int cc, int cd) {
        solid.add(new Quad(new float[]{a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2], d[0], d[1], d[2]}, new int[]{ca, cb, cc, cd}));
    }

    void quad(float[] a, float[] b, float[] c, float[] d, int col) { quad(a, b, c, d, col, col, col, col); }

    void glowQuad(float[] a, float[] b, float[] c, float[] d, int ca, int cb, int cc, int cd) {
        glow.add(new Quad(new float[]{a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2], d[0], d[1], d[2]}, new int[]{ca, cb, cc, cd}));
    }

    static float[] v(float x, float y, float z) { return new float[]{x, y, z}; }

    /** Shaded box; {@code top}/{@code bottom} colours allow a vertical gradient. */
    void box(float x0, float y0, float z0, float x1, float y1, float z1, int bottom, int top) {
        float[] a = v(x0, y0, z0), b = v(x1, y0, z0), c = v(x1, y1, z0), d = v(x0, y1, z0);
        float[] e = v(x0, y0, z1), f = v(x1, y0, z1), g = v(x1, y1, z1), h = v(x0, y1, z1);
        quad(a, b, c, d, lit(bottom, 0.78f), lit(bottom, 0.78f), lit(top, 0.78f), lit(top, 0.78f));
        quad(e, f, g, h, lit(bottom, 1f), lit(bottom, 1f), lit(top, 1f), lit(top, 1f));
        quad(a, e, h, d, lit(bottom, 0.64f), lit(bottom, 0.64f), lit(top, 0.64f), lit(top, 0.64f));
        quad(b, f, g, c, lit(bottom, 0.88f), lit(bottom, 0.88f), lit(top, 0.88f), lit(top, 0.88f));
        quad(d, c, g, h, lit(top, 1.12f));
        quad(a, b, f, e, lit(bottom, 0.5f));
    }

    void box(float x0, float y0, float z0, float x1, float y1, float z1, int c) { box(x0, y0, z0, x1, y1, z1, c, c); }

    /** Metal part: gunmetal-to-silver gradient with a bright top. */
    void metal(float x0, float y0, float z0, float x1, float y1, float z1, int tint) {
        box(x0, y0, z0, x1, y1, z1, ColorUtil.lerp(0xFF3A3C46, tint, 0.25), ColorUtil.lerp(0xFFD8DCE6, tint, 0.25));
    }

    /** Ring (torus-like, square tube) in the XY plane. */
    void ring(float cx, float cy, float r, float tube, int inner, int outer) {
        int n = 20;
        for (int i = 0; i < n; i++) {
            double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float[] i0 = v(cx + c0 * r, cy + s0 * r, 0), i1 = v(cx + c1 * r, cy + s1 * r, 0);
            float[] o0 = v(cx + c0 * (r + tube), cy + s0 * (r + tube), 0), o1 = v(cx + c1 * (r + tube), cy + s1 * (r + tube), 0);
            float h = tube * 0.6f;
            float lf = 0.85f + 0.25f * s0; // light from above
            quad(z(i0, h), z(o0, h), z(o1, h), z(i1, h), lit(inner, lf), lit(outer, lf), lit(outer, lf), lit(inner, lf));
            quad(z(i0, -h), z(o0, -h), z(o1, -h), z(i1, -h), lit(inner, lf * 0.75f), lit(outer, lf * 0.75f), lit(outer, lf * 0.75f), lit(inner, lf * 0.75f));
            quad(z(o0, h), z(o0, -h), z(o1, -h), z(o1, h), lit(outer, lf * 0.9f));
            quad(z(i0, h), z(i0, -h), z(i1, -h), z(i1, h), lit(inner, lf * 0.6f));
        }
    }

    private static float[] z(float[] p, float z) { return v(p[0], p[1], z); }

    /** Five-point star prism in the XY plane, squashed vertically by {@code sy}. */
    void star(float cx, float cy, float outer, float inner, float sy, float th, int c1, int c2, boolean glowEdge) {
        for (int i = 0; i < 10; i++) {
            double a0 = i * Math.PI / 5 + Math.PI / 2, a1 = (i + 1) * Math.PI / 5 + Math.PI / 2;
            float r0 = i % 2 == 0 ? outer : inner, r1 = i % 2 == 0 ? inner : outer;
            float[] p0 = v(cx + (float) Math.cos(a0) * r0, cy + (float) Math.sin(a0) * r0 * sy, 0);
            float[] p1 = v(cx + (float) Math.cos(a1) * r1, cy + (float) Math.sin(a1) * r1 * sy, 0);
            int ca = ColorUtil.lerp(c1, c2, i / 10f), cb = ColorUtil.lerp(c1, c2, (i + 1) / 10f);
            // raised centre: the middle is thicker than the points (bevelled star)
            quad(v(cx, cy, th * 1.6f), z(p0, th * 0.5f), z(p1, th * 0.5f), z(p1, th * 0.5f), lit(0xFFFFFFFF, 1f), lit(ca, 1.1f), lit(cb, 1.1f), lit(cb, 1.1f));
            quad(v(cx, cy, -th * 1.6f), z(p0, -th * 0.5f), z(p1, -th * 0.5f), z(p1, -th * 0.5f), lit(ca, 0.9f), lit(ca, 0.75f), lit(cb, 0.75f), lit(cb, 0.75f));
            quad(z(p0, th * 0.5f), z(p0, -th * 0.5f), z(p1, -th * 0.5f), z(p1, th * 0.5f), lit(ca, 0.65f));
            if (glowEdge) {
                float k = 1.25f;
                float[] q0 = v(cx + (p0[0] - cx) * k, cy + (p0[1] - cy) * k, 0), q1 = v(cx + (p1[0] - cx) * k, cy + (p1[1] - cy) * k, 0);
                int g = ColorUtil.withAlpha(c2, 0x70);
                glowQuad(z(p0, 0), z(q0, 0), z(q1, 0), z(p1, 0), g, ColorUtil.withAlpha(g, 0), ColorUtil.withAlpha(g, 0), g);
            }
        }
    }

    /** Faceted gem (octahedron-ish), glowing. */
    void gem(float cx, float cy, float r, int c) {
        float[] top = v(cx, cy + r * 1.2f, 0), bot = v(cx, cy - r * 1.2f, 0);
        int n = 6;
        for (int i = 0; i < n; i++) {
            double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
            float[] p0 = v(cx + (float) Math.cos(a0) * r, cy, (float) Math.sin(a0) * r), p1 = v(cx + (float) Math.cos(a1) * r, cy, (float) Math.sin(a1) * r);
            float f = 0.75f + 0.3f * (float) Math.sin(a0 + 1);
            quad(top, p0, p1, p1, lit(0xFFFFFFFF, 1f), lit(c, f + 0.2f), lit(c, f + 0.2f), lit(c, f + 0.2f));
            quad(bot, p0, p1, p1, lit(c, 0.5f), lit(c, f), lit(c, f), lit(c, f));
        }
        int g = ColorUtil.withAlpha(c, 0x90);
        float gr = r * 2.6f;
        glowQuad(v(cx - gr, cy, 0), v(cx, cy - gr, 0), v(cx + gr, cy, 0), v(cx, cy + gr, 0), ColorUtil.withAlpha(g, 0), ColorUtil.withAlpha(g, 0), ColorUtil.withAlpha(g, 0), ColorUtil.withAlpha(g, 0));
        float s = r * 0.9f;
        glowQuad(v(cx - s, cy, 0.001f), v(cx, cy - s, 0.001f), v(cx + s, cy, 0.001f), v(cx, cy + s, 0.001f), g, g, g, g);
    }

    /** Small four-point sparkle on a blade face (additive). */
    void sparkle(float x, float y, float z, float r, int c) {
        int cc = ColorUtil.withAlpha(c, 0xE0), clear = ColorUtil.withAlpha(c, 0);
        glowQuad(v(x - r, y, z), v(x, y - r * 0.2f, z), v(x + r, y, z), v(x, y + r * 0.2f, z), clear, cc, clear, cc);
        glowQuad(v(x, y - r, z), v(x + r * 0.2f, y, z), v(x, y + r, z), v(x - r * 0.2f, y, z), clear, cc, clear, cc);
    }

    // ------------------------------------------------------------------ blade

    /**
     * Blade from cross-sections. Each section i has a spine point (sx, sy) and an edge point (ex, ey);
     * the edge is ground (bevel to zero thickness), the spine is flat. {@code finish} colours the flat
     * by (across 0..1, along 0..1, side). Double-edged blades bevel both sides from a centre ridge.
     */
    void blade(float[][] spine, float[][] edge, float th, boolean doubleEdge, Finish finish, float shine, int edgeGlow) {
        int n = spine.length;
        int across = 7;
        for (int i = 0; i < n - 1; i++) {
            float s0 = i / (float) (n - 1), s1 = (i + 1) / (float) (n - 1);
            for (int side = -1; side <= 1; side += 2) {
                float fz = side;
                float sideShade = side > 0 ? 1.0f : 0.8f;
                if (!doubleEdge) {
                    // flat: spine -> ridge (55% across), subdivided for the pattern
                    for (int k = 0; k < across; k++) {
                        float u0 = 0.55f * k / across, u1 = 0.55f * (k + 1) / across;
                        float[] a = mix(spine[i], edge[i], u0, th * fz), b = mix(spine[i], edge[i], u1, th * fz);
                        float[] c = mix(spine[i + 1], edge[i + 1], u1, th * fz), d = mix(spine[i + 1], edge[i + 1], u0, th * fz);
                        quad(a, b, c, d, fcol(finish, u0, s0, side, shine, sideShade), fcol(finish, u1, s0, side, shine, sideShade),
                                fcol(finish, u1, s1, side, shine, sideShade), fcol(finish, u0, s1, side, shine, sideShade));
                    }
                    // bevel: ridge -> edge, thickness falls to zero, polished (lighter, silvery)
                    float[] a = mix(spine[i], edge[i], 0.55f, th * fz), b = mix(spine[i], edge[i], 1f, 0), c = mix(spine[i + 1], edge[i + 1], 1f, 0), d = mix(spine[i + 1], edge[i + 1], 0.55f, th * fz);
                    quad(a, b, c, d, bev(finish, 0.55f, s0, side, shine, sideShade), bev(finish, 1f, s0, side, shine, sideShade),
                            bev(finish, 1f, s1, side, shine, sideShade), bev(finish, 0.55f, s1, side, shine, sideShade));
                } else {
                    // two bevels from a centre ridge
                    for (int half = 0; half < 2; half++) {
                        float[][] outer = half == 0 ? spine : edge;
                        float[] a = mix(spine[i], edge[i], 0.5f, th * fz), d = mix(spine[i + 1], edge[i + 1], 0.5f, th * fz);
                        float[] b = mix(outer[i], outer[i], 0, 0), c = mix(outer[i + 1], outer[i + 1], 0, 0);
                        int ca = fcol(finish, 0.5f, s0, side, shine, sideShade * (half == 0 ? 1.05f : 0.92f));
                        int cd = fcol(finish, 0.5f, s1, side, shine, sideShade * (half == 0 ? 1.05f : 0.92f));
                        quad(a, b, c, d, ca, bev(finish, 1f, s0, side, shine, sideShade), bev(finish, 1f, s1, side, shine, sideShade), cd);
                    }
                }
            }
            if (!doubleEdge) {
                // spine strip
                float[] a = mix(spine[i], edge[i], 0, th), b = mix(spine[i], edge[i], 0, -th), c = mix(spine[i + 1], edge[i + 1], 0, -th), d = mix(spine[i + 1], edge[i + 1], 0, th);
                int sc = lit(ColorUtil.lerp(finish.at(0, s0, 1), 0xFF2A2A33, 0.35f), 0.7f);
                quad(a, b, c, d, sc);
            }
            if (edgeGlow != 0) {
                // neon edge line on both faces
                for (int side = -1; side <= 1; side += 2) {
                    float[] a = mix(spine[i], edge[i], 0.86f, th * 0.25f * side), b = mix(spine[i], edge[i], 1.06f, 0.0005f * side);
                    float[] c = mix(spine[i + 1], edge[i + 1], 1.06f, 0.0005f * side), d = mix(spine[i + 1], edge[i + 1], 0.86f, th * 0.25f * side);
                    int g = ColorUtil.withAlpha(edgeGlow, 0x55), g2 = ColorUtil.withAlpha(edgeGlow, 0xB0);
                    glowQuad(a, b, c, d, ColorUtil.withAlpha(g, 0), g2, g2, ColorUtil.withAlpha(g, 0));
                }
            }
        }
    }

    private int fcol(Finish f, float u, float s, int side, float shine, float shade) {
        int c = f.at(u, s, side);
        // moving specular band along the blade
        double d = s - shine;
        float hl = (float) Math.exp(-d * d / 0.006) * 0.55f;
        return lit(ColorUtil.lerp(c, 0xFFFFFFFF, hl), shade);
    }

    private int bev(Finish f, float u, float s, int side, float shine, float shade) {
        int c = ColorUtil.lerp(f.at(u, s, side), 0xFFE6E9F2, 0.45f);
        double d = s - shine;
        float hl = (float) Math.exp(-d * d / 0.004) * 0.7f;
        return lit(ColorUtil.lerp(c, 0xFFFFFFFF, hl), shade * 1.08f);
    }

    /** Point between spine and edge at fraction u, with thickness z. */
    private static float[] mix(float[] sp, float[] ed, float u, float z) {
        return v(sp[0] + (ed[0] - sp[0]) * u, sp[1] + (ed[1] - sp[1]) * u, z);
    }

    /** Blade surface colour by position. */
    interface Finish {
        int at(float across, float along, int side);
    }

}
