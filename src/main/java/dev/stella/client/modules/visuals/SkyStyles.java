package dev.stella.client.modules.visuals;

import dev.stella.client.gui.theme.ThemeManager;
import dev.stella.client.util.ColorUtil;

/**
 * Sky designs. Each one is a palette plus signature effects: moons and suns with halos, shooting stars,
 * a milky way, a spiral galaxy, drifting particles (petals, embers, snow, bubbles, sparkles), god rays and
 * a rainbow arc. "Match GUI Theme" picks the design for your current ClickGUI theme.
 */
final class SkyStyles {
    private SkyStyles() {}

    static final int NONE = 0, MOON = 1, SUN = 2, CRESCENT = 3;
    static final int P_NONE = 0, P_PETALS = 1, P_EMBERS = 2, P_SNOW = 3, P_BUBBLES = 4, P_SPARKLES = 5;

    /** A full sky design. Colours are ARGB; 0 means "off" for optional effects. */
    record Style(int top, int horizon, int bottom, double atmosphere,
                 int stars, int starColor, int nebulaA, int nebulaB, int aurora,
                 int body, int bodyColor, float bodySize, float bodyElevation, float bodyAzimuth,
                 boolean shootingStars, int milkyWay, int galaxy,
                 int particles, int particleColor, boolean rays, boolean rainbow) {}

    static final String[] PRESETS = {"Match GUI Theme", "Cosmic Stella", "Sakura Dawn", "Lavender Sunset", "Starlight",
            "Blood Moon", "Emerald Aurora", "Midnight", "Deep Ocean", "Arctic Aurora", "Golden Hour", "Toxic Nebula",
            "Monochrome", "Spiral Galaxy", "Prism Glass", "Rainbow Drift", "Blue & White", "Custom"};

    static Style preset(String name) {
        return switch (name) {
            case "Sakura Dawn" -> new Style(0xFF2A1638, 0xFFF7A8C4, 0xFF4A2848, 0.55,
                    500, 0xFFFFE4F1, 0x50F472B6, 0x40FDA4AF, 0,
                    CRESCENT, 0xFFFFF1F7, 0.06f, 0.45f, 2.4f, true, 0, 0, P_PETALS, 0xFFFFB7D5, false, false);
            case "Lavender Sunset" -> new Style(0xFF2E1F5E, 0xFFF4A3C8, 0xFF5B3A7A, 0.6,
                    350, 0xFFFFF1F8, 0x50C4B5FD, 0x50FDA4AF, 0,
                    SUN, 0xFFFFC8A8, 0.09f, 0.06f, 1.1f, false, 0, 0, P_SPARKLES, 0xFFFFE0F0, true, false);
            case "Starlight" -> new Style(0xFF030A1E, 0xFF13306B, 0xFF01040C, 0.3,
                    2600, 0xFFDCEBFF, 0x5060A5FA, 0x403B82F6, 0,
                    MOON, 0xFFE8F0FF, 0.05f, 0.62f, 0.6f, true, 0xFF9CC4FF, 0, P_NONE, 0, false, false);
            case "Blood Moon" -> new Style(0xFF120203, 0xFF6B0F14, 0xFF0A0102, 0.45,
                    700, 0xFFFFC2C2, 0x60B91C1C, 0x407F1D1D, 0,
                    MOON, 0xFFFF3B30, 0.11f, 0.35f, 0.4f, false, 0, 0, P_EMBERS, 0xFFFF6A2A, true, false);
            case "Emerald Aurora" -> new Style(0xFF01120B, 0xFF0B4D35, 0xFF010A06, 0.32,
                    1400, 0xFFD1FAE5, 0x4034D399, 0x3010B981, 0xB034D399,
                    CRESCENT, 0xFFE6FFF4, 0.045f, 0.7f, 3.6f, true, 0, 0, P_SPARKLES, 0xFF6EE7B7, false, false);
            case "Midnight" -> new Style(0xFF020205, 0xFF0D0F1C, 0xFF010103, 0.25,
                    3200, 0xFFE2E8F0, 0x30475569, 0x30334155, 0,
                    MOON, 0xFFF1F5F9, 0.055f, 0.5f, 5.2f, true, 0xFFB8C2D6, 0, P_NONE, 0, false, false);
            case "Deep Ocean" -> new Style(0xFF021626, 0xFF0B4F6C, 0xFF010A12, 0.5,
                    600, 0xFFBDE8FF, 0x500EA5E9, 0x402DD4BF, 0,
                    SUN, 0xFFB8F1FF, 0.07f, 0.85f, 0.0f, false, 0, 0, P_BUBBLES, 0xFF9BE7FF, true, false);
            case "Arctic Aurora" -> new Style(0xFF020B1A, 0xFF0E3B5C, 0xFF01060E, 0.3,
                    1900, 0xFFE0F2FE, 0x4038BDF8, 0x3022D3EE, 0xA022D3EE,
                    MOON, 0xFFEFF8FF, 0.045f, 0.55f, 2.0f, true, 0, 0, P_SNOW, 0xFFF0F9FF, false, false);
            case "Golden Hour" -> new Style(0xFF3A1C0A, 0xFFFFB347, 0xFF4A2410, 0.7,
                    120, 0xFFFFF4D6, 0x50FB923C, 0x40FACC15, 0,
                    SUN, 0xFFFFD27A, 0.12f, 0.04f, 4.2f, false, 0, 0, P_SPARKLES, 0xFFFFE6A8, true, false);
            case "Toxic Nebula" -> new Style(0xFF061005, 0xFF2F5A12, 0xFF030802, 0.4,
                    1300, 0xFFECFCCB, 0x80A3E635, 0x6022C55E, 0,
                    CRESCENT, 0xFFD9F99D, 0.06f, 0.5f, 1.4f, true, 0, 0, P_EMBERS, 0xFFA3E635, false, false);
            case "Monochrome" -> new Style(0xFF050505, 0xFF2A2A2E, 0xFF020202, 0.3,
                    2400, 0xFFFFFFFF, 0x30A1A1AA, 0x2071717A, 0,
                    MOON, 0xFFF4F4F5, 0.06f, 0.55f, 3.0f, true, 0xFFD4D4D8, 0, P_NONE, 0, false, false);
            case "Spiral Galaxy" -> new Style(0xFF07021A, 0xFF241052, 0xFF03010A, 0.3,
                    2200, 0xFFF3E8FF, 0x60C084FC, 0x50F472B6, 0,
                    NONE, 0, 0, 0, 0, true, 0, 0xFFE9D5FF, P_NONE, 0, false, false);
            case "Prism Glass" -> new Style(0xFF1B2A4A, 0xFFB8D8FF, 0xFF2A3654, 0.65,
                    300, 0xFFFFFFFF, 0x409AD8FF, 0x40F0ABFC, 0,
                    SUN, 0xFFFFFFFF, 0.06f, 0.5f, 2.2f, false, 0, 0, P_SPARKLES, 0xFFE0F2FF, true, true);
            case "Blue & White" -> new Style(0xFF0B2A5C, 0xFF7FC3FF, 0xFF0A1F44, 0.5,
                    1300, 0xFFFFFFFF, 0x5060A5FA, 0x40FFFFFF, 0,
                    MOON, 0xFFFFFFFF, 0.055f, 0.55f, 2.4f, true, 0xFFD6ECFF, 0, P_SPARKLES, 0xFFFFFFFF, false, false);
            case "Rainbow Drift" -> {
                float h = (System.currentTimeMillis() % 30000L) / 30000f;
                int a = ColorUtil.fromHsb(h, 0.7f, 0.45f, 255), b = ColorUtil.fromHsb((h + 0.33f) % 1f, 0.6f, 0.95f, 255);
                yield new Style(ColorUtil.lerp(a, 0xFF000000, 0.75), b, ColorUtil.lerp(a, 0xFF000000, 0.85), 0.45,
                        1500, 0xFFFFFFFF, ColorUtil.withAlpha(ColorUtil.fromHsb((h + 0.5f) % 1f, 0.7f, 1f, 255), 0x60),
                        ColorUtil.withAlpha(ColorUtil.fromHsb((h + 0.8f) % 1f, 0.7f, 1f, 255), 0x50), 0,
                        NONE, 0, 0, 0, 0, true, 0, 0, P_SPARKLES, 0xFFFFFFFF, false, true);
            }
            default -> new Style(0xFF0B0418, 0xFF3B1466, 0xFF07030D, 0.35, // Cosmic Stella
                    1500, 0xFFF3E8FF, 0x80A855F7, 0x60EC4899, 0,
                    CRESCENT, 0xFFF3E8FF, 0.05f, 0.55f, 2.8f, true, 0xFFD8B4FE, 0, P_SPARKLES, 0xFFE9D5FF, false, false);
        };
    }

