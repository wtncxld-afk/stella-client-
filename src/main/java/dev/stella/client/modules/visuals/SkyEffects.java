package dev.stella.client.modules.visuals;

import dev.stella.client.render.StellaPipelines;
import dev.stella.client.util.ColorUtil;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Random;

/** The signature effects of each sky design, drawn on the sky dome after the gradient and stars. */
final class SkyEffects {
    private SkyEffects() {}

    private static float[][] milky, galaxy, parts;

    private static void build() {
        if (milky != null) return;
        Random r = new Random(0x5E11A);
        milky = new float[2200][];
        for (int i = 0; i < milky.length; i++) {
            double th = r.nextDouble() * Math.PI * 2, off = r.nextGaussian() * 0.09 + r.nextGaussian() * 0.03;
            milky[i] = new float[]{(float) th, (float) off, 0.3f + r.nextFloat() * r.nextFloat() * 1.2f, r.nextFloat(), r.nextFloat() * 6.28f};
        }
        galaxy = new float[1800][];
        for (int i = 0; i < galaxy.length; i++) {
            double rr = Math.pow(r.nextDouble(), 0.7), arm = r.nextInt(2) * Math.PI;
            double ang = rr * Math.PI * 3.2 + arm + r.nextGaussian() * 0.28 * (1.1 - rr);
            galaxy[i] = new float[]{(float) rr, (float) ang, (float) (r.nextGaussian() * 0.04), 0.4f + r.nextFloat() * 0.8f};
        }
        parts = new float[150][];
        for (int i = 0; i < parts.length; i++)
            parts[i] = new float[]{(float) (r.nextDouble() * Math.PI * 2), r.nextFloat(), 0.5f + r.nextFloat(), r.nextFloat() * 6.28f, 0.6f + r.nextFloat() * 0.8f};
    }

