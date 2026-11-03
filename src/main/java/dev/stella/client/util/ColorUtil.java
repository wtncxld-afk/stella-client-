package dev.stella.client.util;

public final class ColorUtil {
    private ColorUtil() {}

    public static int argb(int a, int r, int g, int b) {
        return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    public static int withAlpha(int color, int alpha) { return (clamp(alpha) << 24) | (color & 0xFFFFFF); }

    public static int mulAlpha(int color, double mul) {
        int a = (int) (((color >>> 24) & 0xFF) * Math.max(0, Math.min(1, mul)));
        return (a << 24) | (color & 0xFFFFFF);
    }

    public static int lerp(int c1, int c2, double t) {
        t = Math.max(0, Math.min(1, t));
        int a = (int) (((c1 >>> 24) & 0xFF) + (((c2 >>> 24) & 0xFF) - ((c1 >>> 24) & 0xFF)) * t);
        int r = (int) (((c1 >> 16) & 0xFF) + (((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t);
        int g = (int) (((c1 >> 8) & 0xFF) + (((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * t);
        int b = (int) ((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * t);
        return argb(a, r, g, b);
    }

    public static float[] toHsb(int color) {
        return java.awt.Color.RGBtoHSB((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, null);
    }

    public static int fromHsb(float h, float s, float b, int alpha) {
        int rgb = java.awt.Color.HSBtoRGB(h, s, b) & 0xFFFFFF;
        return (clamp(alpha) << 24) | rgb;
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }
}
