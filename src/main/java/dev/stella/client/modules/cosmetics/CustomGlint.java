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

    @Override
    protected void onEnable() {
        OptionOverrides.set(mc.options.getGlintSpeed(), () -> speed.get());
        OptionOverrides.set(mc.options.getGlintStrength(), () -> strength.get());
        lastKey = "";
        refresh();
    }

    @Override
    protected void onDisable() {
        OptionOverrides.clear(mc.options.getGlintSpeed());
        OptionOverrides.clear(mc.options.getGlintStrength());
        try {
            for (int i = 0; i < TARGETS.length; i++) {
                if (textures[i] != null) mc.getTextureManager().registerTexture(TARGETS[i]); // back to the vanilla texture
                textures[i] = null;
            }
        } catch (Throwable t) { StellaClient.reportError("Custom Glint", t); }
        lastKey = "";
    }

    @Override
    public void onTick() {
        long now = System.currentTimeMillis();
        if (now - lastCheck < 250) return;
        lastCheck = now;
        refresh();
    }

    /** Regenerates the texture when a setting or the theme changes, and re-applies it after a resource reload. */
    private void refresh() {
        String key = style.get() + colorMode.get() + color1.get() + color2.get() + brightness.get() + ThemeManager.get().accent() + ThemeManager.accentAt(0.5);
        boolean replaced = false;
        for (int i = 0; i < TARGETS.length; i++) if (textures[i] == null || mc.getTextureManager().getTexture(TARGETS[i]) != textures[i]) replaced = true;
        if (key.equals(lastKey) && !replaced) return;
        lastKey = key;
        try {
            for (int i = 0; i < TARGETS.length; i++) {
                NativeImage img = generate();
                if (textures[i] == null || mc.getTextureManager().getTexture(TARGETS[i]) != textures[i]) {
                    final int idx = i;
                    textures[i] = new NativeImageBackedTexture(() -> "stella_glint_" + idx, img);
                    mc.getTextureManager().registerTexture(TARGETS[i], textures[i]);
                } else {
                    textures[i].setImage(img);
                }
                textures[i].upload();
            }
        } catch (Throwable t) { StellaClient.reportError("Custom Glint", t); }
    }

    /** A representative glint colour (knife shimmer, previews). */
    public int previewColor() { return colorAt(0.3, 0.2) | 0xFF000000; }

    private int colorAt(double u, double v) {
        return switch (colorMode.get()) {
            case "Rainbow" -> ColorUtil.fromHsb((float) ((u + v) % 1.0), 0.8f, 1f, 255);
            case "Custom" -> color1.get();
            case "Gradient" -> ColorUtil.lerp(color1.get(), color2.get(), 0.5 + 0.5 * Math.sin((u + v) * Math.PI * 2));
            default -> ColorUtil.lerp(ThemeManager.get().accent(), ThemeManager.accentAt(0.5), 0.5 + 0.5 * Math.sin((u + v) * Math.PI * 2));
        };
    }

    /** Builds the tiling glint texture (the glint is drawn additively, so black = no glint). */
    private NativeImage generate() {
        NativeImage img = new NativeImage(SIZE, SIZE, false);
        java.util.Random rnd = new java.util.Random(7);
        double[][] sparkles = new double[28][3];
        for (double[] s : sparkles) { s[0] = rnd.nextDouble(); s[1] = rnd.nextDouble(); s[2] = 0.015 + rnd.nextDouble() * 0.035; }
        double gain = brightness.get();
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                double u = x / (double) SIZE, v = y / (double) SIZE;
                double k = switch (style.get()) {
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
                int c = colorAt(u, v);
                int r = (int) Math.min(255, ((c >> 16) & 0xFF) * k), g = (int) Math.min(255, ((c >> 8) & 0xFF) * k), b = (int) Math.min(255, (c & 0xFF) * k);
                img.setColorArgb(x, y, 0xFF000000 | (r << 16) | (g << 8) | b);
            }
        }
        return img;
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
