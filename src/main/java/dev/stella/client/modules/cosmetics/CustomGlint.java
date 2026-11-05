package dev.stella.client.modules.cosmetics;

import dev.stella.client.StellaClient;
import dev.stella.client.gui.theme.ThemeManager;
import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.setting.ColorSetting;
import dev.stella.client.setting.ModeSetting;
import dev.stella.client.setting.NumberSetting;
import dev.stella.client.util.ColorUtil;
import dev.stella.client.util.OptionOverrides;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

/**
 * Recolours and restyles the enchantment glint on items and armor. Works by swapping the glint
 * texture for one generated here (Stella sparkles, waves, a single shine, or classic streaks) in your
 * colour; speed and strength use vanilla's own glint options without changing your options.txt.
 */
public class CustomGlint extends Module {
    private final ModeSetting style = add(new ModeSetting("Style", "Glint pattern", "Stella", "Stella", "Classic", "Waves", "Shine"));
    private final ModeSetting colorMode = add(new ModeSetting("Color", "Theme accent, rainbow, custom or a two-colour gradient", "Theme", "Theme", "Rainbow", "Custom", "Gradient"));
    private final ColorSetting color1 = add(new ColorSetting("Custom Color", "", 0xFFB46CFF)).visibleWhen(() -> colorMode.is("Custom") || colorMode.is("Gradient"));
    private final ColorSetting color2 = add(new ColorSetting("Second Color", "", 0xFF4FD8FF)).visibleWhen(() -> colorMode.is("Gradient"));
    private final NumberSetting speed = add(new NumberSetting("Speed", "How fast the glint moves", 0.6, 0.0, 2.0, 0.05).suffix("x"));
    private final NumberSetting strength = add(new NumberSetting("Strength", "How bright the glint is", 0.9, 0.1, 1.0, 0.05));
    private final NumberSetting brightness = add(new NumberSetting("Brightness", "Colour intensity of the pattern", 1.0, 0.3, 1.6, 0.05));

    private static final int SIZE = 256;
    private static final Identifier[] TARGETS = {ItemRenderer.ITEM_ENCHANTMENT_GLINT, ItemRenderer.ENTITY_ENCHANTMENT_GLINT};
    private final NativeImageBackedTexture[] textures = new NativeImageBackedTexture[TARGETS.length];
    private String lastKey = "";
    private long lastCheck;

    public CustomGlint() { super("Custom Glint", "Recolour and restyle the enchantment glint (Stella sparkles, waves, shine)", Category.COSMETICS, false); }

    private static final Identifier[] OWN = {Identifier.of(StellaClient.MOD_ID, "dynamic/glint_item"), Identifier.of(StellaClient.MOD_ID, "dynamic/glint_entity")};

    /** Used by the TextureManager hook: our texture for a vanilla glint id while enabled, else null. */
    public static net.minecraft.client.texture.AbstractTexture override(Identifier id) {
        CustomGlint g = INSTANCE;
        if (g == null || !g.isEnabled()) return null;
        for (int i = 0; i < TARGETS.length; i++) if (TARGETS[i].equals(id)) return g.textures[i];
        return null;
    }

    private static CustomGlint INSTANCE;

    @Override
    protected void onEnable() {
        INSTANCE = this;
        OptionOverrides.set(mc.options.getGlintSpeed(), () -> speed.get());
        OptionOverrides.set(mc.options.getGlintStrength(), () -> strength.get());
        lastKey = "";
        refresh();
    }

    @Override
    protected void onDisable() {
        // the hook stops serving our texture; vanilla's glint texture was never touched
        OptionOverrides.clear(mc.options.getGlintSpeed());
        OptionOverrides.clear(mc.options.getGlintStrength());
    }

    @Override
    public void onTick() {
        long now = System.currentTimeMillis();
        if (now - lastCheck < 250) return;
        lastCheck = now;
        refresh();
    }

    /** Settings + theme colours captured on the main thread, so the pixels can be computed on another thread. */
    private record Params(String style, String colorMode, int c1, int c2, int accent, int accent2, double gain) {}

    private java.util.concurrent.CompletableFuture<int[]> pending;
    private String pendingKey;

