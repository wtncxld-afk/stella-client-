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
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.RotationAxis;
import org.lwjgl.glfw.GLFW;

/**
 * First-person knife models for swords: Karambit, Butterfly, Bayonet or the Stella Dagger, with
 * patterned finishes, a ground edge, moving shine, glowing edges, enchantment shimmer, a draw
 * flourish when you switch to it and an inspect animation on a hotkey. Visual and client-side only:
 * other players still see your sword.
 */
public class KnifeSkins extends Module {
    private final ModeSetting knife = add(new ModeSetting("Knife", "Knife model", "Karambit", "Karambit", "Butterfly", "Bayonet", "Stella Dagger", "Kunai", "Tanto"));
    private final ModeSetting finish = add(new ModeSetting("Finish", "Blade finish", "Nebula",
            "Nebula", "Galaxy", "Chrome", "Damascus", "Sunset", "Ember", "Prism", "Ruby", "Sapphire", "Emerald", "Gold", "Midnight", "Theme"));
    private final NumberSetting wear = add(new NumberSetting("Wear", "0 = factory new, 1 = battle-scarred (scratches and worn patches)", 0.0, 0.0, 1.0, 0.01));
    private final NumberSetting seed = add(new NumberSetting("Pattern Seed", "Shifts the pattern so every knife looks a little different", 0, 0, 999, 1));
    private final BoolSetting showHand = add(new BoolSetting("Show Hand", "Draw your arm holding the knife", true));
    private final BoolSetting knifeSounds = add(new BoolSetting("Sounds", "Draw, flip and spin sounds", true));
    private final KeySetting inspectKey = add(new KeySetting("Inspect Key", "Press to inspect your knife", GLFW.GLFW_KEY_Y));
    private final NumberSetting inspectTime = add(new NumberSetting("Inspect Time", "Length of the inspect animation", 2.8, 1.5, 5.0, 0.1).suffix("s"));
    private final BoolSetting drawAnim = add(new BoolSetting("Draw Animation", "Flourish when you switch to the knife", true));
    private final BoolSetting edgeGlow = add(new BoolSetting("Edge Glow", "Neon glow along the cutting edge", true));
    private final BoolSetting sparkles = add(new BoolSetting("Sparkles", "Twinkling sparkles on the blade", true));
    private final BoolSetting sway = add(new BoolSetting("Idle Sway", "Gentle breathing movement", true));
    private final BoolSetting axes = add(new BoolSetting("Axes Too", "Also show the knife when holding an axe", false));
    private final BoolSetting maces = add(new BoolSetting("Maces Too", "Also show the knife when holding a mace", false));
    private final dev.stella.client.setting.TextSetting perSlot = add(new dev.stella.client.setting.TextSetting("Per Slot Knives",
            "Different knife per hotbar slot, e.g. 1=Karambit, 2=Bayonet, 3=Butterfly, 4=Stella Dagger", "", 200));

    private long inspectStart = -1, drawStart = -1;
    private boolean keyWasDown;
    private ItemStack lastStack = ItemStack.EMPTY;
    /** When >= 0, freezes the inspect animation at this progress (previews and the smoke test). */
    public static float previewT = -1f;

    public KnifeSkins() { super("Knife Skins", "First-person knife models with finishes, draw and inspect animations (sword slot)", Category.COSMETICS, false); }

    public void setKnife(String k) { knife.set(k); }
    public void setFinish(String f) { finish.set(f); }
    public boolean showHand() { return showHand.isOn(); }
    public void setWear(double w) { wear.set(w); }

    private float lastSoundT = -1;

