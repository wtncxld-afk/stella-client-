package dev.stella.client.modules.cosmetics;

import dev.stella.client.gui.theme.ThemeManager;
import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.module.ModuleManager;
import dev.stella.client.render.StellaPipelines;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.KeySetting;
import dev.stella.client.setting.ModeSetting;
import dev.stella.client.setting.NumberSetting;
import dev.stella.client.util.ColorUtil;
import dev.stella.client.util.Keys;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.RotationAxis;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Replaces the first-person look of swords with a knife model (Karambit, Butterfly, Bayonet or the
 * Stella Dagger) in one of several finishes, with an inspect animation on a hotkey. Purely visual and
 * client-side: other players still see your normal sword.
 */
public class KnifeSkins extends Module {
    private final ModeSetting knife = add(new ModeSetting("Knife", "Knife model", "Karambit", "Karambit", "Butterfly", "Bayonet", "Stella Dagger"));
    private final ModeSetting finish = add(new ModeSetting("Finish", "Blade finish", "Nebula", "Nebula", "Theme", "Ruby", "Emerald", "Sapphire", "Gold", "Midnight", "Rainbow"));
    private final KeySetting inspectKey = add(new KeySetting("Inspect Key", "Press to inspect your knife", GLFW.GLFW_KEY_Y));
    private final NumberSetting inspectTime = add(new NumberSetting("Inspect Time", "Length of the inspect animation", 2.2, 1.0, 4.0, 0.1).suffix("s"));
    private final BoolSetting axes = add(new BoolSetting("Axes Too", "Also show the knife when holding an axe", false));

    private long inspectStart = -1;
    private boolean keyWasDown;
    /** When >= 0, freezes the inspect animation at this progress (previews and the smoke test). */
    public static float previewT = -1f;

    public KnifeSkins() { super("Knife Skins", "First-person knife models with an inspect animation (sword slot)", Category.COSMETICS, false); }

    public void setKnife(String k) { knife.set(k); }
    public void setFinish(String f) { finish.set(f); }
    public void inspect() { inspectStart = System.currentTimeMillis(); }

    @Override
    public void onTick() {
        boolean down = mc.currentScreen == null && inspectKey.isBound() && Keys.down(inspectKey.key());
        if (down && !keyWasDown && mc.player != null && applies(mc.player.getMainHandStack())) inspect();
        keyWasDown = down;
    }

    public static KnifeSkins active(ItemStack stack) {
        KnifeSkins k = ModuleManager.get(KnifeSkins.class);
        return k != null && k.isEnabled() && k.applies(stack) ? k : null;
    }

    private boolean applies(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        String path = Registries.ITEM.getId(stack.getItem()).getPath();
        return path.endsWith("_sword") || (axes.isOn() && path.endsWith("_axe"));
    }

    /** Inspect progress 0..1, or -1 when not inspecting. */
    private float inspectProgress() {
        if (previewT >= 0) return previewT;
        if (inspectStart < 0) return -1;
        float t = (System.currentTimeMillis() - inspectStart) / (float) (inspectTime.get() * 1000);
        if (t >= 1) { inspectStart = -1; return -1; }
        return t;
    }

