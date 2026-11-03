package dev.stella.client.gui.theme;

import dev.stella.client.util.ColorUtil;

/** Current theme. "Rainbow" cycles hue over time; "Custom" uses a user-picked accent. */
public final class ThemeManager {
    private static String name = "Stella";
    private static int customAccent = 0xFFA855F7;
    private static Theme cached = Theme.STELLA;
    private static long cachedAt;

    private ThemeManager() {}

    public static void select(String themeName, int custom) {
        name = themeName;
        customAccent = custom;
        cachedAt = 0;
    }

    public static String name() { return name; }

    public static Theme get() {
        long now = System.currentTimeMillis();
        if (Theme.RAINBOW.equalsIgnoreCase(name)) {
            if (now - cachedAt > 33) {
                float hue = (now % 6000L) / 6000f;
                cached = Theme.fromAccent(name, ColorUtil.fromHsb(hue, 0.55f, 1f, 255));
                cachedAt = now;
            }
            return cached;
        }
        float[] range = Theme.animatedHues(name);
        if (range != null) {
            if (now - cachedAt > 33) {
                double t = 0.5 + 0.5 * Math.sin(now / 1800.0);
                float hue = (float) (range[0] + (range[1] - range[0]) * t);
                cached = Theme.fromAccent(name, ColorUtil.fromHsb(hue, 0.6f, 1f, 255));
                cachedAt = now;
            }
            return cached;
        }
        if (cachedAt == 0) {
            int accent = Theme.CUSTOM.equalsIgnoreCase(name) ? customAccent : Theme.accentOf(name);
            cached = Theme.fromAccent(name, accent);
            cachedAt = now;
        }
        return cached;
    }

    /** Accent at a position (for rainbow gradients along text / bars). */
    public static int accentAt(double offset) {
        float[] range = Theme.animatedHues(name);
        if (range != null) {
            double t = 0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 1800.0 + offset * Math.PI * 2);
            return ColorUtil.fromHsb((float) (range[0] + (range[1] - range[0]) * t), 0.6f, 1f, 255);
        }
        if (!Theme.RAINBOW.equalsIgnoreCase(name)) return get().accent();
        float hue = (float) (((System.currentTimeMillis() % 6000L) / 6000.0 + offset) % 1.0);
        return ColorUtil.fromHsb(hue, 0.55f, 1f, 255);
    }
}