    /** The design for a ClickGUI theme. */
    static String forTheme(String theme) {
        return switch (theme) {
            case "Pink", "Sakura" -> "Sakura Dawn";
            case "Purple", "Lavender" -> "Lavender Sunset";
            case "Blue" -> "Starlight";
            case "Red", "Crimson" -> "Blood Moon";
            case "Emerald", "Mint" -> "Emerald Aurora";
            case "Midnight" -> "Midnight";
            case "Ocean", "Aqua" -> "Deep Ocean";
            case "Frost", "Aurora" -> "Arctic Aurora";
            case "Sunset", "Amber", "Gold" -> "Golden Hour";
            case "Toxic" -> "Toxic Nebula";
            case "Mono" -> "Monochrome";
            case "Galaxy" -> "Spiral Galaxy";
            case "Liquid Glass" -> "Prism Glass";
            case "Rainbow" -> "Rainbow Drift";
            case "Israel" -> "Blue & White";
            case "Custom" -> "Accent";
            default -> "Cosmic Stella";
        };
    }

    /** Builds a design around the theme's accent colour (used for the Custom theme). */
    static Style fromAccent() {
        int acc = ThemeManager.get().accent(), acc2 = ThemeManager.accentAt(0.5);
        return new Style(ColorUtil.lerp(acc, 0xFF000000, 0.88), ColorUtil.lerp(acc, 0xFF000000, 0.45), ColorUtil.lerp(acc, 0xFF000000, 0.93), 0.4,
                1600, ColorUtil.lerp(acc, 0xFFFFFFFF, 0.8), ColorUtil.withAlpha(acc, 0x70), ColorUtil.withAlpha(acc2, 0x50), 0,
                CRESCENT, ColorUtil.lerp(acc, 0xFFFFFFFF, 0.85), 0.05f, 0.55f, 2.6f, true, ColorUtil.lerp(acc, 0xFFFFFFFF, 0.6), 0,
                P_SPARKLES, ColorUtil.lerp(acc, 0xFFFFFFFF, 0.5), false, false);
    }
}
