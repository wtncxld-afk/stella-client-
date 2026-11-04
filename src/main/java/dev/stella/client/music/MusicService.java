package dev.stella.client.music;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.stella.client.StellaClient;
import dev.stella.client.config.ConfigManager;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

/**
 * Reads "now playing" from the operating system — no Spotify account, API key
 * or login:
 * <ul>
 *   <li>Windows: Global System Media Transport Controls via a bundled PowerShell helper</li>
 *   <li>macOS: the Spotify app via AppleScript (osascript)</li>
 *   <li>Linux: MPRIS via the {@code playerctl} command</li>
 * </ul>
 * Runs on its own daemon thread only while the Music HUD is enabled.
 */
public final class MusicService {
    public enum Os { WINDOWS, MAC, LINUX, OTHER }

    private static volatile MediaInfo info = MediaInfo.NONE;
    private static volatile String status = "Starting...";
    private static volatile byte[] pendingCover; // PNG bytes waiting for upload on the render thread
    private static volatile boolean coverCleared;
    private static final ConcurrentLinkedQueue<String> COMMANDS = new ConcurrentLinkedQueue<>();
    private static volatile boolean running;
    private static volatile boolean spotifyOnly = true;
    private static volatile int intervalMs = 1000;
    private static Thread thread;
    private static String lastKey = "";

    // Windows helper process
    private static Process ps;
    private static Writer psIn;
    private static BufferedReader psOut;

    private MusicService() {}

    public static Os os() {
        String n = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (n.contains("win")) return Os.WINDOWS;
        if (n.contains("mac")) return Os.MAC;
        if (n.contains("linux") || n.contains("nix")) return Os.LINUX;
        return Os.OTHER;
    }

    public static MediaInfo info() { return info; }
    public static String status() { return status; }
    public static byte[] takeCover() { byte[] c = pendingCover; pendingCover = null; return c; }
    public static boolean takeCoverCleared() { boolean c = coverCleared; coverCleared = false; return c; }

    public static void configure(boolean onlySpotify, int pollMs) {
        spotifyOnly = onlySpotify;
        intervalMs = Math.max(250, pollMs);
    }

    public static void command(String cmd) { COMMANDS.add(cmd); if (thread != null) thread.interrupt(); }

    public static synchronized void start() {
        if (running) return;
        running = true;
        thread = new Thread(MusicService::loop, "Stella Music");
        thread.setDaemon(true);
        thread.start();
    }

    public static synchronized void stop() {
        running = false;
        if (thread != null) thread.interrupt();
        thread = null;
        stopPowerShell();
        info = MediaInfo.NONE;
    }

    private static void loop() {
        while (running) {
            try {
                String cmd = COMMANDS.poll();
                switch (os()) {
                    case WINDOWS -> pollWindows(cmd == null ? "poll" : cmd);
                    case MAC -> pollMac(cmd);
                    case LINUX -> pollLinux(cmd);
                    default -> status = "Music HUD is not supported on this OS";
                }
            } catch (Throwable t) {
                status = "Media error: " + t.getClass().getSimpleName();
                StellaClient.LOGGER.debug("Music poll failed", t);
            }
            if (!COMMANDS.isEmpty()) continue;
            try { Thread.sleep(intervalMs); } catch (InterruptedException ignored) { }
        }
    }

    // ------------------------------------------------------------------ Windows

