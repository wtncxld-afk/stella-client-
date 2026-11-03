package dev.stella.client.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.stella.client.config.ConfigManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Simple persistent waypoint list (config/stella/waypoints.json). */
public final class Waypoints {
    public record Waypoint(String name, int x, int y, int z, String dimension, int color) {}

    private static final List<Waypoint> LIST = new ArrayList<>();
    private Waypoints() {}

    private static Path file() { return ConfigManager.root().resolve("waypoints.json"); }

    public static List<Waypoint> all() { return LIST; }

    public static void add(Waypoint w) {
        LIST.removeIf(o -> o.x == w.x && o.y == w.y && o.z == w.z && o.dimension.equals(w.dimension));
        LIST.add(w);
        save();
    }

    public static void remove(Waypoint w) { LIST.remove(w); save(); }
    public static void clear() { LIST.clear(); save(); }

    public static void load() {
        LIST.clear();
        try {
            if (!Files.exists(file())) return;
            JsonArray arr = JsonParser.parseString(Files.readString(file())).getAsJsonArray();
            for (var e : arr) {
                JsonObject o = e.getAsJsonObject();
                LIST.add(new Waypoint(o.get("name").getAsString(), o.get("x").getAsInt(), o.get("y").getAsInt(),
                        o.get("z").getAsInt(), o.get("dimension").getAsString(), (int) Long.parseLong(o.get("color").getAsString(), 16)));
            }
        } catch (Exception ignored) { }
    }

    public static void save() {
        JsonArray arr = new JsonArray();
        for (Waypoint w : LIST) {
            JsonObject o = new JsonObject();
            o.addProperty("name", w.name);
            o.addProperty("x", w.x);
            o.addProperty("y", w.y);
            o.addProperty("z", w.z);
            o.addProperty("dimension", w.dimension);
            o.addProperty("color", String.format("%08X", w.color));
            arr.add(o);
        }
        try { Files.writeString(file(), ConfigManager.GSON.toJson(arr)); } catch (Exception ignored) { }
    }
}