    static void render(CustomSky sky, SkyStyles.Style s, Matrix4f m, VertexConsumerProvider.Immediate consumers, float radius,
                       float night, float inten, long time, boolean body, boolean shooting, boolean milkyWay, boolean particles, boolean rays) {
        build();
        double t = (time % 3_600_000L) / 1000.0;
        float R = radius * 0.93f;

        // ---- milky way: a glowing band of tiny stars across the sky
        if (milkyWay && s.milkyWay() != 0 && night > 0.02f) {
            Vector3f n = new Vector3f(0.35f, 1f, 0.55f).normalize();
            Vector3f[] b = basis(n);
            VertexConsumer g = consumers.getBuffer(StellaPipelines.SKY_GLOW_Q);
            int segs = 72;
            for (int i = 0; i < segs; i++) {
                double a0 = Math.PI * 2 * i / segs, a1 = Math.PI * 2 * (i + 1) / segs;
                for (int side = -1; side <= 1; side += 2) {
                    Vector3f p0 = ring(b, n, a0, 0), p1 = ring(b, n, a1, 0), q0 = ring(b, n, a0, side * 0.16), q1 = ring(b, n, a1, side * 0.16);
                    if (p0.y < -0.05f && p1.y < -0.05f) continue;
                    double w = 0.55 + 0.45 * Math.sin(a0 * 3 + 1.3);
                    int c = ColorUtil.withAlpha(s.milkyWay(), (int) (0x26 * w * night * inten));
                    quad(g, m, p0, p1, q1, q0, R, c, c, s.milkyWay() & 0xFFFFFF, s.milkyWay() & 0xFFFFFF);
                }
            }
            for (float[] st : milky) {
                Vector3f p = ring(b, n, st[0], st[1]);
                if (p.y < -0.1f) continue;
                float tw = (float) (0.6 + 0.4 * Math.sin(t * 2 + st[4]));
                int c = ColorUtil.withAlpha(ColorUtil.lerp(s.milkyWay(), 0xFFFFFFFF, st[3]), (int) (200 * tw * night * inten));
                star(g, m, p, R * 0.99f, st[2] * radius / 600f, c);
            }
            consumers.draw(StellaPipelines.SKY_GLOW_Q);
        }

        // ---- spiral galaxy: two arms of stars around a bright core, slowly turning
        if (s.galaxy() != 0 && night > 0.02f) {
            Vector3f c0 = dir(1.15, 0.5);
            Vector3f[] b = basis(c0);
            float size = 0.42f;
            double spin = t * 0.01;
            VertexConsumer g = consumers.getBuffer(StellaPipelines.SKY_GLOW_Q);
            for (float[] st : galaxy) {
                double ang = st[1] + spin;
                float x = (float) (Math.cos(ang) * st[0] * size), y = (float) (Math.sin(ang) * st[0] * size * 0.42 + st[2]);
                Vector3f p = new Vector3f(c0).add(new Vector3f(b[0]).mul(x)).add(new Vector3f(b[1]).mul(y)).normalize();
                int col = st[0] < 0.18f ? 0xFFFFF4E0 : ColorUtil.lerp(s.galaxy(), 0xFFF472B6, st[0]);
                star(g, m, p, R, st[3] * radius / (st[0] < 0.18f ? 300f : 520f), ColorUtil.withAlpha(col, (int) (220 * night * inten * (1.1f - st[0] * 0.6f))));
            }
            consumers.draw(StellaPipelines.SKY_GLOW_Q);
            VertexConsumer f = consumers.getBuffer(StellaPipelines.SKY_GLOW);
            fan(f, m, c0, b, R, size * 0.32f, 0.42f, ColorUtil.withAlpha(0xFFFFE8C8, (int) (200 * night * inten)), 0);
            fan(f, m, c0, b, R, size * 1.05f, 0.42f, ColorUtil.withAlpha(s.galaxy(), (int) (70 * night * inten)), 0);
            consumers.draw(StellaPipelines.SKY_GLOW);
        }

        // ---- rainbow arc
        if (s.rainbow()) {
            Vector3f c0 = dir(4.0, -0.25);
            Vector3f[] b = basis(c0);
            VertexConsumer g = consumers.getBuffer(StellaPipelines.SKY_GLOW_Q);
            int segs = 80;
            for (int i = 0; i < segs; i++) {
                double a0 = Math.PI * 2 * i / segs, a1 = Math.PI * 2 * (i + 1) / segs;
                for (int band = 0; band < 6; band++) {
                    float r0 = 0.62f + band * 0.022f, r1 = r0 + 0.022f;
                    Vector3f p0 = onCircle(c0, b, a0, r0), p1 = onCircle(c0, b, a1, r0), q0 = onCircle(c0, b, a0, r1), q1 = onCircle(c0, b, a1, r1);
                    if (p0.y < 0 && p1.y < 0) continue;
                    int col = ColorUtil.withAlpha(ColorUtil.fromHsb(band / 6.5f, 0.85f, 1f, 255), (int) (40 * inten));
                    quad(g, m, p0, p1, q1, q0, R, col, col, col, col);
                }
            }
            consumers.draw(StellaPipelines.SKY_GLOW_Q);
        }

        // ---- moon / sun with halo, crescent shadow and god rays
        if (body && s.body() != SkyStyles.NONE) {
            Vector3f d = dir(s.bodyAzimuth() + t * 0.002, s.bodyElevation());
            Vector3f[] b = basis(d);
            float vis = s.body() == SkyStyles.SUN ? 1f : Math.max(0.35f, night);
            VertexConsumer glow = consumers.getBuffer(StellaPipelines.SKY_GLOW);
            fan(glow, m, d, b, R, s.bodySize() * 5.5f, 1f, ColorUtil.withAlpha(s.bodyColor(), (int) (45 * vis * inten)), 0);
            fan(glow, m, d, b, R, s.bodySize() * 2.3f, 1f, ColorUtil.withAlpha(s.bodyColor(), (int) (110 * vis * inten)), 0);
            if (rays && s.rays()) {
                int n = 14;
                for (int i = 0; i < n; i++) {
                    double a = t * 0.05 + i * Math.PI * 2 / n;
                    float len = s.bodySize() * (7f + 3f * (float) Math.sin(i * 2.3 + t * 0.3)), w = 0.035f;
                    Vector3f tip = add(d, b, Math.cos(a) * len, Math.sin(a) * len);
                    Vector3f l = add(d, b, Math.cos(a + w) * s.bodySize(), Math.sin(a + w) * s.bodySize());
                    Vector3f rr = add(d, b, Math.cos(a - w) * s.bodySize(), Math.sin(a - w) * s.bodySize());
                    int c = ColorUtil.withAlpha(s.bodyColor(), (int) ((i % 2 == 0 ? 40 : 22) * vis * inten));
                    v(glow, m, l, R, c); v(glow, m, rr, R, c); v(glow, m, tip, R, c & 0xFFFFFF);
                }
            }
            consumers.draw(StellaPipelines.SKY_GLOW);
            VertexConsumer disc = consumers.getBuffer(StellaPipelines.SKY);
            discFan(disc, m, d, b, R * 0.995f, s.bodySize(), ColorUtil.withAlpha(ColorUtil.lerp(s.bodyColor(), 0xFFFFFFFF, 0.35), (int) (255 * vis)),
                    ColorUtil.withAlpha(s.bodyColor(), (int) (255 * vis)));
            if (s.body() == SkyStyles.CRESCENT) {
                Vector3f off = add(d, b, s.bodySize() * 0.42, s.bodySize() * 0.18);
                Vector3f[] b2 = basis(off);
                discFan(disc, m, off, b2, R * 0.993f, s.bodySize() * 0.98f, s.top() | 0xFF000000, ColorUtil.lerp(s.top(), s.horizon(), 0.2) | 0xFF000000);
            } else if (s.body() == SkyStyles.MOON) {
                // a few soft craters
                for (int i = 0; i < 4; i++) {
                    Vector3f cc = add(d, b, Math.cos(i * 1.9) * s.bodySize() * 0.5, Math.sin(i * 1.9 + 0.4) * s.bodySize() * 0.45);
                    discFan(disc, m, cc, basis(cc), R * 0.993f, s.bodySize() * (0.12f + 0.05f * i),
                            ColorUtil.withAlpha(ColorUtil.lerp(s.bodyColor(), 0xFF000000, 0.18), (int) (150 * vis)), ColorUtil.withAlpha(s.bodyColor(), 0));
                }
            }
            consumers.draw(StellaPipelines.SKY);
        }

        // ---- shooting stars
        if (shooting && s.shootingStars() && night > 0.05f) {
            VertexConsumer g = consumers.getBuffer(StellaPipelines.SKY_GLOW_Q);
            for (int k = 0; k < 3; k++) {
                double period = 4.2 + k * 1.3, tt = (t + k * 1.7) / period;
                long seed = (long) Math.floor(tt);
                double ph = tt - seed;
                if (ph > 0.3) continue;
                Random r = new Random(seed * 31 + k);
                double az = r.nextDouble() * Math.PI * 2, el = 0.35 + r.nextDouble() * 0.5, daz = (r.nextBoolean() ? 1 : -1) * (0.35 + r.nextDouble() * 0.3);
                double p = ph / 0.3, fade = Math.sin(p * Math.PI);
                Vector3f head = dir(az + daz * p, el - 0.28 * p), tail = dir(az + daz * (p - 0.12), el - 0.28 * (p - 0.12));
                Vector3f side = new Vector3f(head).cross(new Vector3f(tail).sub(head)).normalize().mul(0.0022f);
                int hc = ColorUtil.withAlpha(0xFFFFFFFF, (int) (255 * fade * night)), tc = 0x00FFFFFF & s.starColor();
                quad(g, m, new Vector3f(head).add(side), new Vector3f(head).sub(side), new Vector3f(tail).sub(side), new Vector3f(tail).add(side),
                        R * 0.97f, hc, hc, tc, tc);
            }
            consumers.draw(StellaPipelines.SKY_GLOW_Q);
        }

        // ---- drifting particles
        if (particles && s.particles() != SkyStyles.P_NONE) {
            boolean additive = s.particles() == SkyStyles.P_EMBERS || s.particles() == SkyStyles.P_SPARKLES || s.particles() == SkyStyles.P_BUBBLES;
            VertexConsumer g = consumers.getBuffer(additive ? StellaPipelines.SKY_GLOW_Q : StellaPipelines.SKY);
            for (float[] p : parts) {
                double az = p[0], el, size = p[4] * radius / 260f;
                float alpha;
                switch (s.particles()) {
                    case SkyStyles.P_PETALS -> { el = 0.9 - ((p[1] + t * 0.018 * p[2]) % 1.0) * 1.0; az += Math.sin(t * 0.5 + p[3]) * 0.06 + t * 0.004 * p[2]; alpha = 0.85f; }
                    case SkyStyles.P_SNOW -> { el = 0.9 - ((p[1] + t * 0.012 * p[2]) % 1.0) * 0.95; az += Math.sin(t * 0.7 + p[3]) * 0.02; alpha = 0.8f; size *= 0.6; }
                    case SkyStyles.P_EMBERS -> { el = -0.05 + ((p[1] + t * 0.02 * p[2]) % 1.0) * 0.8; az += Math.sin(t * 1.3 + p[3]) * 0.03; alpha = (float) (0.9 - el); size *= 0.5; }
                    case SkyStyles.P_BUBBLES -> { el = -0.05 + ((p[1] + t * 0.015 * p[2]) % 1.0) * 0.9; az += Math.sin(t * 0.9 + p[3]) * 0.04; alpha = 0.45f; size *= 0.9; }
                    default -> { el = 0.08 + p[1] * 0.8; alpha = (float) Math.max(0, Math.sin(t * 1.6 * p[2] + p[3])); size *= 0.8; } // sparkles
                }
                if (alpha <= 0.02f) continue;
                Vector3f d = dir(az, el);
                int c = ColorUtil.withAlpha(s.particleColor(), (int) (255 * alpha * inten));
                if (s.particles() == SkyStyles.P_PETALS) petal(g, m, d, R * 0.82f, (float) size, t * 0.8 + p[3], c);
                else if (s.particles() == SkyStyles.P_SPARKLES) sparkle(g, m, d, R * 0.85f, (float) size * 1.6f, c);
                else star(g, m, d, R * 0.84f, (float) size, c);
            }
            consumers.draw(additive ? StellaPipelines.SKY_GLOW_Q : StellaPipelines.SKY);
        }
    }

