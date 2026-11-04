package dev.stella.client.modules.client;

import dev.stella.client.config.ConfigManager;
import dev.stella.client.gui.ScreenshotGallery;
import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.notification.Notification;
import dev.stella.client.notification.NotificationManager;
import dev.stella.client.setting.BoolSetting;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Screenshot gallery and config share codes (copy your profile as a code, import a friend's). */
public class ClientTools extends Module {
    private final BoolSetting gallery = add(new BoolSetting("Open Screenshot Gallery", "Toggle to open your screenshots", false)).onChange(this::openGallery);
    private final BoolSetting copyCode = add(new BoolSetting("Copy Share Code", "Toggle to copy your current profile as a share code", false)).onChange(this::copyCode);
    private final BoolSetting importCode = add(new BoolSetting("Import Code From Clipboard", "Toggle to import a share code from your clipboard as a new profile", false)).onChange(this::importCode);

    public static final String PREFIX = "STELLA1:";

    public ClientTools() { super("Client Tools", "Screenshot gallery and profile share codes", Category.CLIENT, true); }

    @Override protected boolean showTogglePopup() { return false; }

    private void openGallery() {
        if (!gallery.isOn()) return;
        gallery.set(false);
        mc.send(() -> mc.setScreen(new ScreenshotGallery()));
    }

    /** Current profile as a share code: STELLA1: + base64(gzip(json)). */
    public static String shareCode() throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(bo)) { gz.write(ConfigManager.exportString().getBytes(StandardCharsets.UTF_8)); }
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bo.toByteArray());
    }

    /** Imports a share code as a new profile and switches to it; returns the profile name. */
    public static String importShareCode(String code) throws Exception {
        String c = code.trim();
        if (!c.startsWith(PREFIX)) throw new IllegalArgumentException("Not a Stella share code");
        byte[] gz = Base64.getUrlDecoder().decode(c.substring(PREFIX.length()));
        String json;
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(gz))) { json = new String(in.readAllBytes(), StandardCharsets.UTF_8); }
        String name = ConfigManager.importString(json, "shared");
        ConfigManager.switchProfile(name);
        return name;
    }

    private void copyCode() {
        if (!copyCode.isOn()) return;
        copyCode.set(false);
        try {
            String code = shareCode();
            mc.keyboard.setClipboard(code);
            NotificationManager.post("Share Code", "Copied (" + code.length() + " characters) - send it to a friend", Notification.Type.SUCCESS, 3000);
        } catch (Exception e) { NotificationManager.post("Share Code", "Failed: " + e.getMessage(), Notification.Type.ALERT, 3000); }
    }

    private void importCode() {
        if (!importCode.isOn()) return;
        importCode.set(false);
        try {
            String name = importShareCode(mc.keyboard.getClipboard());
            NotificationManager.post("Share Code", "Imported as profile '" + name + "'", Notification.Type.SUCCESS, 3000);
        } catch (Exception e) { NotificationManager.post("Share Code", "Import failed: " + e.getMessage(), Notification.Type.ALERT, 3000); }
    }
}
