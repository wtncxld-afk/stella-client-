package dev.stella.client.gui.theme;

public final class ThemeManager {
    private static Theme current = Theme.STELLA;
    private ThemeManager() {}
    public static Theme get() { return current; }
    public static void set(Theme t) { current = t; }
}
