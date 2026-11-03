package dev.stella.client.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.stella.client.StellaClient;
import dev.stella.client.config.ConfigManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * User-maintained staff roster loaded from {@code config/stella/staff.json}.
 * <p>
 * Stella ships this file EMPTY on purpose: it never invents staff names or
 * ranks. "Online" only means the name is present in the server's tab list,
 * which is the only reliable signal a normal client receives. Vanished staff are
 * normally removed from the tab list and therefore cannot be shown as online.
 */
public final class StaffDatabase {
    /** Channel fields are optional handles used only for the live-status check. */
    public record StaffMember(String name, String rank, String uuid, String twitch, String youtube, String kick, String tiktok) {
        public StaffMember(String name, String rank, String uuid) { this(name, rank, uuid, "", "", "", ""); }
        public boolean hasChannels() { return !twitch.isBlank() || !youtube.isBlank() || !kick.isBlank() || !tiktok.isBlank(); }
    }

    private static String str(JsonObject o, String k) { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString().trim() : ""; }

    private static List<StaffMember> staff = new ArrayList<>();
    private static String source = "";
    private static String lastVerified = "";

    private StaffDatabase() {}

    public static Path file() { return ConfigManager.root().resolve("staff.json"); }

    public static void load() {
        Path f = file();
        if (!Files.exists(f)) writeTemplate(f);
        try {
            JsonObject o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            List<StaffMember> list = new ArrayList<>();
            JsonArray arr = o.has("staff") ? o.getAsJsonArray("staff") : new JsonArray();
            for (JsonElement e : arr) {
                JsonObject s = e.getAsJsonObject();
                String name = s.has("name") ? s.get("name").getAsString().trim() : "";
                if (name.isEmpty()) continue;
                list.add(new StaffMember(name,
                        s.has("rank") ? s.get("rank").getAsString() : "Staff",
                        str(s, "uuid"), str(s, "twitch"), str(s, "youtube"), str(s, "kick"), str(s, "tiktok")));
            }
            staff = list;
            source = o.has("source") ? o.get("source").getAsString() : "";
            lastVerified = o.has("lastVerified") ? o.get("lastVerified").getAsString() : "";
        } catch (Exception e) {
            StellaClient.LOGGER.error("Could not read staff.json", e);
            staff = new ArrayList<>();
        }
    }

    private static void writeTemplate(Path f) {
        JsonObject o = new JsonObject();
        o.addProperty("_readme", "Add ONLY staff members you have verified yourself (for example from DonutSMP's official website or Discord). "
                + "Stella ships no staff data and never guesses. Example entry: {\"name\": \"PlayerName\", \"rank\": \"Moderator\", \"uuid\": \"\", "
                + "\"twitch\": \"handle\", \"youtube\": \"@handle\", \"kick\": \"handle\", \"tiktok\": \"handle\"} - the channel fields are optional "
                + "and only used to show who is live. "
                + "Use the Staff List module's 'Reload staff.json' option after editing.");
        o.addProperty("source", "");
        o.addProperty("lastVerified", "");
        o.add("staff", new JsonArray());
        try { Files.writeString(f, ConfigManager.GSON.toJson(o)); } catch (Exception ignored) { }
    }

    public static List<StaffMember> all() { return Collections.unmodifiableList(staff); }
    public static String source() { return source; }
    public static String lastVerified() { return lastVerified; }

    public static StaffMember find(String name) {
        for (StaffMember m : staff) if (m.name.equalsIgnoreCase(name)) return m;
        return null;
    }

    public static boolean isStaff(String name) { return find(name) != null; }

    /** Staff currently present in the tab list (the only reliable online signal). */
    public static List<StaffMember> visibleInTab() {
        List<StaffMember> out = new ArrayList<>();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() == null) return out;
        for (StaffMember m : staff) if (tabEntry(m) != null) out.add(m);
        return out;
    }

    public static PlayerListEntry tabEntry(StaffMember m) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() == null) return null;
        if (!m.uuid.isBlank()) {
            try {
                PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(java.util.UUID.fromString(m.uuid));
                if (e != null) return e;
            } catch (IllegalArgumentException ignored) { }
        }
        for (PlayerListEntry e : mc.getNetworkHandler().getPlayerList()) {
            if (e.getProfile().getName().toLowerCase(Locale.ROOT).equals(m.name.toLowerCase(Locale.ROOT))) return e;
        }
        return null;
    }
}
