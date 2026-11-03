package dev.stella.client.modules.visuals;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.ColorSetting;
import dev.stella.client.setting.ModeSetting;
import dev.stella.client.setting.NumberSetting;
import dev.stella.client.util.ColorUtil;
import dev.stella.client.render.StellaPipelines;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Random;

/**
 * Procedural sky dome that replaces the vanilla sky (sun/moon included).
 * Drawn after opaque terrain with depth testing, at a radius between the
 * terrain and the far plane, so it only fills pixels where no terrain is.
 */
public class CustomSky extends Module {
    private final ModeSetting preset = add(new ModeSetting("Preset", "Sky style", "Cosmic Stella",
            "Cosmic Stella", "Lavender Sunset", "Blood Moon", "Arctic Aurora", "Midnight", "Custom")).onChange(this::applyPreset);
    private final ColorSetting top = add(new ColorSetting("Zenith Color", "Colour overhead", 0xFF0B0418));
    private final ColorSetting horizon = add(new ColorSetting("Horizon Color", "Colour at the horizon", 0xFF3B1466));
    private final ColorSetting bottom = add(new ColorSetting("Lower Color", "Colour below the horizon", 0xFF07030D));
    private final NumberSetting atmosphere = add(new NumberSetting("Atmosphere", "Width of the horizon glow", 0.35, 0.05, 1, 0.05));
    private final BoolSetting stars = add(new BoolSetting("Stars", "Draw stars", true));
    private final NumberSetting starDensity = add(new NumberSetting("Star Density", "", 1500, 100, 4000, 100)).visibleWhen(stars::isOn);
    private final ColorSetting starColor = add(new ColorSetting("Star Color", "", 0xFFF3E8FF)).visibleWhen(stars::isOn);
    private final BoolSetting nebula = add(new BoolSetting("Nebula", "Soft glowing clouds", true));
    private final ColorSetting nebulaColor1 = add(new ColorSetting("Nebula Color A", "", 0x80A855F7)).visibleWhen(nebula::isOn);
    private final ColorSetting nebulaColor2 = add(new ColorSetting("Nebula Color B", "", 0x60EC4899)).visibleWhen(nebula::isOn);
    private final BoolSetting aurora = add(new BoolSetting("Aurora", "Animated aurora curtains", false));
    private final ColorSetting auroraColor = add(new ColorSetting("Aurora Color", "", 0x9022D3EE)).visibleWhen(aurora::isOn);
    private final NumberSetting intensity = add(new NumberSetting("Intensity", "Overall visual intensity", 1, 0.1, 1.5, 0.05));
    private final BoolSetting dayCycle = add(new BoolSetting("Follow Day Cycle", "Brighten during the day, show stars at night", false));
    private final BoolSetting matchFog = add(new BoolSetting("Match Fog", "Tint fog to the horizon colour", true));
    private final BoolSetting hideClouds = add(new BoolSetting("Hide Clouds", "Remove vanilla clouds", false));
    private final BoolSetting overworldOnly = add(new BoolSetting("Overworld Only", "Keep the vanilla Nether/End skies", true));

    private float[][] starData;
    private float[][] nebulaData;
    private int builtStars = -1;

    public CustomSky() {
        super("Custom Sky", "Cosmic Stella, Lavender Sunset, Blood Moon, Arctic Aurora and Midnight skies", Category.VISUALS);
    }

    private void applyPreset() {
        switch (preset.get()) {
            case "Cosmic Stella" -> set(0xFF0B0418, 0xFF3B1466, 0xFF07030D, 0.35, true, 1500, 0xFFF3E8FF, true, 0x80A855F7, 0x60EC4899, false, 0x9022D3EE);
            case "Lavender Sunset" -> set(0xFF2E1F5E, 0xFFF4A3C8, 0xFF5B3A7A, 0.6, true, 300, 0xFFFFF1F8, true, 0x50C4B5FD, 0x50FDA4AF, false, 0x9022D3EE);
            case "Blood Moon" -> set(0xFF120203, 0xFF6B0F14, 0xFF0A0102, 0.45, true, 700, 0xFFFFC2C2, true, 0x60B91C1C, 0x407F1D1D, false, 0x9022D3EE);
            case "Arctic Aurora" -> set(0xFF020B1A, 0xFF0E3B5C, 0xFF01060E, 0.3, true, 1800, 0xFFE0F2FE, false, 0x6038BDF8, 0x4022D3EE, true, 0xA022D3EE);
            case "Midnight" -> set(0xFF020205, 0xFF0D0F1C, 0xFF010103, 0.25, true, 900, 0xFFCBD5E1, false, 0x40475569, 0x40334155, false, 0x9022D3EE);
            default -> { }
        }
    }