    // ------------------------------------------------------------------ geometry helpers

    private static Vector3f dir(double azimuth, double elevation) {
        double c = Math.cos(elevation);
        return new Vector3f((float) (c * Math.sin(azimuth)), (float) Math.sin(elevation), (float) (-c * Math.cos(azimuth)));
    }

    private static Vector3f[] basis(Vector3f d) {
        Vector3f up = Math.abs(d.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
        Vector3f a = new Vector3f(d).cross(up).normalize();
        Vector3f b = new Vector3f(d).cross(a).normalize();
        return new Vector3f[]{a, b};
    }

    /** Point on the great circle with normal n, at angle a, pushed off the circle by {@code off}. */
    private static Vector3f ring(Vector3f[] b, Vector3f n, double a, double off) {
        return new Vector3f(b[0]).mul((float) Math.cos(a)).add(new Vector3f(b[1]).mul((float) Math.sin(a))).add(new Vector3f(n).mul((float) off)).normalize();
    }

    private static Vector3f onCircle(Vector3f c, Vector3f[] b, double a, double r) {
        return new Vector3f(c).add(new Vector3f(b[0]).mul((float) (Math.cos(a) * r))).add(new Vector3f(b[1]).mul((float) (Math.sin(a) * r))).normalize();
    }

    private static Vector3f add(Vector3f d, Vector3f[] b, double x, double y) {
        return new Vector3f(d).add(new Vector3f(b[0]).mul((float) x)).add(new Vector3f(b[1]).mul((float) y)).normalize();
    }

    private static void v(VertexConsumer buf, Matrix4f m, Vector3f p, float r, int argb) {
        buf.vertex(m, p.x * r, p.y * r, p.z * r).color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF);
    }

