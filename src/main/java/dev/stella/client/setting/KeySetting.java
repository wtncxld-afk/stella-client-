package dev.stella.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/** GLFW key code; {@link GLFW#GLFW_KEY_UNKNOWN} (-1) means unbound. */
public class KeySetting extends Setting<Integer> {
    public KeySetting(String name, String description, int def) { super(name, description, def); }

    public int key() { return value; }
    public boolean isBound() { return value != GLFW.GLFW_KEY_UNKNOWN; }

    public String keyName() {
        if (!isBound()) return "None";
        try {
            String s = InputUtil.Type.KEYSYM.createFromCode(value).getLocalizedText().getString();
            return s.length() > 12 ? s.substring(0, 12) : s;
        } catch (Exception e) {
            return "Key " + value;
        }
    }

    @Override public JsonElement toJson() { return new JsonPrimitive(value); }
    @Override public void fromJson(JsonElement e) { if (e != null && e.isJsonPrimitive()) value = e.getAsInt(); }
}
