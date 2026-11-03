package dev.stella.client.data;

import dev.stella.client.StellaClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Checks whether staff with channel handles in staff.json are live, by loading
 * each platform's public channel page (no API keys or accounts):
 * <ul>
 *   <li>Twitch: {@code twitch.tv/<handle>} — live pages carry {@code "isLiveBroadcast":true}</li>
 *   <li>YouTube: {@code youtube.com/<@handle>/live} — {@code "isLiveNow":true}</li>
 *   <li>Kick: {@code kick.com/api/v2/channels/<handle>} — non-null {@code livestream}</li>
 *   <li>TikTok: {@code tiktok.com/@<handle>/live} — best effort, TikTok often blocks automated requests</li>
 * </ul>
 * Runs on a background daemon thread, one request every few seconds.
 */
public final class LiveChecker {
    public record Live(String platform, int color) {}

    private static final Map<String, Live> LIVE = new ConcurrentHashMap<>();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private static volatile boolean running;
    private static volatile int intervalSec = 180;
    private static volatile Consumer<String> onWentLive = n -> {};
    private static Thread thread;

    private LiveChecker() {}

    public static Live status(String staffName) { return LIVE.get(staffName.toLowerCase(Locale.ROOT)); }
    public static void setInterval(int seconds) { intervalSec = Math.max(60, seconds); }
    public static void setOnWentLive(Consumer<String> c) { onWentLive = c; }

    public static synchronized void start() {
        if (running) return;
        running = true;
        thread = new Thread(LiveChecker::loop, "Stella Live Check");
        thread.setDaemon(true);
        thread.start();
    }

    public static synchronized void stop() {
        running = false;
        if (thread != null) thread.interrupt();
        LIVE.clear();
    }

    private static void loop() {
        while (running) {
            long cycleStart = System.currentTimeMillis();
            for (StaffDatabase.StaffMember m : StaffDatabase.all()) {
                if (!running) return;
                if (!m.hasChannels()) continue;
                String key = m.name().toLowerCase(Locale.ROOT);
                Live found = null;
                try {
                    if (!m.twitch().isBlank() && body("https://www.twitch.tv/" + m.twitch()).contains("\"isLiveBroadcast\":true"))
                        found = new Live("Twitch", 0xFF9146FF);
                    else if (!m.youtube().isBlank() && body("https://www.youtube.com/" + yt(m.youtube()) + "/live").contains("\"isLiveNow\":true"))
                        found = new Live("YouTube", 0xFFFF3333);
                    else if (!m.kick().isBlank() && kickLive(body("https://kick.com/api/v2/channels/" + m.kick())))
                        found = new Live("Kick", 0xFF53FC18);
                    else if (!m.tiktok().isBlank() && body("https://www.tiktok.com/@" + m.tiktok().replace("@", "") + "/live").contains("\"status\":2"))
                        found = new Live("TikTok", 0xFF25F4EE);
                } catch (Exception e) {
                    StellaClient.LOGGER.debug("Live check failed for {}", m.name(), e);
                    continue; // keep the previous state on network errors
                }
                Live before = found == null ? LIVE.remove(key) : LIVE.put(key, found);
                if (found != null && before == null) onWentLive.accept(m.name() + " is live on " + found.platform());
                sleep(3000);
            }
            long wait = intervalSec * 1000L - (System.currentTimeMillis() - cycleStart);
            sleep(Math.max(5000, wait));
        }
    }

    /**
     * Live tag shown by the server itself in the tab list (rank text / team prefix),
     * e.g. "LIVE", "🔴" or "[TWITCH]". Returns null when nothing matches.
     */
    public static Live fromTab(net.minecraft.client.network.PlayerListEntry e, String liveKeywords) {
        if (e == null) return null;
        StringBuilder sb = new StringBuilder();
        if (e.getDisplayName() != null) sb.append(e.getDisplayName().getString()).append(' ');
        var team = e.getScoreboardTeam();
        if (team != null) sb.append(team.getPrefix().getString()).append(' ').append(team.getSuffix().getString());
        String t = sb.toString().toLowerCase(Locale.ROOT);
        boolean live = false;
        for (String k : liveKeywords.split(",")) {
            String kw = k.trim().toLowerCase(Locale.ROOT);
            if (!kw.isEmpty() && t.contains(kw)) { live = true; break; }
        }
        if (!live) return null;
        if (t.contains("twitch")) return new Live("Twitch", 0xFF9146FF);
        if (t.contains("youtube") || t.contains(" yt")) return new Live("YouTube", 0xFFFF3333);
        if (t.contains("kick")) return new Live("Kick", 0xFF53FC18);
        if (t.contains("tiktok")) return new Live("TikTok", 0xFF25F4EE);
        return new Live("Live", 0xFFFF3333);
    }

    private static String yt(String h) { return h.startsWith("@") || h.startsWith("channel/") ? h : "@" + h; }

    private static boolean kickLive(String json) {
        int i = json.indexOf("\"livestream\":");
        return i >= 0 && !json.startsWith("null", i + 13);
    }

    private static String body(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) StellaClient/1.0")
                .header("Accept-Language", "en-US,en;q=0.9").build();
        HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        return r.statusCode() / 100 == 2 ? r.body() : "";
    }

    private static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) { } }
}
