package dev.stella.client.util;

/** Global animation speed, controlled by the GUI settings module. */
public final class AnimationSettings {
    private static double multiplier = 1.0;
    private static boolean enabled = true;
    private AnimationSettings() {}
    public static void set(boolean on, double mul) { enabled = on; multiplier = mul; }
    public static double speedMultiplier() { return enabled ? multiplier : 1000.0; }
}
