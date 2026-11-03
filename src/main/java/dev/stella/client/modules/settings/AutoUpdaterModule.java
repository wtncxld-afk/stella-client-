package dev.stella.client.modules.settings;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.notification.Notification;
import dev.stella.client.notification.NotificationManager;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.TextSetting;
import dev.stella.client.update.AutoUpdater;

/**
 * Auto Updater: checks this mod's GitHub Releases on startup, downloads a newer
 * build, verifies its SHA-256 and installs it when Minecraft closes.
 */
public class AutoUpdaterModule extends Module {
    public static final String DEFAULT_SOURCE = "wtncxld-afk/stella-client-";

    public final BoolSetting checkOnStartup = add(new BoolSetting("Check On Startup", "Look for a new Stella release when the game starts", true));
    public final BoolSetting autoDownload = add(new BoolSetting("Auto Download", "Download and verify new releases automatically", true));
    public final BoolSetting installOnExit = add(new BoolSetting("Install On Exit", "Swap in the downloaded jar after Minecraft closes", true))
            .onChange(() -> AutoUpdater.setInstallOnExit(this.installOnExit.isOn()));
    public final TextSetting source = add(new TextSetting("Update Source", "GitHub owner/repo whose releases are checked", DEFAULT_SOURCE, 100));
    private final BoolSetting checkNow = add(new BoolSetting("Check Now", "Toggle to check for an update now", false)).onChange(this::checkNow);
    private final BoolSetting showStatus = add(new BoolSetting("Show Status", "Toggle to show the updater's last result", false)).onChange(this::status);

    public AutoUpdaterModule() {
        super("Auto Updater", "Keeps Stella up to date from GitHub Releases (SHA-256 verified)", Category.SETTINGS, true);
    }

    @Override protected boolean showTogglePopup() { return false; }

    /** Called once the client has started and the profile is loaded. */
    public void startup() {
        AutoUpdater.setInstallOnExit(installOnExit.isOn());
        if (isEnabled() && checkOnStartup.isOn() && !Boolean.getBoolean("stella.smokeTest")) {
            AutoUpdater.checkAsync(source.get().trim(), autoDownload.isOn(), false);
        }
    }

    private void checkNow() {
        if (!checkNow.isOn()) return;
        checkNow.set(false);
        NotificationManager.post("Updater", "Checking for updates...", Notification.Type.INFO, 2000);
        AutoUpdater.checkAsync(source.get().trim(), autoDownload.isOn(), true);
    }

    private void status() {
        if (!showStatus.isOn()) return;
        showStatus.set(false);
        String m = AutoUpdater.message();
        NotificationManager.post("Updater", m == null || m.isEmpty() ? "No check has run yet" : m, Notification.Type.INFO, 5000);
    }
}
