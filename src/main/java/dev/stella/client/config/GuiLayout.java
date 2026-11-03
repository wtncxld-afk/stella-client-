package dev.stella.client.config;

import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;

/** Persisted ClickGUI panel positions / collapsed state. */
public final class GuiLayout {
    public static final class PanelState {
        public int x, y;
        public boolean open = true;
        public boolean set;
    }

    private static final Map<String, PanelState> PANELS = new HashMap<>();
    private GuiLayout() {}

    public static PanelState panel(String name) { return PANELS.computeIfAbsent(name, k -> new PanelState()); }

    public static void reset() { PANELS.clear(); }

    public static JsonObject toJson() {
        JsonObject o = new JsonObject();
        PANELS.forEach((k, v) -> {
            if (!v.set) return;
            JsonObject p = new JsonObject();
            p.addProperty("x", v.x);
            p.addProperty("y", v.y);
            p.addProperty("open", v.open);
            o.add(k, p);
        });
        return o;
    }

    public static void fromJson(JsonObject o) {
        PANELS.clear();
        if (o == null) return;
        for (String k : o.keySet()) {
            try {
                JsonObject p = o.getAsJsonObject(k);
                PanelState s = panel(k);
                s.x = p.get("x").getAsInt();
                s.y = p.get("y").getAsInt();
                s.open = !p.has("open") || p.get("open").getAsBoolean();
                s.set = true;
            } catch (Exception ignored) { }
        }
    }
}
