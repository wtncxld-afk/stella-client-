package dev.stella.client.gui.theme;

/** Colour palette for the ClickGUI and HUD accents (ARGB). */
public record Theme(String name,
                    int background,   // panel body
                    int header,       // panel header
                    int row,          // module row
                    int rowHover,
                    int accent,       // primary purple
                    int accentSoft,   // lavender
                    int text,
                    int textDim,
                    int outline) {

    public static final Theme STELLA = new Theme("Stella",
            0xEE0D0B12, 0xF5141019, 0xCC17141D, 0xDD211B2B, 0xFFA855F7, 0xFFD8B4FE, 0xFFF3F0F7, 0xFF8E879A, 0x40A855F7);
    public static final Theme LAVENDER = new Theme("Lavender",
            0xEE100E14, 0xF518151D, 0xCC1A1721, 0xDD251F2E, 0xFFC4B5FD, 0xFFEDE9FE, 0xFFF5F3FF, 0xFF948DA3, 0x40C4B5FD);
    public static final Theme AMETHYST = new Theme("Amethyst",
            0xEE0B0A10, 0xF5120F18, 0xCC15121B, 0xDD1F1828, 0xFF9333EA, 0xFFC084FC, 0xFFF1EDF6, 0xFF8A8396, 0x409333EA);
    public static final Theme NEBULA = new Theme("Nebula",
            0xEE0E0A12, 0xF516101B, 0xCC19121F, 0xDD25182C, 0xFFE879F9, 0xFFF5D0FE, 0xFFFBF2FD, 0xFF9A8AA0, 0x40E879F9);
    public static final Theme MIDNIGHT = new Theme("Midnight",
            0xF0060608, 0xF80B0B0F, 0xCC101015, 0xDD18181F, 0xFF7C3AED, 0xFFA78BFA, 0xFFE9E7EF, 0xFF7E7A88, 0x407C3AED);

    public static final Theme[] ALL = {STELLA, LAVENDER, AMETHYST, NEBULA, MIDNIGHT};

    public static String[] names() {
        String[] n = new String[ALL.length];
        for (int i = 0; i < ALL.length; i++) n[i] = ALL[i].name;
        return n;
    }

    public static Theme byName(String name) {
        for (Theme t : ALL) if (t.name.equalsIgnoreCase(name)) return t;
        return STELLA;
    }
}