    private static void quad(VertexConsumer buf, Matrix4f m, Vector3f a, Vector3f b, Vector3f c, Vector3f d, float r, int ca, int cb, int cc, int cd) {
        v(buf, m, a, r, ca); v(buf, m, b, r, cb); v(buf, m, c, r, cc); v(buf, m, d, r, cd);
    }

    /** Small square star facing the centre. */
    private static void star(VertexConsumer buf, Matrix4f m, Vector3f d, float r, float size, int c) {
        Vector3f[] b = basis(d);
        float s = size / r;
        quad(buf, m, add(d, b, -s, -s), add(d, b, s, -s), add(d, b, s, s), add(d, b, -s, s), r, c, c, c, c);
    }

    private static void sparkle(VertexConsumer buf, Matrix4f m, Vector3f d, float r, float size, int c) {
        Vector3f[] b = basis(d);
        float s = size / r, w = s * 0.18f;
        int clear = c & 0xFFFFFF;
        quad(buf, m, add(d, b, -s, 0), add(d, b, 0, -w), add(d, b, s, 0), add(d, b, 0, w), r, clear, c, clear, c);
        quad(buf, m, add(d, b, 0, -s), add(d, b, w, 0), add(d, b, 0, s), add(d, b, -w, 0), r, clear, c, clear, c);
    }

