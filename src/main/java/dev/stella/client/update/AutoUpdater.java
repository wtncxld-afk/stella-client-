package dev.stella.client.update;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.stella.client.StellaClient;
import dev.stella.client.notification.Notification;
import dev.stella.client.notification.NotificationManager;
import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipFile;

/**
 * Checks GitHub Releases for a newer Stella build, downloads it, verifies its
 * SHA-256 and that it really is the Stella mod, then installs it when the game
 * closes (Fabric refuses to start with two copies of the same mod, and Windows
 * locks the running jar, so the swap must happen after exit).
 * <p>
 * What it does on the network: one GET to {@code api.github.com/repos/<source>/releases/latest}
 * and, only if an update exists, downloads the jar and its {@code .sha256} file from
 * that release. Nothing is uploaded. Downloads are only accepted from github.com hosts.
 */
public final class AutoUpdater {
    public enum State { IDLE, CHECKING, UP_TO_DATE, DOWNLOADING, READY, FAILED, DISABLED }

    private static volatile State state = State.IDLE;
    private static volatile String latestVersion;
    private static volatile String message = "";
    private static volatile Path stagedJar;
    private static volatile boolean installOnExit = true;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private AutoUpdater() {}

    public static State state() { return state; }
    public static String message() { return message; }
    public static String latestVersion() { return latestVersion; }

    /** Short status for the main menu, or null when there is nothing to say. */
    public static String statusLine() {
        return switch (state) {
            case DOWNLOADING -> "Downloading Stella " + latestVersion + "...";
            case READY -> installOnExit ? "Stella " + latestVersion + " installs when you close the game" : "Stella " + latestVersion + " downloaded";
            default -> null;
        };
    }

    public static Path stagingDir() { return FabricLoader.getInstance().getGameDir().resolve(".stella-update"); }

    /** The installed Stella jar, or null in a development environment. */
    public static Path installedJar() {
        return FabricLoader.getInstance().getModContainer(StellaClient.MOD_ID)
                .flatMap(c -> c.getOrigin().getPaths().stream().findFirst())
                .filter(p -> String.valueOf(p.getFileName()).endsWith(".jar") && Files.isRegularFile(p))
                .orElse(null);
    }

    public static void setInstallOnExit(boolean on) { installOnExit = on; }

    /** Starts a background check (and download when {@code autoDownload}). Safe to call repeatedly. */
    public static synchronized void checkAsync(String source, boolean autoDownload, boolean notifyUpToDate) {
        if (state == State.CHECKING || state == State.DOWNLOADING || state == State.READY) return;
        if (dev.stella.client.Brand.ISRAELI) {
            // this repository's releases are Stella builds: never "update" Israeli Client into Stella
            message = "Auto-update is off in Israeli Client - new versions come as new jars";
            return;
        }
        if (!source.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
            fail("Invalid update source '" + source + "' (expected owner/repo)");
            return;
        }
        state = State.CHECKING;
        Thread t = new Thread(() -> {
            try {
                check(source, autoDownload, notifyUpToDate);
            } catch (Exception e) {
                fail("Update check failed: " + e.getMessage());
                StellaClient.LOGGER.warn("Stella update check failed", e);
            }
        }, "Stella-Updater");
        t.setDaemon(true);
        t.start();
    }

