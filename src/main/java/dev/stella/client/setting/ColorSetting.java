package dev.stella.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** ARGB colour stored as a hex string (#AARRGGBB) in the config. */
public class ColorSetting extends Setting<Integer> {
    public ColorSetting(String name, String description, int argb) { super(name, description, argb); }

    public int argb() { return value; }
    public int alpha() { return (value >>> 24) & 0xFF; }
    public int red() { return (value >> 16) & 0xFF; }
    public int green() { return (value >> 8) & 0xFF; }
    public int blue() { return value & 0xFF; }
    public float rf() { return red() / 255f; }
    public float gf() { return green() / 255f; }
    public float bf() { return blue() / 255f; }
    public float af() { return alpha() / 255f; }

    /** Colour with alpha multiplied by {@code mul} (0..1). */
    public int withAlphaMul(double mul) {
        int a = (int) Math.max(0, Math.min(255, alpha() * mul));
        return (a << 24) | (value & 0xFFFFFF);
    }

    @Override public JsonElement toJson() { return new JsonPrimitive(String.format("#%08X", value)); }

    @Override
    public void fromJson(JsonElement e) {
        if (e == null || !e.isJsonPrimitive()) return;
        try {
            String s = e.getAsString().replace("#", "");
            value = (int) Long.parseLong(s, 16);
        } catch (Exception ignored) { }
    }
}
