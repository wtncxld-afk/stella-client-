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
    public static final String[] NAMES = {"Stella", "Pink", "Purple", "Blue", "Red", "Emerald", RAINBOW, CUSTOM};
    public static final int[] ACCENTS = {0xFFA855F7, 0xFFF472B6, 0xFF8B5CF6, 0xFF60A5FA, 0xFFF87171, 0xFF34D399, 0xFFFF6AD5, 0xFFA855F7};

    public static final Theme STELLA = fromAccent("Stella", 0xFFA855F7);

    /** Builds the charcoal Stella palette around one accent colour. */
    public static Theme fromAccent(String name, int accent) {
        accent |= 0xFF000000;
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

    public static int accentOf(String name) {
        for (int i = 0; i < NAMES.length; i++) if (NAMES[i].equalsIgnoreCase(name)) return ACCENTS[i];
        return ACCENTS[0];
    }
}