    /** Spinning petal (a rotated, slightly curved diamond). */
    private static void petal(VertexConsumer buf, Matrix4f m, Vector3f d, float r, float size, double spin, int c) {
        Vector3f[] b = basis(d);
        float s = size / r;
        double ca = Math.cos(spin), sa = Math.sin(spin);
        float[][] pts = {{0, -1.3f}, {0.55f, 0}, {0, 1.0f}, {-0.45f, 0.05f}};
        Vector3f[] q = new Vector3f[4];
        for (int i = 0; i < 4; i++) {
            double x = pts[i][0] * s, y = pts[i][1] * s;
            q[i] = add(d, b, x * ca - y * sa, x * sa + y * ca);
        }
        int tip = ColorUtil.lerp(c, 0xFFFFFFFF, 0.4) & 0xFFFFFF | (c & 0xFF000000);
        quad(buf, m, q[0], q[1], q[2], q[3], r, tip, c, c, c);
    }

    /** Soft glow disc as triangles (centre colour fading to transparent). */
    private static void fan(VertexConsumer buf, Matrix4f m, Vector3f d, Vector3f[] b, float r, float size, float squash, int center, int edge) {
        int segs = 28;
        for (int i = 0; i < segs; i++) {
            double a0 = Math.PI * 2 * i / segs, a1 = Math.PI * 2 * (i + 1) / segs;
            v(buf, m, d, r, center);
            v(buf, m, add(d, b, Math.cos(a0) * size, Math.sin(a0) * size * squash), r, edge);
            v(buf, m, add(d, b, Math.cos(a1) * size, Math.sin(a1) * size * squash), r, edge);
        }
    }

    /** Solid disc in the quad layer (quads with a repeated centre vertex). */
    private static void discFan(VertexConsumer buf, Matrix4f m, Vector3f d, Vector3f[] b, float r, float size, int center, int edge) {
        int segs = 28;
        for (int i = 0; i < segs; i++) {
            double a0 = Math.PI * 2 * i / segs, a1 = Math.PI * 2 * (i + 1) / segs;
            Vector3f p0 = add(d, b, Math.cos(a0) * size, Math.sin(a0) * size), p1 = add(d, b, Math.cos(a1) * size, Math.sin(a1) * size);
            quad(buf, m, d, p0, p1, p1, r, center, edge, edge, edge);
        }
    }
}
