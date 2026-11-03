package dev.stella.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.stella.client.StellaClient;
import dev.stella.client.module.Module;
import dev.stella.client.module.ModuleManager;
import dev.stella.client.setting.Setting;
import dev.stella.client.setting.SettingsDirty;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Profile-based persistence. Layout on disk:
 * <pre>
 * config/stella/client.json          active profile + global flags
 * config/stella/profiles/NAME.json   module states, settings, HUD positions, GUI layout, keybinds, theme
 * config/stella/exports/             exported profiles
 * config/stella/staff.json           user-maintained staff roster (see StaffDatabase)
 * config/stella/regions.json         Region Map grid (placeholder until verified)
 * </pre>
 */
public final class ConfigManager {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final int VERSION = 2;

    private static String activeProfile = "default";
    private static int ticksSinceSave;

    private ConfigManager() {}

    public static Path root() {
        Path p = FabricLoader.getInstance().getConfigDir().resolve("stella");
        try { Files.createDirectories(p); } catch (IOException ignored) { }
        return p;
    }

    public static Path profilesDir() { return ensure(root().resolve("profiles")); }
    public static Path exportsDir() { return ensure(root().resolve("exports")); }

    private static Path ensure(Path p) {
        try { Files.createDirectories(p); } catch (IOException ignored) { }
        return p;
    }

    public static String activeProfile() { return activeProfile; }
    public static boolean isAutoSave() {
        dev.stella.client.modules.settings.ConfigModule m = ModuleManager.get(dev.stella.client.modules.settings.ConfigModule.class);
        return m == null || !m.isEnabled() || m.autoSave.isOn();
    }

    // ---------------------------------------------------------------- startup

    public static void init() {
        Path client = root().resolve("client.json");
        if (Files.exists(client)) {
            try {
                JsonObject o = JsonParser.parseString(Files.readString(client)).getAsJsonObject();
                if (o.has("activeProfile")) activeProfile = sanitizeName(o.get("activeProfile").getAsString());
            } catch (Exception e) {
                StellaClient.LOGGER.warn("Could not read client.json, using defaults", e);
            }
        }
        if (!Files.exists(profileFile(activeProfile))) {
            applyDefaults();
            save();
        } else {
            load(activeProfile);
        }
    }

    public static void tick() {
        if (!isAutoSave()) return;
        dev.stella.client.modules.settings.ConfigModule cm = ModuleManager.get(dev.stella.client.modules.settings.ConfigModule.class);
        int every = cm == null ? 100 : cm.interval.getInt() * 20;
        if (++ticksSinceSave >= every) {
            ticksSinceSave = 0;
            if (SettingsDirty.consume()) save();
        }
    }

    // ---------------------------------------------------------------- profiles

    public static Path profileFile(String name) { return profilesDir().resolve(sanitizeName(name) + ".json"); }

    public static String sanitizeName(String s) {
        String n = s.replaceAll("[^A-Za-z0-9_\\- ]", "").trim();
        return n.isEmpty() ? "default" : (n.length() > 32 ? n.substring(0, 32) : n);
    }

