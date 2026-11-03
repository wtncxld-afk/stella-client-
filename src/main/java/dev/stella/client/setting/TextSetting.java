package dev.stella.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class TextSetting extends Setting<String> {
    private final int maxLength;
    public TextSetting(String name, String description, String def, int maxLength) {
        super(name, description, def);
        this.maxLength = maxLength;
    }
    public int getMaxLength() { return maxLength; }
    @Override protected String sanitize(String v) {
        if (v == null) return "";
        return v.length() > maxLength ? v.substring(0, maxLength) : v;
    }
    @Override public JsonElement toJson() { return new JsonPrimitive(value); }
    @Override public void fromJson(JsonElement e) { if (e != null && e.isJsonPrimitive()) value = sanitize(e.getAsString()); }
}
