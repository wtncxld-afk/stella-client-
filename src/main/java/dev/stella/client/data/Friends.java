package dev.stella.client.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.stella.client.StellaClient;
import dev.stella.client.config.ConfigManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Your friends list ({@code config/stella/friends.json}), shared by ESP, Radar, Target HUD and combat modules. */
public final class Friends {
    private Friends() {}

    private static final Set<String> NAMES = new LinkedHashSet<>();

    public static Path file() { return ConfigManager.root().resolve("friends.json"); }

    public static boolean isFriend(String name) { return name != null && NAMES.contains(name.toLowerCase(Locale.ROOT)); }
    public static Set<String> all() { return NAMES; }

    /** Adds or removes; returns true when the player is now a friend. */
    public static boolean toggle(String name) {
        String k = name.toLowerCase(Locale.ROOT);
        boolean now = !NAMES.remove(k);
        if (now) NAMES.add(k);
        save();
        return now;
    }

    public static void load() {
        NAMES.clear();
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            JsonObject o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            if (o.has("friends")) o.getAsJsonArray("friends").forEach(e -> NAMES.add(e.getAsString().toLowerCase(Locale.ROOT)));
        } catch (Exception e) { StellaClient.LOGGER.warn("Could not read friends.json", e); }
    }

    public static void save() {
        try {
            JsonObject o = new JsonObject();
            JsonArray a = new JsonArray();
            NAMES.forEach(a::add);
            o.add("friends", a);
            Files.writeString(file(), ConfigManager.GSON.toJson(o));
        } catch (Exception e) { StellaClient.LOGGER.warn("Could not save friends.json", e); }
    }
}