    public static List<String> listProfiles() {
        List<String> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(profilesDir())) {
            s.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                String f = p.getFileName().toString();
                out.add(f.substring(0, f.length() - 5));
            });
        } catch (IOException ignored) { }
        if (!out.contains(activeProfile)) out.add(activeProfile);
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    public static void save() {
        writeProfile(activeProfile, serialize());
        saveClientFile();
    }

    public static boolean switchProfile(String name) {
        save();
        String n = sanitizeName(name);
        activeProfile = n;
        if (Files.exists(profileFile(n))) load(n);
        else { save(); }
        saveClientFile();
        return true;
    }

    public static void createProfile(String name, boolean copyCurrent) {
        String n = sanitizeName(name);
        save();
        activeProfile = n;
        if (!copyCurrent) applyDefaults();
        save();
    }

    public static boolean deleteProfile(String name) {
        String n = sanitizeName(name);
        if (n.equals(activeProfile)) return false;
        try { return Files.deleteIfExists(profileFile(n)); } catch (IOException e) { return false; }
    }

    public static void resetToDefaults() {
        applyDefaults();
        save();
    }

    // ---------------------------------------------------------------- import / export

    /** Writes the active profile to config/stella/exports and returns the JSON text. */
    public static Path exportProfile() throws IOException {
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path out = exportsDir().resolve(activeProfile + "-" + stamp + ".json");
        Files.writeString(out, GSON.toJson(serialize()), StandardCharsets.UTF_8);
        return out;
    }

    public static String exportString() { return GSON.toJson(serialize()); }

    /** Imports a profile JSON (e.g. from the clipboard) into a new profile. */
    public static String importString(String json, String baseName) throws Exception {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        if (!o.has("modules")) throw new IllegalArgumentException("Not a Stella profile (missing 'modules')");
        String name = sanitizeName(baseName);
        int i = 1;
        while (Files.exists(profileFile(name))) name = sanitizeName(baseName) + "-" + (i++);
        writeProfile(name, o);
        return name;
    }

    // ---------------------------------------------------------------- serialisation

    private static JsonObject serialize() {
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        JsonObject modules = new JsonObject();
        for (Module m : ModuleManager.all()) {
            JsonObject mo = new JsonObject();
            mo.addProperty("enabled", m.isEnabled());
            JsonObject so = new JsonObject();
            for (Setting<?> s : m.getSettings()) so.add(s.getName(), s.toJson());
            mo.add("settings", so);
            modules.add(m.getName(), mo);
        }
        root.add("modules", modules);
        root.add("gui", GuiLayout.toJson());
        return root;
    }

    private static void load(String name) {
        try {
            JsonObject root = JsonParser.parseString(Files.readString(profileFile(name))).getAsJsonObject();
            applyDefaults();
            JsonObject modules = root.has("modules") ? root.getAsJsonObject("modules") : new JsonObject();
            for (Module m : ModuleManager.all()) {
                if (!modules.has(m.getName())) continue;
                JsonObject mo = modules.getAsJsonObject(m.getName());
                if (mo.has("settings")) {
                    JsonObject so = mo.getAsJsonObject("settings");
                    for (Setting<?> s : m.getSettings()) {
                        JsonElement e = so.get(s.getName());
                        if (e != null) {
                            try { s.fromJson(e); } catch (Exception ex) { StellaClient.LOGGER.warn("Bad value for {}.{}", m.getName(), s.getName()); }
                        }
                    }
                }
                if (mo.has("enabled")) m.loadEnabled(mo.get("enabled").getAsBoolean());
            }
            GuiLayout.fromJson(root.has("gui") ? root.getAsJsonObject("gui") : null);
            int version = root.has("version") ? root.get("version").getAsInt() : 1;
            if (version < 2) {
                // v2: compact HUD (smaller elements and Region Map); move everything to the new default layout once
                for (Module m : ModuleManager.all()) if (m instanceof dev.stella.client.hud.HudElement h) h.resetLayout();
                StellaClient.LOGGER.info("Profile {}: HUD moved to the compact v2 layout", name);
            }
            ModuleManager.afterConfigLoad();
        } catch (Exception e) {
            StellaClient.LOGGER.error("Failed to load profile {}; defaults applied", name, e);
            applyDefaults();
        }
    }

    private static void applyDefaults() {
        for (Module m : ModuleManager.all()) {
            m.resetSettings();
            m.loadEnabled(m.isDefaultEnabled());
        }
        GuiLayout.reset();
        ModuleManager.afterConfigLoad();
    }

    private static void writeProfile(String name, JsonObject o) {
        Path file = profileFile(name);
        try {
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(o), StandardCharsets.UTF_8);
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            try { Files.writeString(file, GSON.toJson(o), StandardCharsets.UTF_8); } catch (IOException ex) {
                StellaClient.LOGGER.error("Could not save profile {}", name, ex);
            }
        }
    }

    private static void saveClientFile() {
        JsonObject o = new JsonObject();
        o.addProperty("activeProfile", activeProfile);
        try { Files.writeString(root().resolve("client.json"), GSON.toJson(o)); } catch (IOException ignored) { }
    }
}