    /**
     * Regenerates the texture when a setting or the theme changes. The 256x256 pattern is computed on a
     * background thread (it used to stall a frame for ~60 ms on every theme change); only the upload runs here.
     */
    private void refresh() {
        INSTANCE = this;
        int accent = ThemeManager.get().accent(), accent2 = ThemeManager.accentAt(0.5);
        String key = style.get() + colorMode.get() + color1.get() + color2.get() + brightness.get() + accent + accent2;
        if (!key.equals(lastKey) && !key.equals(pendingKey)) {
            Params p = new Params(style.get(), colorMode.get(), color1.get(), color2.get(), accent, accent2, brightness.get());
            pendingKey = key;
            pending = java.util.concurrent.CompletableFuture.supplyAsync(() -> pixels(p));
        }
        if (pending == null || !pending.isDone()) return;
        try {
            int[] px = pending.join();
            lastKey = pendingKey;
            for (int i = 0; i < TARGETS.length; i++) {
                NativeImage img = new NativeImage(SIZE, SIZE, false);
                for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) img.setColorArgb(x, y, px[y * SIZE + x]);
                if (textures[i] == null) {
                    final int idx = i;
                    textures[i] = new NativeImageBackedTexture(() -> "stella_glint_" + idx, img);
                    mc.getTextureManager().registerTexture(OWN[i], textures[i]);
                } else {
                    textures[i].setImage(img);
                }
                textures[i].upload();
            }
        } catch (Throwable t) { StellaClient.reportError("Custom Glint", t); }
        pending = null;
        pendingKey = null;
    }

    /** A representative glint colour (knife shimmer, previews). */
    public int previewColor() { return colorAt(0.3, 0.2) | 0xFF000000; }

    private int colorAt(double u, double v) {
        return colorAt(colorMode.get(), color1.get(), color2.get(), ThemeManager.get().accent(), ThemeManager.accentAt(0.5), u, v);
    }

    private static int colorAt(String mode, int c1, int c2, int accent, int accent2, double u, double v) {
        return switch (mode) {
            case "Rainbow" -> ColorUtil.fromHsb((float) ((u + v) % 1.0), 0.8f, 1f, 255);
            case "Custom" -> c1;
            case "Gradient" -> ColorUtil.lerp(c1, c2, 0.5 + 0.5 * Math.sin((u + v) * Math.PI * 2));
            default -> ColorUtil.lerp(accent, accent2, 0.5 + 0.5 * Math.sin((u + v) * Math.PI * 2));
        };
    }

    /** Builds the tiling glint pattern as ARGB pixels (the glint is drawn additively, so black = no glint). Thread-safe. */
    private static int[] pixels(Params p) {
        int[] out = new int[SIZE * SIZE];
        java.util.Random rnd = new java.util.Random(7);
        double[][] sparkles = new double[28][3];
        for (double[] s : sparkles) { s[0] = rnd.nextDouble(); s[1] = rnd.nextDouble(); s[2] = 0.015 + rnd.nextDouble() * 0.035; }
        double gain = p.gain();
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                double u = x / (double) SIZE, v = y / (double) SIZE;
                double k = switch (p.style()) {
                    case "Classic" -> streaks(u, v);
                    case "Waves" -> {
                        double w = Math.sin((u * 3 + Math.sin(v * Math.PI * 2) * 0.35) * Math.PI * 2);
                        yield Math.pow(0.5 + 0.5 * w, 6) * 0.9 + 0.12;
                    }
                    case "Shine" -> {
                        double d = Math.abs(((u + v) % 1.0) - 0.5);
                        yield Math.pow(Math.max(0, 1 - d * 5), 3) + 0.08;
                    }
                    default -> { // Stella: soft streaks plus four-point sparkles
                        double base = streaks(u, v) * 0.6;
                        double sp = 0;
                        for (double[] s : sparkles) sp = Math.max(sp, sparkle(u, v, s[0], s[1], s[2]));
                        yield base + sp * 1.2;
                    }
                };
                k = Math.max(0, Math.min(1.4, k * gain));
                int c = colorAt(p.colorMode(), p.c1(), p.c2(), p.accent(), p.accent2(), u, v);
                int r = (int) Math.min(255, ((c >> 16) & 0xFF) * k), g = (int) Math.min(255, ((c >> 8) & 0xFF) * k), b = (int) Math.min(255, (c & 0xFF) * k);
                out[y * SIZE + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }
        return out;
    }

    /** Diagonal streaky noise that tiles. */
    private static double streaks(double u, double v) {
        double s = 0;
        double[] f = {1, 2, 3, 5};
        double[] a = {0.5, 0.25, 0.18, 0.1};
        for (int i = 0; i < f.length; i++) s += a[i] * (0.5 + 0.5 * Math.sin((u * f[i] + v * f[i] * 0.5 + i * 0.37) * Math.PI * 2));
        return Math.pow(s, 2.2);
    }

    /** Four-point star centred at (cx, cy) with wrap-around, size r. */
    private static double sparkle(double u, double v, double cx, double cy, double r) {
        double dx = Math.abs(u - cx), dy = Math.abs(v - cy);
        dx = Math.min(dx, 1 - dx); dy = Math.min(dy, 1 - dy);
        double cross = Math.max(Math.max(0, 1 - dx / r) * Math.max(0, 1 - dy / (r * 0.12)), Math.max(0, 1 - dy / r) * Math.max(0, 1 - dx / (r * 0.12)));
        double core = Math.max(0, 1 - Math.hypot(dx, dy) / (r * 0.35));
        return Math.max(cross, core);
    }
}