    private void sound(String name, float pitch) {
        if (!knifeSounds.isOn()) return;
        mc.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.ui(
                net.minecraft.sound.SoundEvent.of(net.minecraft.util.Identifier.of(dev.stella.client.StellaClient.MOD_ID, name)), pitch, 0.7f));
    }
    public void inspect() { inspectStart = System.currentTimeMillis(); }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        ItemStack held = mc.player.getMainHandStack();
        boolean down = mc.currentScreen == null && inspectKey.isBound() && Keys.down(inspectKey.key());
        if (down && !keyWasDown && applies(held)) inspect();
        keyWasDown = down;
        if (applies(held) && !ItemStack.areItemsEqual(held, lastStack) && drawAnim.isOn()) { drawStart = System.currentTimeMillis(); inspectStart = -1; sound("knife_draw", 1.0f); }
        lastStack = held.copy();
    }

    public static KnifeSkins active(ItemStack stack) {
        KnifeSkins k = ModuleManager.get(KnifeSkins.class);
        return k != null && k.isEnabled() && k.applies(stack) ? k : null;
    }

    private boolean applies(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        String path = Registries.ITEM.getId(stack.getItem()).getPath();
        return path.endsWith("_sword") || (axes.isOn() && path.endsWith("_axe")) || (maces.isOn() && path.equals("mace"));
    }

    private float inspectProgress() {
        if (previewT >= 0) return previewT;
        if (inspectStart < 0) return -1;
        float t = (System.currentTimeMillis() - inspectStart) / (float) (inspectTime.get() * 1000);
        if (t >= 1) { inspectStart = -1; return -1; }
        return t;
    }

    private float drawProgress() {
        if (drawStart < 0 || previewT >= 0) return 1;
        float t = (System.currentTimeMillis() - drawStart) / 550f;
        if (t >= 1) { drawStart = -1; return 1; }
        return t;
    }

    // ------------------------------------------------------------------ animation

    /** Called from the held-item mixin instead of drawing the sword in first person. */
    public void renderFirstPerson(MatrixStack m, OrderedRenderCommandQueue queue, boolean leftHand, int light, ItemStack stack) {
        m.push();
        float side = leftHand ? -1 : 1;
        double now = System.currentTimeMillis() / 1000.0;
        float t = inspectProgress(), d = drawProgress();
        String k = slotKnife();
        float open = 0;

        // inspect choreography: raise (0-.14), show face (.14-.36), roll to show the back (.36-.5),
        // trick (.5-.84), return (.84-1)
        float pose = 0, roll = 0, trick = 0;
        if (previewT < 0) {
            // sounds at the roll and the trick
            if (t >= 0.36f && lastSoundT < 0.36f) sound("knife_flip", 1.3f);
            if (t >= 0.5f && lastSoundT < 0.5f) sound(k.equals("Butterfly") ? "knife_flip" : "knife_spin", k.equals("Karambit") ? 1.2f : 1.0f);
            if (k.equals("Butterfly") && t >= 0.67f && lastSoundT < 0.67f) sound("knife_flip", 0.9f);
            lastSoundT = t;
        }
        if (t >= 0) {
            pose = smooth(clamp(t / 0.14f)) * (1 - smooth(clamp((t - 0.84f) / 0.16f)));
            roll = smooth(clamp((t - 0.36f) / 0.14f)) * (1 - smooth(clamp((t - 0.84f) / 0.16f)));
            trick = clamp((t - 0.5f) / 0.34f);
        }
        float breathe = sway.isOn() && t < 0 ? (float) Math.sin(now * 1.7) : 0;

        // draw flourish: comes up from below while flipping once around the blade axis
        float dIn = 1 - smooth(d);
        m.translate(0.04f * side * dIn, -0.32f * dIn, 0.05f * dIn);

        float tilt = t >= 0 ? (float) Math.sin(clamp((t - 0.14f) / 0.22f) * Math.PI) : 0;
        m.translate(lerp(0.0f, -0.17f, pose) * side, lerp(0.13f, 0.21f, pose) + breathe * 0.006f, lerp(-0.06f, -0.04f, pose));
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(lerp(-68, -14, pose) + breathe * 0.8f));
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((lerp(16, 64, pose) + 20 * tilt) * side));
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((lerp(0, -16, pose) + 180 * roll) * side - 360 * dIn * side));
        float sc = lerp(1.28f, 1.12f, pose);
        m.scale(sc, sc, sc);
        if (t >= 0 && trick > 0) {
            float sm = smooth(trick);
            switch (k) {
                case "Karambit" -> { // twirl twice around the finger ring
                    m.translate(0, -0.06f, 0);
                    m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-720 * side * sm));
                    m.translate(0, 0.06f, 0);
                }
                case "Butterfly" -> { // handles flip open and shut twice
                    open = (float) Math.abs(Math.sin(trick * Math.PI * 2));
                    m.translate(0, 0.24f, 0);
                    m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180 * sm));
                    m.translate(0, -0.24f, 0);
                }
                case "Bayonet" -> { // toss it up, full flip, catch
                    float arc = (float) Math.sin(trick * Math.PI);
                    m.translate(0, 0.12f * arc, 0.02f * arc);
                    m.translate(0, 0.3f, 0);
                    m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(360 * sm));
                    m.translate(0, -0.3f, 0);
                }
                default -> { // spin around the blade
                    m.translate(0, 0.3f, 0);
                    m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(540 * sm));
                    m.translate(0, -0.3f, 0);
                }
            }
        }

        KnifeMesh mesh = new KnifeMesh();
        int block = (light & 0xFFFF) >> 4, sky = (light >> 20) & 0xF;
        mesh.light = 0.42f + 0.58f * Math.max(block, sky) / 15f;
        float shine = (float) ((now * 0.45) % 1.6) - 0.3f;
        build(mesh, k, open, shine, now);
        if (stack != null && stack.hasGlint()) glint(mesh, now);
        queue.submitCustom(m, StellaPipelines.SOLID, mesh::emitSolid);
        queue.submitCustom(m, StellaPipelines.SKY_GLOW_Q, mesh::emitGlow);
        m.pop();
    }

    /** Knife for the selected hotbar slot (Per Slot Knives), else the main Knife setting. */
    private String slotKnife() {
        if (mc.player != null && !perSlot.get().isBlank()) {
            String slot = String.valueOf(mc.player.getInventory().getSelectedSlot() + 1);
            for (String e : perSlot.get().split(",")) {
                int eq = e.indexOf('=');
                if (eq <= 0 || !e.substring(0, eq).trim().equals(slot)) continue;
                String want = e.substring(eq + 1).trim().toLowerCase(java.util.Locale.ROOT);
                for (String option : new String[]{"Karambit", "Butterfly", "Bayonet", "Stella Dagger", "Kunai", "Tanto"})
                    if (option.toLowerCase(java.util.Locale.ROOT).startsWith(want) || want.startsWith(option.toLowerCase(java.util.Locale.ROOT).split(" ")[0])) return option;
            }
        }
        return knife.get();
    }

    private static float smooth(float x) { return x * x * (3 - 2 * x); }
    private static float clamp(float x) { return Math.max(0, Math.min(1, x)); }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    // ------------------------------------------------------------------ finishes

    private record Palette(int a, int b, int handle, int accent, int glow) {}

    private Palette palette() {
        return switch (finish.get()) {
            case "Galaxy" -> new Palette(0xFF05040E, 0xFF2B1466, 0xFF0B0A14, 0xFFB9A0FF, 0xFF6E8BFF);
            case "Chrome" -> new Palette(0xFF4A5060, 0xFFFFFFFF, 0xFF15161C, 0xFFE8ECF4, 0xFFBFE3FF);
            case "Damascus" -> new Palette(0xFF4A4D57, 0xFFC9CDD8, 0xFF2B1D14, 0xFFB8BCC8, 0xFF9FD3FF);
            case "Sunset" -> new Palette(0xFFFF4FA3, 0xFFFFD36B, 0xFF1F1420, 0xFFFFB3D9, 0xFFFF8FC8);
            case "Ember" -> new Palette(0xFFFF7A1A, 0xFF1A0E08, 0xFF16100C, 0xFFFFB066, 0xFFFF6A1A);
            case "Prism" -> new Palette(0xFF6AE3FF, 0xFFFF7BE5, 0xFF141320, 0xFFE8F6FF, 0xFFB6F0FF);
            case "Ruby" -> new Palette(0xFF5A0414, 0xFFFF2E57, 0xFF1C0A0E, 0xFFFF8FA5, 0xFFFF3B5C);
            case "Sapphire" -> new Palette(0xFF061E6E, 0xFF3FA9FF, 0xFF0B1022, 0xFF9FD6FF, 0xFF4FB6FF);
            case "Emerald" -> new Palette(0xFF033D29, 0xFF2EF29A, 0xFF0B1A14, 0xFF9DFFD2, 0xFF33F2A0);
            case "Gold" -> new Palette(0xFF8A5A0C, 0xFFFFE27A, 0xFF231A0C, 0xFFFFF0B3, 0xFFFFD45A);
            case "Midnight" -> new Palette(0xFF0E0D14, 0xFF4B4860, 0xFF09090C, 0xFF8C88A8, 0xFF7C6CFF);
            case "Theme" -> new Palette(ColorUtil.lerp(ThemeManager.get().accent(), 0xFF000000, 0.45), ThemeManager.accentAt(0.5), 0xFF15121C,
                    ColorUtil.lerp(ThemeManager.get().accent(), 0xFFFFFFFF, 0.5), ThemeManager.get().accent());
            default -> new Palette(0xFF26105E, 0xFFE070FF, 0xFF15101F, 0xFFD9B8FF, 0xFFC27CFF); // Nebula
        };
    }

    /** Blade pattern for the chosen finish (across 0 = spine .. 1 = edge, along 0 = base .. 1 = tip). */
    private KnifeMesh.Finish pattern(Palette p, double now) {
        KnifeMesh.Finish base = basePattern(p, now, seed.get() * 0.61);
        double w = wear.get();
        if (w <= 0.001) return base;
        return (u, s, side) -> {
            int c = base.at(u, s, side);
            // worn patches and scratches: deterministic noise over the blade
            double n = 0.5 + 0.25 * Math.sin(u * 23 + s * 71 + seed.get()) + 0.25 * Math.sin(u * 57 - s * 39 + 2.1 + seed.get() * 0.3);
            double scratch = Math.abs(Math.sin(s * 140 + u * 9 + seed.get())) > 0.985 ? 1 : 0;
            if (n < w * 0.75) c = ColorUtil.lerp(c, 0xFF6E7078, 0.55 + 0.3 * w);   // paint worn off to steel
            if (scratch > 0 && w > 0.2) c = ColorUtil.lerp(c, 0xFFD8DCE4, 0.6);
            return ColorUtil.lerp(c, 0xFF2A2A30, w * 0.18);
        };
    }

    private KnifeMesh.Finish basePattern(Palette p, double now, double ph) {
        return switch (finish.get()) {
            case "Galaxy" -> (u, s, side) -> {
                double neb = 0.5 + 0.5 * Math.sin(s * 9 + ph + Math.sin(u * 5 + s * 3) * 2);
                double neb2 = 0.5 + 0.5 * Math.sin(s * 17 - u * 7 + ph * 2);
                int c = ColorUtil.lerp(0xFF05040E, 0xFF2B1466, neb * 0.8);
                return ColorUtil.lerp(c, 0xFF1F6BFF, neb2 * neb * 0.45);
            };
            case "Chrome" -> (u, s, side) -> {
                // mirror-like: sharp bright and dark reflection bands that drift with time
                double b = Math.sin((s * 6 - u * 2.2) + now * 0.8 + ph);
                double hl = Math.pow(Math.max(0, b), 6);
                int c = ColorUtil.lerp(0xFF4A5060, 0xFFC8CED8, 0.5 + 0.5 * Math.sin(s * 3 + u + ph));
                return ColorUtil.lerp(c, 0xFFFFFFFF, hl * 0.9);
            };
            case "Damascus" -> (u, s, side) -> {
                double w = Math.sin(s * 46 + ph + Math.sin(u * 7 + s * 9 + ph) * 2.2);
                return ColorUtil.lerp(p.a(), p.b(), 0.5 + 0.5 * w);
            };
            case "Ember" -> (u, s, side) -> {
                double stripe = Math.sin(s * 30 + ph + Math.sin(u * 5 + ph) * 1.8);
                return stripe > 0.45 ? 0xFF1A0E08 : ColorUtil.lerp(0xFFFF4A0A, 0xFFFFB347, u * 0.7 + s * 0.3);
            };
            case "Sunset" -> (u, s, side) -> {
                int c = s < 0.5 ? ColorUtil.lerp(0xFF7B3BFF, p.a(), s * 2) : ColorUtil.lerp(p.a(), p.b(), (s - 0.5) * 2);
                return ColorUtil.lerp(c, 0xFFFFFFFF, u * 0.15);
            };
            case "Prism" -> (u, s, side) -> ColorUtil.fromHsb((float) ((s * 0.8 + u * 0.3 + now * 0.08 + (side > 0 ? 0 : 0.5)) % 1.0), 0.55f, 1f, 255);
            case "Ruby", "Sapphire", "Emerald" -> (u, s, side) -> {
                double n = Math.sin(s * 23 + u * 5 + ph) * Math.sin(s * 9 - u * 11 + 1.3 + ph) + 0.4 * Math.sin(s * 41 + u * 17 + ph);
                return ColorUtil.lerp(p.a(), p.b(), Math.max(0, Math.min(1, 0.45 + n * 0.55)));
            };
            case "Nebula" -> (u, s, side) -> {
                double cloud = 0.5 + 0.5 * Math.sin(s * 13 + ph + Math.sin(u * 6 + s * 4 + ph) * 2.5);
                int c = ColorUtil.lerp(p.a(), 0xFF3A6BFF, s * 0.5);
                return ColorUtil.lerp(c, p.b(), cloud * 0.75);
            };
            default -> (u, s, side) -> ColorUtil.lerp(p.a(), p.b(), 0.25 + s * 0.6 + u * 0.15);
        };
    }

    // ------------------------------------------------------------------ models

    private void build(KnifeMesh mesh, String k, float open, float shine, double now) {
        Palette p = palette();
        KnifeMesh.Finish f = pattern(p, now);
        int glow = edgeGlow.isOn() ? p.glow() : 0;
        switch (k) {
            case "Karambit" -> karambit(mesh, p, f, shine, glow, now);
            case "Butterfly" -> butterfly(mesh, p, f, shine, glow, open, now);
            case "Bayonet" -> bayonet(mesh, p, f, shine, glow, now);
            case "Kunai" -> kunai(mesh, p, f, shine, glow, now);
            case "Tanto" -> tanto(mesh, p, f, shine, glow, now);
            default -> dagger(mesh, p, f, shine, glow, now);
        }
    }

    private void karambit(KnifeMesh m, Palette p, KnifeMesh.Finish f, float shine, int glow, double now) {
        m.ring(0, -0.06f, 0.042f, 0.014f, ColorUtil.lerp(0xFF9EA3B0, p.accent(), 0.3), ColorUtil.lerp(0xFFE9ECF3, p.accent(), 0.3));
        m.metal(-0.026f, -0.022f, -0.019f, 0.026f, 0.005f, 0.019f, p.accent());
        // slightly curved grip with alternating wrap bands
        for (int i = 0; i < 9; i++) {
            float y0 = 0.005f + i * 0.024f, y1 = y0 + 0.024f, cx = 0.004f * i;
            boolean band = i % 2 == 0;
            float w = band ? 0.025f : 0.023f;
            m.box(cx - w, y0, -0.018f, cx + w, y1, 0.018f, band ? ColorUtil.lerp(p.handle(), p.accent(), 0.18f) : p.handle());
        }
        for (float y : new float[]{0.05f, 0.17f}) { // screws
            float cx = 0.004f * (y / 0.024f);
            m.metal(cx - 0.006f, y - 0.006f, 0.0185f, cx + 0.006f, y + 0.006f, 0.0215f, 0xFFFFFFFF);
            m.metal(cx - 0.006f, y - 0.006f, -0.0215f, cx + 0.006f, y + 0.006f, -0.0185f, 0xFFFFFFFF);
        }
        m.metal(0.012f, 0.215f, -0.021f, 0.06f, 0.235f, 0.021f, p.accent()); // guard
        // hooked blade: the sharp edge is on the inside of the curve
        int n = 22;
        float[][] sp = new float[n][], ed = new float[n][];
        for (int i = 0; i < n; i++) {
            float s = i / (float) (n - 1);
            double a = Math.toRadians(-8 + 100 * s);
            float cx = 0.2f, cy = 0.22f, r = 0.17f;
            float x = (float) (cx - Math.cos(a) * r), y = (float) (cy + Math.sin(a) * r);
            float nx = (float) Math.cos(a), ny = (float) -Math.sin(a);
            float w = 0.056f * (1 - (float) Math.pow(s, 1.25) * 0.97f);
            sp[i] = new float[]{x - nx * w * 0.5f, y - ny * w * 0.5f};
            ed[i] = new float[]{x + nx * w * 0.5f, y + ny * w * 0.5f};
        }
        m.blade(sp, ed, 0.0085f, false, f, shine, glow);
        if (sparkles.isOn()) bladeSparkles(m, sp, ed, p.glow(), now, 5);
    }

    private void butterfly(KnifeMesh m, Palette p, KnifeMesh.Finish f, float shine, int glow, float open, double now) {
        int n = 16;
        float y0 = 0.25f, y1 = 0.66f;
        float[][] sp = new float[n][], ed = new float[n][];
        for (int i = 0; i < n; i++) {
            float s = i / (float) (n - 1), y = y0 + (y1 - y0) * s;
            float spineX = s < 0.68f ? -0.03f : -0.03f + (s - 0.68f) / 0.32f * 0.03f; // clip point
            float edgeX = s < 0.75f ? 0.032f : 0.032f * (1 - (float) Math.pow((s - 0.75f) / 0.25f, 1.4f));
            float spineY = s < 0.68f ? y : y + (s - 0.68f) * 0.04f;
            sp[i] = new float[]{spineX, spineY};
            ed[i] = new float[]{Math.max(edgeX, spineX + 0.0015f), y};
        }
        m.blade(sp, ed, 0.0075f, false, f, shine, glow);
        m.metal(-0.034f, 0.235f, -0.012f, 0.034f, 0.255f, 0.012f, p.accent()); // tang and pivot pins
        float ang = open * 180f;
        handleHalf(m, -1, ang, p);
        handleHalf(m, 1, ang, p);
        if (sparkles.isOn()) bladeSparkles(m, sp, ed, p.glow(), now, 4);
    }

    private void handleHalf(KnifeMesh dst, int side, float ang, Palette p) {
        KnifeMesh tmp = new KnifeMesh();
        tmp.light = dst.light;
        float x0 = side < 0 ? -0.028f : 0.002f, x1 = side < 0 ? -0.002f : 0.028f, zo = side * 0.0045f;
        // frame rails and end caps, darker inset with cut-out windows
        tmp.metal(x0, 0f, -0.016f + zo, x1, 0.012f, 0.016f + zo, p.accent());
        tmp.metal(x0, 0.226f, -0.016f + zo, x1, 0.238f, 0.016f + zo, p.accent());
        int rail = ColorUtil.lerp(0xFF4A4E5A, p.accent(), 0.3);
        tmp.box(x0, 0.012f, -0.016f + zo, x0 + 0.006f, 0.226f, 0.016f + zo, rail);
        tmp.box(x1 - 0.006f, 0.012f, -0.016f + zo, x1, 0.226f, 0.016f + zo, rail);
        for (int i = 0; i < 6; i++) {
            float yy = 0.016f + i * 0.035f;
            tmp.box(x0 + 0.006f, yy, -0.012f + zo, x1 - 0.006f, yy + 0.022f, 0.012f + zo, i % 2 == 0 ? p.handle() : ColorUtil.lerp(p.handle(), p.glow(), 0.35));
        }
        if (side > 0) tmp.metal(x0 - 0.004f, -0.018f, -0.006f + zo, x1, 0.002f, 0.006f + zo, 0xFFFFFFFF); // latch
        double r = Math.toRadians(ang * side);
        for (KnifeMesh.Quad q : tmp.solid) {
            for (int i = 0; i < 4; i++) {
                float x = q.p()[i * 3], y = q.p()[i * 3 + 1] - 0.245f;
                q.p()[i * 3] = (float) (x * Math.cos(r) - y * Math.sin(r));
                q.p()[i * 3 + 1] = (float) (x * Math.sin(r) + y * Math.cos(r)) + 0.245f;
            }
            dst.solid.add(q);
        }
    }

    private void bayonet(KnifeMesh m, Palette p, KnifeMesh.Finish f, float shine, int glow, double now) {
        // pommel with release button, ribbed grip, crossguard with muzzle ring
        m.metal(-0.027f, -0.034f, -0.025f, 0.027f, 0.0f, 0.025f, p.accent());
        m.metal(-0.03f, -0.02f, 0.025f, -0.012f, -0.008f, 0.03f, 0xFFFFFFFF);
        for (int i = 0; i < 9; i++) {
            float y0 = i * 0.027f;
            m.box(-0.024f, y0, -0.022f, 0.024f, y0 + 0.016f, 0.022f, p.handle());
            m.box(-0.021f, y0 + 0.016f, -0.019f, 0.021f, y0 + 0.027f, 0.019f, ColorUtil.lerp(p.handle(), 0xFF000000, 0.35));
        }
        m.metal(-0.06f, 0.243f, -0.026f, 0.06f, 0.268f, 0.026f, p.accent());
        m.ring(-0.072f, 0.255f, 0.018f, 0.008f, ColorUtil.lerp(0xFF9EA3B0, p.accent(), 0.3), ColorUtil.lerp(0xFFE9ECF3, p.accent(), 0.3));
        int n = 20;
        float y0 = 0.268f, y1 = 0.8f;
        float[][] sp = new float[n][], ed = new float[n][];
        for (int i = 0; i < n; i++) {
            float s = i / (float) (n - 1), y = y0 + (y1 - y0) * s;
            float spineX = s < 0.8f ? -0.036f : -0.036f + (s - 0.8f) / 0.2f * 0.034f;
            float edgeX = s < 0.72f ? 0.04f : 0.04f * (1 - (float) Math.pow((s - 0.72f) / 0.28f, 1.6f));
            sp[i] = new float[]{spineX, y};
            ed[i] = new float[]{Math.max(edgeX, spineX + 0.0015f), y + (s > 0.72f ? (s - 0.72f) * 0.03f : 0)};
        }
        m.blade(sp, ed, 0.0095f, false, f, shine, glow);
        // fuller groove and saw-back teeth on the spine
        m.box(-0.026f, 0.3f, -0.0105f, -0.016f, 0.62f, 0.0105f, ColorUtil.lerp(p.a(), 0xFF000000, 0.45));
        for (int i = 0; i < 9; i++) {
            float y = 0.3f + i * 0.026f;
            m.quad(KnifeMesh.v(-0.036f, y, 0.006f), KnifeMesh.v(-0.048f, y + 0.013f, 0f), KnifeMesh.v(-0.036f, y + 0.026f, 0.006f), KnifeMesh.v(-0.036f, y + 0.026f, 0.006f), m.lit(0xFFD8DCE6, 1f));
            m.quad(KnifeMesh.v(-0.036f, y, -0.006f), KnifeMesh.v(-0.048f, y + 0.013f, 0f), KnifeMesh.v(-0.036f, y + 0.026f, -0.006f), KnifeMesh.v(-0.036f, y + 0.026f, -0.006f), m.lit(0xFFD8DCE6, 0.7f));
        }
        if (sparkles.isOn()) bladeSparkles(m, sp, ed, p.glow(), now, 5);
    }

    private void dagger(KnifeMesh m, Palette p, KnifeMesh.Finish f, float shine, int glow, double now) {
        m.gem(0, -0.04f, 0.026f, p.glow());
        m.metal(-0.022f, -0.012f, -0.022f, 0.022f, 0.004f, 0.022f, p.accent());
        for (int i = 0; i < 8; i++) { // wrapped grip
            float y0 = 0.004f + i * 0.025f;
            m.box(-0.02f, y0, -0.02f, 0.02f, y0 + 0.025f, 0.02f, i % 2 == 0 ? p.handle() : ColorUtil.lerp(p.handle(), p.accent(), 0.3), ColorUtil.lerp(p.handle(), p.glow(), 0.15));
        }
        m.metal(-0.026f, 0.2f, -0.024f, 0.026f, 0.212f, 0.024f, p.accent());
        m.star(0, 0.226f, 0.1f, 0.04f, 0.62f, 0.022f, ColorUtil.lerp(p.accent(), 0xFFFFFFFF, 0.3), p.glow(), true);
        // double-edged diamond blade
        int n = 18;
        float y0 = 0.24f, y1 = 0.74f;
        float[][] sp = new float[n][], ed = new float[n][];
        for (int i = 0; i < n; i++) {
            float s = i / (float) (n - 1), y = y0 + (y1 - y0) * s;
            float w = s < 0.08f ? 0.03f + s * 0.25f : 0.05f * (1 - (float) Math.pow((s - 0.08f) / 0.92f, 1.15f)) + 0.0015f;
            sp[i] = new float[]{-w, y};
            ed[i] = new float[]{w, y};
        }
        m.blade(sp, ed, 0.011f, true, f, shine, glow);
        if (sparkles.isOn()) bladeSparkles(m, sp, ed, p.glow(), now, 6);
        double a = now * 2.2; // orbiting sparkle around the star guard
        m.sparkle((float) Math.cos(a) * 0.11f, 0.226f + (float) Math.sin(a) * 0.05f, 0.03f, 0.03f, p.glow());
    }

    private void kunai(KnifeMesh m, Palette p, KnifeMesh.Finish f, float shine, int glow, double now) {
        // ring pommel, cord-wrapped grip, leaf-shaped double-edged blade
        m.ring(0, -0.055f, 0.035f, 0.011f, ColorUtil.lerp(0xFF9EA3B0, p.accent(), 0.3), ColorUtil.lerp(0xFFE9ECF3, p.accent(), 0.3));
        m.metal(-0.012f, -0.022f, -0.012f, 0.012f, 0.0f, 0.012f, p.accent());
        for (int i = 0; i < 12; i++) {
            float y0 = i * 0.018f;
            boolean a = i % 2 == 0;
            m.box(a ? -0.017f : -0.015f, y0, a ? -0.015f : -0.017f, a ? 0.017f : 0.015f, y0 + 0.018f, a ? 0.015f : 0.017f,
                    a ? p.handle() : ColorUtil.lerp(p.handle(), p.glow(), 0.4));
        }
        m.metal(-0.02f, 0.216f, -0.016f, 0.02f, 0.23f, 0.016f, p.accent());
        int n = 20;
        float y0 = 0.23f, y1 = 0.62f;
        float[][] sp = new float[n][], ed = new float[n][];
        for (int i = 0; i < n; i++) {
            float s = i / (float) (n - 1), y = y0 + (y1 - y0) * s;
            float w = (float) (0.012 + 0.05 * Math.sin(Math.min(1, s * 1.25) * Math.PI) * (1 - s * 0.25)) * (s > 0.85f ? (1 - (s - 0.85f) / 0.15f) : 1) + 0.0015f;
            sp[i] = new float[]{-w, y};
            ed[i] = new float[]{w, y};
        }
        m.blade(sp, ed, 0.01f, true, f, shine, glow);
        if (sparkles.isOn()) bladeSparkles(m, sp, ed, p.glow(), now, 4);
    }

    private void tanto(KnifeMesh m, Palette p, KnifeMesh.Finish f, float shine, int glow, double now) {
        // wrapped tsuka with a diamond pattern, round tsuba, habaki collar, angular tanto point
        m.metal(-0.022f, -0.02f, -0.02f, 0.022f, 0.0f, 0.02f, p.accent());                       // kashira (end cap)
        for (int i = 0; i < 10; i++) {
            float y0 = i * 0.022f;
            m.box(-0.021f, y0, -0.019f, 0.021f, y0 + 0.022f, 0.019f, p.handle());
            // crossing wrap: two slanted bands per segment, front and back
            float yc = y0 + 0.011f;
            for (int side = -1; side <= 1; side += 2) {
                float z = side * 0.0195f;
                int wc = m.lit(ColorUtil.lerp(p.accent(), p.glow(), 0.3), side > 0 ? 1f : 0.75f);
                m.quad(KnifeMesh.v(-0.021f, yc - 0.004f, z), KnifeMesh.v(0f, yc - 0.011f, z), KnifeMesh.v(0.021f, yc - 0.004f, z), KnifeMesh.v(0f, yc + 0.003f, z), wc);
            }
        }
        m.ring(0, 0.226f, 0.034f, 0.024f, ColorUtil.lerp(0xFF2A2C34, p.accent(), 0.3), ColorUtil.lerp(0xFF8A8F9C, p.accent(), 0.4)); // tsuba
        m.metal(-0.03f, 0.232f, -0.012f, 0.03f, 0.252f, 0.012f, 0xFFFFE07A);                       // habaki
        int n = 20;
        float y0 = 0.252f, y1 = 0.74f;
        float[][] sp = new float[n][], ed = new float[n][];
        for (int i = 0; i < n; i++) {
            float s = i / (float) (n - 1), y = y0 + (y1 - y0) * s;
            float spineX = -0.03f;
            float edgeX = s < 0.82f ? 0.034f : 0.034f - (s - 0.82f) / 0.18f * 0.062f;                 // the angular tanto tip
            float ey = s < 0.82f ? y : y + (s - 0.82f) * 0.02f;
            sp[i] = new float[]{spineX, y + (s > 0.9f ? (s - 0.9f) * 0.12f : 0)};
            ed[i] = new float[]{Math.max(edgeX, spineX + 0.0015f), ey};
        }
        m.blade(sp, ed, 0.01f, false, f, shine, glow);
        if (sparkles.isOn()) bladeSparkles(m, sp, ed, p.glow(), now, 4);
    }

    /** Twinkling four-point sparkles scattered on both faces of the blade. */
    private void bladeSparkles(KnifeMesh m, float[][] sp, float[][] ed, int c, double now, int count) {
        java.util.Random rnd = new java.util.Random(31);
        for (int i = 0; i < count; i++) {
            float s = 0.1f + rnd.nextFloat() * 0.75f, u = 0.15f + rnd.nextFloat() * 0.4f;
            double tw = Math.sin(now * (2.5 + rnd.nextFloat() * 2) + i * 1.7);
            if (tw < 0.2) continue;
            int idx = Math.min(sp.length - 1, (int) (s * (sp.length - 1)));
            float x = sp[idx][0] + (ed[idx][0] - sp[idx][0]) * u, y = sp[idx][1] + (ed[idx][1] - sp[idx][1]) * u;
            float r = 0.012f + 0.014f * (float) tw;
            m.sparkle(x, y, 0.012f, r, c);
            m.sparkle(x, y, -0.012f, r, c);
        }
    }

    /** Enchanted sword: glint bands sweep over the whole knife in the glint colour. */
    private void glint(KnifeMesh m, double now) {
        CustomGlint cg = ModuleManager.get(CustomGlint.class);
        int c = cg != null && cg.isEnabled() ? cg.previewColor() : 0xFF8F4BFF;
        java.util.List<KnifeMesh.Quad> extra = new java.util.ArrayList<>();
        for (KnifeMesh.Quad q : m.solid) {
            int[] cols = new int[4];
            for (int i = 0; i < 4; i++) {
                float x = q.p()[i * 3], y = q.p()[i * 3 + 1];
                double band = Math.sin((y * 9 - x * 4 - now * 2.4) * Math.PI);
                cols[i] = ColorUtil.withAlpha(c, (int) (Math.max(0, band) * 110));
            }
            float[] pts = q.p().clone();
            for (int i = 0; i < 4; i++) pts[i * 3 + 2] *= 1.04f; // just above the surface
            extra.add(new KnifeMesh.Quad(pts, cols));
        }
        m.glow.addAll(extra);
    }
}