    private static void check(String source, boolean autoDownload, boolean notifyUpToDate) throws Exception {
        cleanStaging();
        HttpResponse<String> res = HTTP.send(request("https://api.github.com/repos/" + source + "/releases/latest")
                .header("Accept", "application/vnd.github+json").build(), HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 404) {
            fail("No public releases found at " + source + " (is the repository public?)");
            return;
        }
        if (res.statusCode() != 200) { fail("GitHub returned HTTP " + res.statusCode()); return; }

        JsonObject rel = JsonParser.parseString(res.body()).getAsJsonObject();
        String tag = rel.get("tag_name").getAsString();
        String version = tag.startsWith("v") ? tag.substring(1) : tag;
        latestVersion = version;
        if (compare(version, StellaClient.VERSION) <= 0) {
            state = State.UP_TO_DATE;
            message = "Stella " + StellaClient.VERSION + " is up to date";
            if (notifyUpToDate) NotificationManager.post("Updater", message, Notification.Type.SUCCESS, 3000);
            return;
        }

        String jarUrl = null, shaUrl = null, digest = null, jarName = null;
        for (JsonElement e : rel.getAsJsonArray("assets")) {
            JsonObject a = e.getAsJsonObject();
            String name = a.get("name").getAsString();
            String url = a.get("browser_download_url").getAsString();
            if (name.endsWith(".jar") && !name.contains("-sources")) {
                jarUrl = url; jarName = name;
                if (a.has("digest") && !a.get("digest").isJsonNull()) digest = a.get("digest").getAsString();
            } else if (name.endsWith(".jar.sha256") && !name.contains("-sources")) {
                shaUrl = url;
            }
        }
        if (jarUrl == null || !jarName.matches("[A-Za-z0-9_.+-]+\\.jar")) { fail("Release " + tag + " has no jar"); return; }

        String expected = null;
        if (digest != null && digest.startsWith("sha256:")) expected = digest.substring(7);
        else if (shaUrl != null) {
            HttpResponse<String> s = HTTP.send(request(shaUrl).build(), HttpResponse.BodyHandlers.ofString());
            checkHost(s.uri());
            if (s.statusCode() == 200) expected = s.body().trim().split("\\s+")[0];
        }
        if (expected == null || !expected.matches("[0-9a-fA-F]{64}")) { fail("Release " + tag + " has no SHA-256, refusing to install"); return; }

        NotificationManager.post("Updater", "Stella " + version + " is available", Notification.Type.INFO, 4000);
        if (!autoDownload) {
            state = State.IDLE;
            message = "Stella " + version + " is available - use Check Now with Auto Download on to get it";
            return;
        }

        state = State.DOWNLOADING;
        Path dir = stagingDir();
        Files.createDirectories(dir);
        Path part = dir.resolve(jarName + ".part");
        HttpResponse<InputStream> d = HTTP.send(request(jarUrl).build(), HttpResponse.BodyHandlers.ofInputStream());
        checkHost(d.uri());
        if (d.statusCode() != 200) { fail("Download failed: HTTP " + d.statusCode()); return; }
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        long size = 0;
        try (InputStream in = d.body(); var out = Files.newOutputStream(part)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) {
                size += n;
                if (size > 64L * 1024 * 1024) throw new IllegalStateException("download too large");
                md.update(buf, 0, n);
                out.write(buf, 0, n);
            }
        }
        String actual = HexFormat.of().formatHex(md.digest());
        if (!actual.equalsIgnoreCase(expected)) {
            Files.deleteIfExists(part);
            fail("Downloaded jar failed SHA-256 verification - discarded");
            return;
        }
        if (!isStellaJar(part)) {
            Files.deleteIfExists(part);
            fail("Downloaded file is not a Stella jar - discarded");
            return;
        }
        Path staged = dir.resolve(jarName);
        Files.move(part, staged, StandardCopyOption.REPLACE_EXISTING);
        stagedJar = staged;
        state = State.READY;
        message = "Stella " + version + " downloaded and verified - it installs when you close Minecraft";
        NotificationManager.post("Updater", message, Notification.Type.SUCCESS, 6000);
        StellaClient.LOGGER.info("Stella update {} staged at {} (sha256 {})", version, staged, actual);
    }

    /** Called from CLIENT_STOPPING: hands the verified jar to the installer helper. */
    public static void installOnShutdown() {
        Path staged = stagedJar;
        if (state != State.READY || staged == null || !installOnExit) return;
        Path old = installedJar();
        if (old == null) { StellaClient.LOGGER.info("Stella update not installed: running from a development environment"); return; }
        try {
            Path target = old.resolveSibling(String.valueOf(staged.getFileName()));
            String java = ProcessHandle.current().info().command().orElse("java");
            new ProcessBuilder(List.of(java, "-cp", staged.toString(), UpdateInstaller.class.getName(),
                    Long.toString(ProcessHandle.current().pid()), old.toString(), staged.toString(), target.toString()))
                    .redirectErrorStream(true)
                    .redirectOutput(stagingDir().resolve("installer.log").toFile())
                    .start();
            StellaClient.LOGGER.info("Stella update {} will replace {} after exit", latestVersion, old.getFileName());
        } catch (Exception e) {
            StellaClient.LOGGER.warn("Could not start the Stella update installer", e);
        }
    }

    private static boolean isStellaJar(Path jar) {
        try (ZipFile z = new ZipFile(jar.toFile())) {
            var entry = z.getEntry("fabric.mod.json");
            if (entry == null || z.getEntry(UpdateInstaller.class.getName().replace('.', '/') + ".class") == null) return false;
            try (InputStream in = z.getInputStream(entry)) {
                JsonObject o = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                return StellaClient.MOD_ID.equals(o.get("id").getAsString());
            }
        } catch (Exception e) {
            return false;
        }
    }

    /** Removes leftovers from earlier updates (never touches the mods folder). */
    private static void cleanStaging() {
        Path dir = stagingDir();
        if (!Files.isDirectory(dir)) return;
        try (var files = Files.list(dir)) {
            files.filter(p -> {
                String n = String.valueOf(p.getFileName());
                return n.endsWith(".jar") || n.endsWith(".part");
            }).forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) { } });
        } catch (Exception ignored) { }
    }

    private static void checkHost(URI uri) {
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!"https".equals(uri.getScheme()) || !(host.equals("github.com") || host.endsWith(".github.com") || host.endsWith(".githubusercontent.com"))) {
            throw new IllegalStateException("refusing download from " + host);
        }
    }

    private static HttpRequest.Builder request(String url) {
        URI uri = URI.create(url);
        checkHost(uri);
        return HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60))
                .header("User-Agent", "StellaClient-Updater/" + StellaClient.VERSION);
    }

    private static void fail(String msg) {
        state = State.FAILED;
        message = msg;
        StellaClient.LOGGER.warn("Stella updater: {}", msg);
    }

    /** Compares dotted numeric versions ("1.0.12" &gt; "1.0.9"); non-numeric parts count as 0. */
    static int compare(String a, String b) {
        String[] x = a.split("[.+-]"), y = b.split("[.+-]");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? num(x[i]) : 0, q = i < y.length ? num(y[i]) : 0;
            if (p != q) return Integer.compare(p, q);
        }
        return 0;
    }

    private static int num(String s) {
        try { return Integer.parseInt(s.replaceAll("\\D", "")); } catch (Exception e) { return 0; }
    }
}
