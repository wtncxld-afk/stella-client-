package dev.stella.client;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Which build this is. The same code builds "Stella Client" and, with {@code -Pflavor=israeli}, "Israeli Client"
 * (its own name, config folder, light blue and white theme and an Israeli-flag main menu). The flavour is written
 * into {@code assets/stella/flavor.txt} at build time.
 */
public final class Brand {
    private Brand() {}

    public static final boolean ISRAELI;
    /** "Stella" / "Israeli" */
    public static final String NAME;
    /** "Stella Client" / "Israeli Client" */
    public static final String FULL;
    /** "STELLA" / "ISRAELI" */
    public static final String UPPER;

    static {
        String f = "stella";
        try (InputStream in = Brand.class.getResourceAsStream("/assets/stella/flavor.txt")) {
            if (in != null) f = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (Exception ignored) { }
        ISRAELI = "israeli".equalsIgnoreCase(f);
        NAME = ISRAELI ? "Israeli" : "Stella";
        FULL = NAME + " Client";
        UPPER = NAME.toUpperCase(java.util.Locale.ROOT);
    }

    /** Default ClickGUI theme for this build. */
    public static String defaultTheme() { return ISRAELI ? "Israel" : "Stella"; }
}