    private void set(int t, int h, int b, double atm, boolean st, int dens, int sc, boolean neb, int n1, int n2, boolean aur, int ac) {
        top.set(t); horizon.set(h); bottom.set(b); atmosphere.set(atm); stars.set(st); starDensity.set((double) dens);
        starColor.set(sc); nebula.set(neb); nebulaColor1.set(n1); nebulaColor2.set(n2); aurora.set(aur); auroraColor.set(ac);
    }

    public boolean active() {
        if (!isEnabled() || mc.world == null) return false;
        return !overworldOnly.isOn() || mc.world.getRegistryKey() == World.OVERWORLD;
    }

    public boolean hideClouds() { return active() && hideClouds.isOn(); }

    public float[] fogColor() {
        if (!active() || !matchFog.isOn()) return null;
        int c = shade(horizon.argb(), 1f);
        return new float[]{((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f};
    }

    private float daylight(float tickDelta) {
        if (!dayCycle.isOn() || mc.world == null) return 0f;
        return MathHelper.clamp(MathHelper.cos((float) (((mc.world.getTimeOfDay() - 6000L) % 24000L + 24000L) % 24000L / 24000.0 * Math.PI * 2)) * 2f + 0.5f, 0f, 1f);
    }

    private int shade(int c, float mul) {
        float d = daylight(1f);
        float m = (float) (mul * Math.min(1.5, intensity.get())) * (dayCycle.isOn() ? 0.55f + d * 0.9f : 1f);
        int r = (int) Math.min(255, ((c >> 16) & 0xFF) * m), g = (int) Math.min(255, ((c >> 8) & 0xFF) * m), b = (int) Math.min(255, (c & 0xFF) * m);
        return (c & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private void buildStatic() {
        int n = starDensity.getInt();
        if (builtStars == n && starData != null) return;
        builtStars = n;
        Random r = new Random(0x57E11AL);
        starData = new float[n][];
        for (int i = 0; i < n; i++) {
            Vector3f d = randomDir(r);
            if (d.y < -0.15f) d.y = -d.y; // keep most stars above the horizon
            starData[i] = new float[]{d.x, d.y, d.z, 0.35f + r.nextFloat() * r.nextFloat() * 1.4f, r.nextFloat() * 6.28f, 0.4f + r.nextFloat() * 0.6f};
        }
        nebulaData = new float[14][];
        for (int i = 0; i < nebulaData.length; i++) {
            Vector3f d = randomDir(r);
            d.y = Math.abs(d.y) * 0.8f + 0.1f;
            d.normalize();
            nebulaData[i] = new float[]{d.x, d.y, d.z, 0.25f + r.nextFloat() * 0.45f, r.nextFloat() < 0.5f ? 0 : 1};
        }
    }

    private static Vector3f randomDir(Random r) {
        double u = r.nextDouble() * 2 - 1, th = r.nextDouble() * Math.PI * 2, s = Math.sqrt(1 - u * u);
        return new Vector3f((float) (s * Math.cos(th)), (float) u, (float) (s * Math.sin(th)));
    }

    /** Elevation gradient: -1 (straight down) .. 1 (zenith). */
    private int gradient(float y) {
        float atm = atmosphere.getFloat();
        int c;
        if (y >= 0) {
            float t = (float) Math.pow(MathHelper.clamp(y / atm, 0, 1), 0.6);
            c = ColorUtil.lerp(horizon.argb(), top.argb(), t);
        } else {
            c = ColorUtil.lerp(horizon.argb(), bottom.argb(), MathHelper.clamp(-y / 0.25f, 0, 1));
        }
        return shade(c, 1f) | 0xFF000000;
    }

    /** Called from WorldRenderEvents.BEFORE_ENTITIES. Model-view already holds the camera rotation. */
    public void render(MatrixStack matrices, VertexConsumerProvider.Immediate consumers, float tickDelta) {
        if (!active()) return;
        buildStatic();
        float radius = Math.max(256f, mc.options.getClampedViewDistance() * 16f * 2.5f);
        Matrix4f m = new Matrix4f(matrices.peek().getPositionMatrix());
        long time = System.currentTimeMillis();
        float day = daylight(tickDelta);
        float nightAlpha = dayCycle.isOn() ? 1f - day : 1f;

        // ---- dome
        VertexConsumer buf = consumers.getBuffer(StellaPipelines.SKY);
        int lat = 24, lon = 48;
        for (int i = 0; i < lat; i++) {
            float a1 = (float) (-Math.PI / 2 + Math.PI * i / lat), a2 = (float) (-Math.PI / 2 + Math.PI * (i + 1) / lat);
            float y1 = MathHelper.sin(a1), y2 = MathHelper.sin(a2), r1 = MathHelper.cos(a1), r2 = MathHelper.cos(a2);
            int c1 = gradient(y1), c2 = gradient(y2);
            for (int j = 0; j < lon; j++) {
                float b1 = (float) (Math.PI * 2 * j / lon), b2 = (float) (Math.PI * 2 * (j + 1) / lon);
                v(buf, m, r1 * MathHelper.cos(b1) * radius, y1 * radius, r1 * MathHelper.sin(b1) * radius, c1);
                v(buf, m, r1 * MathHelper.cos(b2) * radius, y1 * radius, r1 * MathHelper.sin(b2) * radius, c1);
                v(buf, m, r2 * MathHelper.cos(b2) * radius, y2 * radius, r2 * MathHelper.sin(b2) * radius, c2);
                v(buf, m, r2 * MathHelper.cos(b1) * radius, y2 * radius, r2 * MathHelper.sin(b1) * radius, c2);
            }
        }
        consumers.draw(StellaPipelines.SKY);

        // ---- nebula (additive glow)
        if (nebula.isOn() && nightAlpha > 0.02f) {
            VertexConsumer nb = consumers.getBuffer(StellaPipelines.SKY_GLOW);
            for (float[] n : nebulaData) {
                int c = n[4] == 0 ? nebulaColor1.argb() : nebulaColor2.argb();
                int center = ColorUtil.mulAlpha(c, Math.min(1, intensity.get()) * nightAlpha);
                int edge = c & 0x00FFFFFF;
                Vector3f d = new Vector3f(n[0], n[1], n[2]);
                Vector3f[] basis = basis(d);
                float size = n[3] * radius;
                float cxp = d.x * radius * 0.97f, cyp = d.y * radius * 0.97f, czp = d.z * radius * 0.97f;
                int seg = 20;
                for (int s = 0; s < seg; s++) {
                    double t1 = Math.PI * 2 * s / seg, t2 = Math.PI * 2 * (s + 1) / seg;
                    float w1 = 0.75f + 0.25f * (float) Math.sin(t1 * 3 + n[3] * 10), w2 = 0.75f + 0.25f * (float) Math.sin(t2 * 3 + n[3] * 10);
                    v(nb, m, cxp, cyp, czp, center);
                    v(nb, m, cxp + (basis[0].x * (float) Math.cos(t1) + basis[1].x * (float) Math.sin(t1)) * size * w1,
                            cyp + (basis[0].y * (float) Math.cos(t1) + basis[1].y * (float) Math.sin(t1)) * size * w1,
                            czp + (basis[0].z * (float) Math.cos(t1) + basis[1].z * (float) Math.sin(t1)) * size * w1, edge);
                    v(nb, m, cxp + (basis[0].x * (float) Math.cos(t2) + basis[1].x * (float) Math.sin(t2)) * size * w2,
                            cyp + (basis[0].y * (float) Math.cos(t2) + basis[1].y * (float) Math.sin(t2)) * size * w2,
                            czp + (basis[0].z * (float) Math.cos(t2) + basis[1].z * (float) Math.sin(t2)) * size * w2, edge);
                }
            }
            consumers.draw(StellaPipelines.SKY_GLOW);
        }

        // ---- aurora curtains
        if (aurora.isOn() && nightAlpha > 0.02f) {
            VertexConsumer ab = consumers.getBuffer(StellaPipelines.SKY_GLOW_Q);
            double t = (time % 600000L) / 1000.0;
            for (int band = 0; band < 3; band++) {
                int segs = 90;
                float baseEl = 0.18f + band * 0.07f;
                for (int s = 0; s < segs; s++) {
                    double az1 = -1.2 + 2.4 * s / segs, az2 = -1.2 + 2.4 * (s + 1) / segs;
                    double wave1 = Math.sin(az1 * 4 + t * 0.4 + band) * 0.08, wave2 = Math.sin(az2 * 4 + t * 0.4 + band) * 0.08;
                    float bright1 = (float) (0.5 + 0.5 * Math.sin(az1 * 7 - t * 0.9 + band * 2));
                    float bright2 = (float) (0.5 + 0.5 * Math.sin(az2 * 7 - t * 0.9 + band * 2));
                    int c1 = ColorUtil.mulAlpha(auroraColor.argb(), bright1 * nightAlpha * Math.min(1, intensity.get()));
                    int c2 = ColorUtil.mulAlpha(auroraColor.argb(), bright2 * nightAlpha * Math.min(1, intensity.get()));
                    int cTop = auroraColor.argb() & 0x00FFFFFF;
                    float[] p1 = dir(az1 + band * 0.9, baseEl + wave1), p2 = dir(az2 + band * 0.9, baseEl + wave2);
                    float[] q1 = dir(az1 + band * 0.9, baseEl + wave1 + 0.22), q2 = dir(az2 + band * 0.9, baseEl + wave2 + 0.22);
                    float rr = radius * 0.95f;
                    v(ab, m, p1[0] * rr, p1[1] * rr, p1[2] * rr, c1);
                    v(ab, m, p2[0] * rr, p2[1] * rr, p2[2] * rr, c2);
                    v(ab, m, q2[0] * rr, q2[1] * rr, q2[2] * rr, cTop);
                    v(ab, m, q1[0] * rr, q1[1] * rr, q1[2] * rr, cTop);
                }
            }
            consumers.draw(StellaPipelines.SKY_GLOW_Q);
        }

        // ---- stars
        if (stars.isOn() && nightAlpha > 0.02f) {
            VertexConsumer sb = consumers.getBuffer(StellaPipelines.SKY);
            double tw = (time % 100000L) / 1000.0;
            float rr = radius * 0.9f;
            for (float[] s : starData) {
                Vector3f d = new Vector3f(s[0], s[1], s[2]);
                Vector3f[] b = basis(d);
                float size = s[3] * radius / 400f;
                float twinkle = (float) (0.65 + 0.35 * Math.sin(tw * 2.2 + s[4]));
                int c = ColorUtil.mulAlpha(starColor.argb(), s[5] * twinkle * nightAlpha * Math.min(1, intensity.get()));
                float cx = d.x * rr, cy = d.y * rr, cz = d.z * rr;
                v(sb, m, cx + (-b[0].x - b[1].x) * size, cy + (-b[0].y - b[1].y) * size, cz + (-b[0].z - b[1].z) * size, c);
                v(sb, m, cx + (b[0].x - b[1].x) * size, cy + (b[0].y - b[1].y) * size, cz + (b[0].z - b[1].z) * size, c);
                v(sb, m, cx + (b[0].x + b[1].x) * size, cy + (b[0].y + b[1].y) * size, cz + (b[0].z + b[1].z) * size, c);
                v(sb, m, cx + (-b[0].x + b[1].x) * size, cy + (-b[0].y + b[1].y) * size, cz + (-b[0].z + b[1].z) * size, c);
            }
            consumers.draw(StellaPipelines.SKY);
        }

    }

    private static float[] dir(double azimuth, double elevation) {
        double c = Math.cos(elevation);
        return new float[]{(float) (c * Math.sin(azimuth)), (float) Math.sin(elevation), (float) (-c * Math.cos(azimuth))};
    }

    private static Vector3f[] basis(Vector3f d) {
        Vector3f up = Math.abs(d.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
        Vector3f a = new Vector3f(d).cross(up).normalize();
        Vector3f b = new Vector3f(d).cross(a).normalize();
        return new Vector3f[]{a, b};
    }

    private static void v(VertexConsumer b, Matrix4f m, float x, float y, float z, int argb) {
        b.vertex(m, x, y, z).color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF);
    }

}
