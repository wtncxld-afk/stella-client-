package dev.stella.client.gui.theme;

import dev.stella.client.util.ColorUtil;

/** Colour palette for the ClickGUI and HUD accents (ARGB). All themes share the charcoal base. */
public record Theme(String name,
                    int background,   // panel body
                    int header,       // panel header
                    int row,          // module row
                    int rowHover,
                    int accent,       // primary accent
                    int accentSoft,   // light accent
                    int text,
                    int textDim,
                    int outline) {

    public static final String RAINBOW = "Rainbow";
    public static final String CUSTOM = "Custom";

    /** Named presets: name, accent colour. */
    public static final String GALAXY = "Galaxy";
    public static final String AURORA = "Aurora";
    public static final String GLASS = "Liquid Glass";

    public static final String[] NAMES = {"Stella", "Pink", "Purple", "Blue", "Red", "Emerald",
            "Midnight", "Ocean", "Aqua", "Frost", "Sakura", "Lavender", "Sunset", "Amber", "Gold", "Crimson",
            "Toxic", "Mint", "Mono", GALAXY, AURORA, GLASS, RAINBOW, CUSTOM};
    public static final int[] ACCENTS = {0xFFA855F7, 0xFFF472B6, 0xFF8B5CF6, 0xFF60A5FA, 0xFFF87171, 0xFF34D399,
            0xFF6366F1, 0xFF0EA5E9, 0xFF2DD4BF, 0xFF93C5FD, 0xFFFDA4AF, 0xFFC4B5FD, 0xFFFB923C, 0xFFF59E0B, 0xFFFACC15, 0xFFDC2626,
            0xFFA3E635, 0xFF6EE7B7, 0xFFE5E7EB, 0xFFC084FC, 0xFF5EEAD4, 0xFF9AD8FF, 0xFFFF6AD5, 0xFFA855F7};
    /** Background tint per theme (0 = Stella's neutral charcoal). */
    public static final int[] TINTS = {0, 0, 0, 0, 0, 0,
            0xFF1E1B4B, 0xFF082F49, 0xFF042F2E, 0xFF1E293B, 0xFF4C0519, 0xFF2E1065, 0xFF431407, 0xFF451A03, 0xFF422006, 0xFF450A0A,
            0xFF1A2E05, 0xFF022C22, 0, 0xFF2E1065, 0xFF042F2E, 0, 0, 0};

    /** Animated themes: hue range they drift through. */
    public static float[] animatedHues(String name) {
        if (GALAXY.equalsIgnoreCase(name)) return new float[]{0.62f, 0.92f};
        if (AURORA.equalsIgnoreCase(name)) return new float[]{0.40f, 0.78f};
        return null;
    }

    public static final Theme STELLA = fromAccent("Stella", 0xFFA855F7);

    /** Builds the charcoal Stella palette around one accent colour. */
    public static Theme fromAccent(String name, int accent) {
        return fromAccent(name, accent, tintOf(name));
    }

    /** Builds a palette around an accent, with panel backgrounds tinted toward {@code tint} (0 = none). */
    public static Theme fromAccent(String name, int accent, int tint) {
        accent |= 0xFF000000;
        if (GLASS.equalsIgnoreCase(name)) {
            // frosted glass: translucent cool panels, white text, icy accent
            return new Theme(name, 0x58222C3E, 0x30FFFFFF, 0x0CFFFFFF, 0x26FFFFFF, accent,
                    ColorUtil.lerp(accent, 0xFFFFFFFF, 0.6), 0xFFFFFFFF, 0xFFCFD8E6, 0x70FFFFFF);
        }
        if (tint != 0) {
            int bg = ColorUtil.lerp(0xFF111015, tint, 0.45);
            int head = ColorUtil.lerp(0xFF161419, tint, 0.55);
            int row = ColorUtil.lerp(0xFF18161C, tint, 0.35);
            int hov = ColorUtil.lerp(row, accent, 0.12);
            return new Theme(name, (bg & 0xFFFFFF) | 0xF0000000, (head & 0xFFFFFF) | 0xF8000000, row | 0xFF000000, hov | 0xFF000000,
                    accent, ColorUtil.lerp(accent, 0xFFFFFFFF, 0.55), 0xFFF2EFF6, ColorUtil.lerp(0xFF8C8696, accent, 0.18),
                    ColorUtil.withAlpha(accent, 0x55));
        }
        int soft = ColorUtil.lerp(accent, 0xFFFFFFFF, 0.55);
        int hover = ColorUtil.lerp(0xFF1B1820, accent, 0.10);
        return new Theme(name,
                0xF0111015,        // background
                0xF8161419,        // header
                0xFF18161C,        // row
                hover | 0xFF000000,
                accent,
                soft,
                0xFFF2EFF6,
                0xFF8C8696,
                ColorUtil.withAlpha(accent, 0x55));
    }

    public static String[] names() { return NAMES.clone(); }

    public static int tintOf(String name) {
        for (int i = 0; i < NAMES.length; i++) if (NAMES[i].equalsIgnoreCase(name)) return TINTS[i];
        return 0;
    }

    public static int accentOf(String name) {
        for (int i = 0; i < NAMES.length; i++) if (NAMES[i].equalsIgnoreCase(name)) return ACCENTS[i];
        return ACCENTS[0];
    }
}
