package dev.stella.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class BoolSetting extends Setting<Boolean> {
    public BoolSetting(String name, String description, boolean def) { super(name, description, def); }
    public boolean isOn() { return value; }
    public void toggle() { set(!value); }
    @Override public JsonElement toJson() { return new JsonPrimitive(value); }
    @Override public void fromJson(JsonElement e) { if (e != null && e.isJsonPrimitive()) value = e.getAsBoolean(); }
}
