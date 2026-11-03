package dev.stella.client.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.stella.client.StellaClient;
import dev.stella.client.config.ConfigManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Optional user-maintained list of media / content-creator names
 * ({@code config/stella/media.json}). Ships empty; Stella never invents names.
 * The Media Base Finder also detects media by rank keywords in the tab list.
 */
public final class MediaDatabase {
    private static Set<String> names = new LinkedHashSet<>();

    private MediaDatabase() {}

    public static Path file() { return ConfigManager.root().resolve("media.json"); }

    public static void load() {
        Path f = file();
        try {
            if (!Files.exists(f)) {
                JsonObject o = new JsonObject();
                o.addProperty("_readme", "Optional: names of media / content creators you have verified yourself. "
                        + "The Media Base Finder also detects media automatically from rank text in the tab list (keywords in its settings).");
                o.add("media", new JsonArray());
                Files.writeString(f, ConfigManager.GSON.toJson(o));
            }
            JsonObject o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            Set<String> out = new LinkedHashSet<>();
            if (o.has("media")) for (JsonElement e : o.getAsJsonArray("media")) {
                String n = e.isJsonObject() ? e.getAsJsonObject().get("name").getAsString() : e.getAsString();
                if (!n.isBlank()) out.add(n.trim().toLowerCase(Locale.ROOT));
            }
            names = out;
        } catch (Exception e) {
            StellaClient.LOGGER.warn("Could not read media.json", e);
        }
    }

    public static boolean contains(String name) { return names.contains(name.toLowerCase(Locale.ROOT)); }
    public static int size() { return names.size(); }
}
