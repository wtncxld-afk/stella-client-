package dev.stella.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class NumberSetting extends Setting<Double> {
    private final double min, max, step;
    private String suffix = "";

    public NumberSetting(String name, String description, double def, double min, double max, double step) {
        super(name, description, def);
        this.min = min; this.max = max; this.step = step;
    }

    public NumberSetting suffix(String s) { this.suffix = s; return this; }

    public double getMin() { return min; }
    public double getMax() { return max; }
    public double getStep() { return step; }
    public String getSuffix() { return suffix; }
    public int getInt() { return (int) Math.round(value); }
    public float getFloat() { return value.floatValue(); }

    @Override
    protected Double sanitize(Double v) {
        if (v == null || v.isNaN()) return defaultValue;
        double c = Math.max(min, Math.min(max, v));
        if (step > 0) c = Math.round((c - min) / step) * step + min;
        c = Math.max(min, Math.min(max, c));
        return Math.round(c * 10000.0) / 10000.0;
    }

    public String display() {
        if (step >= 1) return getInt() + suffix;
        if (step >= 0.1) return String.format("%.1f", value) + suffix;
        return String.format("%.2f", value) + suffix;
    }

    @Override public JsonElement toJson() { return new JsonPrimitive(value); }
    @Override public void fromJson(JsonElement e) { if (e != null && e.isJsonPrimitive()) value = sanitize(e.getAsDouble()); }
}