    /** Called from the held-item mixin instead of drawing the sword in first person. */
    public void renderFirstPerson(MatrixStack m, OrderedRenderCommandQueue queue, boolean leftHand) {
        m.push();
        float side = leftHand ? -1 : 1;
        // base pose: blade up, leaning into the screen and toward the centre like a held sword
        m.translate(0.0f * side, -0.02f, -0.02f);
        float t = inspectProgress();
        String k = knife.get();
        float butterflyOpen = 0;
        if (t >= 0) {
            float in = smooth(Math.min(1, t / 0.22f)), out = smooth(Math.max(0, (t - 0.78f) / 0.22f));
            float pose = in * (1 - out);
            // bring the knife across and tilt the blade flat towards the camera
            m.translate(-0.16f * side * pose, 0.06f * pose, 0.08f * pose);
            m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-55 * side * pose));
            m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-40 * side * pose));
            float mid = Math.max(0, Math.min(1, (t - 0.22f) / 0.56f));
            switch (k) {
                case "Karambit" -> {
                    // spin around the finger ring twice
                    m.translate(0, 0.03f, 0);
                    m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-720 * side * smooth(mid)));
                    m.translate(0, -0.03f, 0);
                }
                case "Butterfly" -> {
                    butterflyOpen = (float) Math.sin(mid * Math.PI * 3) * 0.5f + 0.5f;
                    m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(360 * smooth(mid)));
                }
                case "Bayonet" -> {
                    // flip over the blade axis and back, with a little toss
                    m.translate(0, 0.08f * (float) Math.sin(mid * Math.PI), 0);
                    m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180 * smooth(Math.min(1, mid * 2)) + 180 * smooth(Math.max(0, mid * 2 - 1))));
                }
                default -> m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(540 * smooth(mid)));
            }
        }
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-62));
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(14 * side));
        m.scale(1.05f, 1.05f, 1.05f);
        List<float[]> quads = new ArrayList<>();
        build(k, butterflyOpen, quads);
        queue.submitCustom(m, StellaPipelines.SOLID, (entry, vc) -> emit(entry, vc, quads));
        m.pop();
    }

    private static float smooth(float x) { return x * x * (3 - 2 * x); }

    // ------------------------------------------------------------------ mesh

    /** Each float[] is one quad: 4 x (x, y, z) + colour (as float bits). */
    private static void emit(MatrixStack.Entry e, VertexConsumer vc, List<float[]> quads) {
        for (float[] q : quads) {
            int c = Float.floatToRawIntBits(q[12]);
            for (int i = 0; i < 4; i++) vc.vertex(e, q[i * 3], q[i * 3 + 1], q[i * 3 + 2]).color(c);
        }
    }

    private int[] palette() {
        double t = (System.currentTimeMillis() % 5000L) / 5000.0;
        return switch (finish.get()) {
            case "Theme" -> new int[]{ThemeManager.accentAt(0), ThemeManager.accentAt(0.5), 0xFF1A1622};
            case "Ruby" -> new int[]{0xFF7A0718, 0xFFFF3B5C, 0xFF1C0A0E};
            case "Emerald" -> new int[]{0xFF04563A, 0xFF33F2A0, 0xFF0B1A14};
            case "Sapphire" -> new int[]{0xFF0A2A8C, 0xFF4FB6FF, 0xFF0B1022};
            case "Gold" -> new int[]{0xFF9C6A12, 0xFFFFE07A, 0xFF231A0C};
            case "Midnight" -> new int[]{0xFF14131A, 0xFF5C5A6E, 0xFF09090C};
            case "Rainbow" -> new int[]{ColorUtil.fromHsb((float) t, 0.8f, 1f, 255), ColorUtil.fromHsb((float) ((t + 0.35) % 1), 0.8f, 1f, 255), 0xFF141218};
            default -> new int[]{0xFF3A1A8C, 0xFFE070FF, 0xFF15101F}; // Nebula: deep violet into pink
        };
    }

    private void build(String k, float open, List<float[]> out) {
        int[] p = palette();
        int bladeA = p[0], bladeB = p[1], handle = p[2];
        int accent = ColorUtil.lerp(bladeB, 0xFFFFFFFF, 0.3);
        switch (k) {
            case "Karambit" -> {
                ring(out, 0, -0.05f, 0.045f, 0.012f, accent);                // finger ring
                box(out, -0.022f, -0.005f, -0.016f, 0.022f, 0.22f, 0.016f, handle); // grip
                box(out, -0.025f, 0.05f, -0.018f, 0.025f, 0.07f, 0.018f, accent);
                box(out, -0.025f, 0.15f, -0.018f, 0.025f, 0.17f, 0.018f, accent);
                // hooked blade: arc curving forward, tapering to a point
                int n = 14;
                for (int i = 0; i < n; i++) {
                    float a0 = i / (float) n, a1 = (i + 1) / (float) n;
                    float[] c0 = arc(a0), c1 = arc(a1);
                    float w0 = 0.048f * (1 - a0 * 0.92f), w1 = 0.048f * (1 - a1 * 0.92f);
                    slab(out, c0[0] - w0 * c0[2], c0[1] + w0 * c0[3], c0[0], c0[1], c1[0], c1[1], c1[0] - w1 * c1[2], c1[1] + w1 * c1[3],
                            0.007f, ColorUtil.lerp(bladeA, bladeB, (a0 + a1) / 2), ColorUtil.lerp(0xFFE8E8F0, bladeB, 0.3));
                }
            }
            case "Butterfly" -> {
                // blade
                prismBlade(out, 0.24f, 0.62f, 0.032f, 0.008f, bladeA, bladeB, true);
                // two handle halves hinged at the base of the blade; "open" swings them apart
                float ang = open * 180f;
                handleHalf(out, -1, ang, handle, accent);
                handleHalf(out, 1, ang, handle, accent);
            }
            case "Bayonet" -> {
                box(out, -0.02f, 0f, -0.02f, 0.02f, 0.24f, 0.02f, handle);
                for (int i = 0; i < 5; i++) box(out, -0.022f, 0.03f + i * 0.042f, -0.022f, 0.022f, 0.04f + i * 0.042f, 0.022f, ColorUtil.lerp(handle, 0xFF3A3A44, 0.6));
                box(out, -0.055f, 0.24f, -0.025f, 0.055f, 0.265f, 0.025f, accent);  // guard
                box(out, -0.026f, -0.03f, -0.026f, 0.026f, 0f, 0.026f, accent);     // pommel
                prismBlade(out, 0.265f, 0.78f, 0.036f, 0.009f, bladeA, bladeB, false);
                box(out, -0.004f, 0.3f, -0.0095f, 0.004f, 0.62f, 0.0095f, ColorUtil.lerp(bladeA, 0xFF000000, 0.35)); // fuller
            }
            default -> { // Stella Dagger
                box(out, -0.02f, 0f, -0.02f, 0.02f, 0.2f, 0.02f, handle);
                star(out, 0, 0.215f, 0.085f, 0.035f, 0.02f, accent, bladeB);          // star crossguard
                gem(out, 0, -0.03f, 0.03f, bladeB);                                   // pommel gem
                prismBlade(out, 0.23f, 0.7f, 0.04f, 0.01f, bladeA, bladeB, false);
            }
        }
    }

    /** Karambit blade centre line: returns x, y and the outward normal (nx, ny). */
    private static float[] arc(float t) {
        double a = Math.toRadians(-10 + 95 * t);
        float cx = 0.16f, cy = 0.22f, rr = 0.16f;
        float x = (float) (cx - Math.cos(a) * rr), y = (float) (cy + Math.sin(a) * rr);
        float nx = (float) -Math.cos(a), ny = (float) Math.sin(a);
        return new float[]{x, y, nx, -ny};
    }

    private void handleHalf(List<float[]> out, int side, float ang, int handle, int accent) {
        // half of the grip, hinged at the base of the blade (y=0.24)
        float hx0 = side < 0 ? -0.024f : 0.004f, hx1 = side < 0 ? -0.004f : 0.024f;
        double r = Math.toRadians(ang * side);
        List<float[]> tmp = new ArrayList<>();
        box(tmp, hx0, 0f, -0.016f, hx1, 0.24f, 0.016f, handle);
        box(tmp, hx0 - 0.001f, 0.02f, -0.017f, hx1 + 0.001f, 0.05f, 0.017f, accent);
        for (float[] q : tmp) {
            for (int i = 0; i < 4; i++) {
                // swing in the blade plane around the pivot at the top of the grip (z axis)
                float x = q[i * 3], y = q[i * 3 + 1] - 0.24f;
                q[i * 3] = (float) (x * Math.cos(r) - y * Math.sin(r));
                q[i * 3 + 1] = (float) (x * Math.sin(r) + y * Math.cos(r)) + 0.24f;
                q[i * 3 + 2] += side * 0.004f;
            }
            out.add(q);
        }
    }

    // ------------------------------------------------------------------ primitives

    private static int shade(int c, float f) {
        int r = (int) (((c >> 16) & 0xFF) * f), g = (int) (((c >> 8) & 0xFF) * f), b = (int) ((c & 0xFF) * f);
        return 0xFF000000 | (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
    }

    private static void quad(List<float[]> out, float[] a, float[] b, float[] c, float[] d, int col) {
        out.add(new float[]{a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2], d[0], d[1], d[2], Float.intBitsToFloat(col)});
    }

    private static void box(List<float[]> out, float x0, float y0, float z0, float x1, float y1, float z1, int c) {
        float[] a = {x0, y0, z0}, b = {x1, y0, z0}, cc = {x1, y1, z0}, d = {x0, y1, z0};
        float[] e = {x0, y0, z1}, f = {x1, y0, z1}, g = {x1, y1, z1}, h = {x0, y1, z1};
        quad(out, a, b, cc, d, shade(c, 0.78f)); // back
        quad(out, e, f, g, h, shade(c, 1.0f));   // front
        quad(out, a, e, h, d, shade(c, 0.66f));  // left
        quad(out, b, f, g, cc, shade(c, 0.86f)); // right
        quad(out, d, cc, g, h, shade(c, 1.1f));  // top
        quad(out, a, b, f, e, shade(c, 0.5f));   // bottom
    }

    /** Flat blade section (two faces + edges) from a 2D quad outline, thickness 2*th. */
    private static void slab(List<float[]> out, float ax, float ay, float bx, float by, float cx, float cy, float dx, float dy, float th, int face, int edge) {
        quad(out, new float[]{ax, ay, th}, new float[]{bx, by, th * 0.2f}, new float[]{cx, cy, th * 0.2f}, new float[]{dx, dy, th}, shade(face, 1.05f));
        quad(out, new float[]{ax, ay, -th}, new float[]{bx, by, -th * 0.2f}, new float[]{cx, cy, -th * 0.2f}, new float[]{dx, dy, -th}, shade(face, 0.8f));
        quad(out, new float[]{bx, by, th * 0.2f}, new float[]{bx, by, -th * 0.2f}, new float[]{cx, cy, -th * 0.2f}, new float[]{cx, cy, th * 0.2f}, edge); // sharp edge
        quad(out, new float[]{ax, ay, th}, new float[]{ax, ay, -th}, new float[]{dx, dy, -th}, new float[]{dx, dy, th}, shade(face, 0.6f));    // spine
    }

    /** Straight blade from y0 to y1 (half width w, half thickness th) with a point; gradient along its length. */
    private static void prismBlade(List<float[]> out, float y0, float y1, float w, float th, int ca, int cb, boolean clip) {
        int n = 8;
        float tipStart = y1 - (y1 - y0) * 0.22f;
        for (int i = 0; i < n; i++) {
            float t0 = i / (float) n, t1 = (i + 1) / (float) n;
            float ya = y0 + (tipStart - y0) * t0, yb = y0 + (tipStart - y0) * t1;
            int col = ColorUtil.lerp(ca, cb, (t0 + t1) / 2 * 0.8f);
            slab(out, -w, ya, w, ya, w, yb, -w, yb, th, col, ColorUtil.lerp(0xFFF2F2FA, cb, 0.25));
        }
        // point: spine side goes straight (or clipped), edge side sweeps to the tip
        float tipX = clip ? -w * 0.6f : 0;
        slab(out, -w, tipStart, w, tipStart, tipX + 0.001f, y1, clip ? -w * 0.9f : -0.001f, clip ? y1 - (y1 - tipStart) * 0.35f : y1, th, cb, ColorUtil.lerp(0xFFFFFFFF, cb, 0.2));
    }

    private static void ring(List<float[]> out, float cx, float cy, float r, float tube, int c) {
        int n = 12;
        for (int i = 0; i < n; i++) {
            double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
            float x0 = cx + (float) Math.cos(a0) * r, y0 = cy + (float) Math.sin(a0) * r, x1 = cx + (float) Math.cos(a1) * r, y1 = cy + (float) Math.sin(a1) * r;
            float ox0 = cx + (float) Math.cos(a0) * (r + tube), oy0 = cy + (float) Math.sin(a0) * (r + tube), ox1 = cx + (float) Math.cos(a1) * (r + tube), oy1 = cy + (float) Math.sin(a1) * (r + tube);
            quad(out, new float[]{x0, y0, tube}, new float[]{ox0, oy0, tube}, new float[]{ox1, oy1, tube}, new float[]{x1, y1, tube}, shade(c, 1f));
            quad(out, new float[]{x0, y0, -tube}, new float[]{ox0, oy0, -tube}, new float[]{ox1, oy1, -tube}, new float[]{x1, y1, -tube}, shade(c, 0.75f));
            quad(out, new float[]{ox0, oy0, tube}, new float[]{ox0, oy0, -tube}, new float[]{ox1, oy1, -tube}, new float[]{ox1, oy1, tube}, shade(c, 0.9f));
            quad(out, new float[]{x0, y0, tube}, new float[]{x0, y0, -tube}, new float[]{x1, y1, -tube}, new float[]{x1, y1, tube}, shade(c, 0.6f));
        }
    }

    /** Five-point star prism in the XY plane (the Stella logo) used as a crossguard. */
    private static void star(List<float[]> out, float cx, float cy, float outer, float inner, float th, int c, int glow) {
        for (int i = 0; i < 10; i++) {
            double a0 = i * Math.PI / 5 + Math.PI / 2, a1 = (i + 1) * Math.PI / 5 + Math.PI / 2;
            float r0 = i % 2 == 0 ? outer : inner, r1 = i % 2 == 0 ? inner : outer;
            float x0 = cx + (float) Math.cos(a0) * r0, y0 = cy + (float) Math.sin(a0) * r0 * 0.6f, x1 = cx + (float) Math.cos(a1) * r1, y1 = cy + (float) Math.sin(a1) * r1 * 0.6f;
            int col = ColorUtil.lerp(c, glow, i / 10f);
            quad(out, new float[]{cx, cy, th}, new float[]{x0, y0, th}, new float[]{x1, y1, th}, new float[]{x1, y1, th}, shade(col, 1.1f));
            quad(out, new float[]{cx, cy, -th}, new float[]{x0, y0, -th}, new float[]{x1, y1, -th}, new float[]{x1, y1, -th}, shade(col, 0.8f));
            quad(out, new float[]{x0, y0, th}, new float[]{x0, y0, -th}, new float[]{x1, y1, -th}, new float[]{x1, y1, th}, shade(col, 0.7f));
        }
    }

    private static void gem(List<float[]> out, float cx, float cy, float r, int c) {
        float[] top = {cx, cy - r, 0}, bot = {cx, cy + r * 0.3f, 0};
        for (int i = 0; i < 6; i++) {
            double a0 = i * Math.PI / 3, a1 = (i + 1) * Math.PI / 3;
            float[] p0 = {cx + (float) Math.cos(a0) * r, cy, (float) Math.sin(a0) * r}, p1 = {cx + (float) Math.cos(a1) * r, cy, (float) Math.sin(a1) * r};
            quad(out, top, p0, p1, p1, shade(c, 0.7f + 0.08f * i));
            quad(out, bot, p0, p1, p1, shade(c, 1.15f - 0.05f * i));
        }
    }
}
