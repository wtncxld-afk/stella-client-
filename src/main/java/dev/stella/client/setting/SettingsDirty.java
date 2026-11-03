package dev.stella.client.setting;

/** Global "something changed" flag used by the auto-save timer. */
public final class SettingsDirty {
    private static volatile boolean dirty;
    private SettingsDirty() {}
    public static void mark() { dirty = true; }
    public static boolean consume() { boolean d = dirty; dirty = false; return d; }
}