    private static void ensurePowerShell() throws Exception {
        if (ps != null && ps.isAlive()) return;
        stopPowerShell();
        Path dir = ConfigManager.root().resolve("media");
        Files.createDirectories(dir);
        Path script = dir.resolve("bridge.ps1");
        try (InputStream in = MusicService.class.getResourceAsStream("/assets/stella/media/bridge.ps1")) {
            if (in == null) throw new IllegalStateException("bridge.ps1 missing from jar");
            Files.write(script, in.readAllBytes());
        }
        Path cover = dir.resolve("cover.img");
        ps = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden",
                "-ExecutionPolicy", "Bypass", "-File", script.toString(), cover.toString())
                .redirectErrorStream(false).start();
        psIn = new OutputStreamWriter(ps.getOutputStream(), StandardCharsets.UTF_8);
        psOut = new BufferedReader(new InputStreamReader(ps.getInputStream(), StandardCharsets.UTF_8));
        String ready = psOut.readLine(); // {"ready":true}
        if (ready == null) throw new IllegalStateException("PowerShell media bridge did not start");
        status = "";
    }

    private static void stopPowerShell() {
        if (ps != null) ps.destroy();
        ps = null;
        psIn = null;
        psOut = null;
    }

    private static void pollWindows(String cmd) throws Exception {
        ensurePowerShell();
        psIn.write(cmd + " " + (spotifyOnly ? "Spotify" : "") + "\n");
        psIn.flush();
        String line = psOut.readLine();
        if (line == null) { stopPowerShell(); status = "Media bridge stopped - retrying"; return; }
        JsonObject o = JsonParser.parseString(line).getAsJsonObject();
        if (!o.has("active") || !o.get("active").getAsBoolean()) {
            setNone(spotifyOnly ? "Spotify isn't playing anything" : "Nothing is playing");
            return;
        }
        String cover = o.has("cover") ? o.get("cover").getAsString() : "";
        if (!cover.isEmpty()) loadCover(Files.readAllBytes(Path.of(cover)));
        update(new MediaInfo(true, o.get("title").getAsString(), o.get("artist").getAsString(),
                "Playing".equalsIgnoreCase(o.get("status").getAsString()),
                o.get("position").getAsLong(), o.get("duration").getAsLong(),
                prettyApp(o.get("app").getAsString()), System.currentTimeMillis()), null);
    }

    private static String prettyApp(String id) {
        String l = id.toLowerCase(Locale.ROOT);
        if (l.contains("spotify")) return "Spotify";
        if (l.contains("chrome")) return "Chrome";
        if (l.contains("firefox")) return "Firefox";
        if (l.contains("edge")) return "Edge";
        return id;
    }

    // ------------------------------------------------------------------ macOS

    private static final String MAC_QUERY = """
            if application "Spotify" is running then
              tell application "Spotify"
                if player state is stopped then return "STOPPED"
                set t to current track
                return (player state as string) & "\t" & (name of t) & "\t" & (artist of t) & "\t" & (duration of t) & "\t" & (player position as string) & "\t" & (artwork url of t)
              end tell
            else
              return "NOTRUNNING"
            end if""";

    private static void pollMac(String cmd) throws Exception {
        if (cmd != null) {
            String verb = switch (cmd) { case "toggle" -> "playpause"; case "next" -> "next track"; default -> "previous track"; };
            run(3, "osascript", "-e", "tell application \"Spotify\" to " + verb);
        }
        String out = run(3, "osascript", "-e", MAC_QUERY).trim();
        if (out.equals("NOTRUNNING")) { setNone("Open Spotify to see what's playing"); return; }
        if (out.equals("STOPPED") || out.isEmpty()) { setNone("Spotify isn't playing anything"); return; }
        String[] f = out.split("\t", -1);
        if (f.length < 6) { setNone("Spotify returned no track"); return; }
        long pos = (long) (Double.parseDouble(f[4].replace(',', '.')) * 1000);
        update(new MediaInfo(true, f[1], f[2], f[0].equalsIgnoreCase("playing"), pos, Long.parseLong(f[3].trim()),
                "Spotify", System.currentTimeMillis()), f[5]);
    }

    // ------------------------------------------------------------------ Linux

    private static void pollLinux(String cmd) throws Exception {
        String player = spotifyOnly ? "spotify" : "%any";
        if (cmd != null) {
            String verb = switch (cmd) { case "toggle" -> "play-pause"; case "next" -> "next"; default -> "previous"; };
            run(3, "playerctl", "-p", player, verb);
        }
        String out;
        try {
            out = run(3, "playerctl", "-p", player, "metadata", "--format",
                    "{{status}}\t{{xesam:title}}\t{{xesam:artist}}\t{{mpris:length}}\t{{position}}\t{{mpris:artUrl}}\t{{playerName}}").trim();
        } catch (java.io.IOException e) {
            setNone("Install 'playerctl' to use the Music HUD on Linux");
            return;
        }
        String[] f = out.split("\t", -1);
        if (out.isEmpty() || f.length < 7) { setNone(spotifyOnly ? "Spotify isn't playing anything" : "Nothing is playing"); return; }
        long dur = f[3].isBlank() ? 0 : Long.parseLong(f[3].trim()) / 1000;
        long pos = f[4].isBlank() ? 0 : Long.parseLong(f[4].trim()) / 1000;
        update(new MediaInfo(true, f[1], f[2], f[0].equalsIgnoreCase("Playing"), pos, dur, f[6], System.currentTimeMillis()), f[5]);
    }

    // ------------------------------------------------------------------ shared

    private static void setNone(String why) {
        info = MediaInfo.NONE;
        status = why;
        if (!lastKey.isEmpty()) { lastKey = ""; coverCleared = true; }
    }

    private static void update(MediaInfo i, String artUrl) {
        status = "";
        String key = i.title() + "|" + i.artist();
        if (!key.equals(lastKey)) {
            lastKey = key;
            if (artUrl != null) {
                if (artUrl.isBlank()) coverCleared = true;
                else {
                    try { loadCover(fetch(artUrl)); } catch (Exception e) { coverCleared = true; }
                }
            }
        }
        info = i;
    }

    /** One shared client (each HttpClient owns threads, so never create one per cover). */
    private static final HttpClient COVER_HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();

    private static byte[] fetch(String url) throws Exception {
        if (url.startsWith("file://")) return Files.readAllBytes(Path.of(URI.create(url)));
        HttpResponse<byte[]> r = COVER_HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(6)).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        return r.body();
    }

    /** Decodes any image (JPEG/PNG/BMP) and re-encodes a 128x128 PNG for Minecraft's NativeImage. */
    private static void loadCover(byte[] bytes) throws Exception {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(bytes));
        if (src == null) { coverCleared = true; return; }
        BufferedImage dst = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        int s = Math.min(src.getWidth(), src.getHeight());
        g.drawImage(src, 0, 0, 128, 128, (src.getWidth() - s) / 2, (src.getHeight() - s) / 2,
                (src.getWidth() + s) / 2, (src.getHeight() + s) / 2, null);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(dst, "png", out);
        pendingCover = out.toByteArray();
    }

    private static String run(int timeoutSec, String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        byte[] out = p.getInputStream().readAllBytes();
        if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) p.destroyForcibly();
        return new String(out, StandardCharsets.UTF_8);
    }
}
