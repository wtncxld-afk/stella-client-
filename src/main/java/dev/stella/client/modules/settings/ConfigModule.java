package dev.stella.client.modules.settings;

import dev.stella.client.config.ConfigManager;
import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.notification.Notification;
import dev.stella.client.notification.NotificationManager;
import dev.stella.client.setting.BoolSetting;

public class ConfigModule extends Module {
    public final BoolSetting autoSave = add(new BoolSetting("Auto Save", "Save changes automatically every few seconds", true));
    public final dev.stella.client.setting.NumberSetting interval = add(new dev.stella.client.setting.NumberSetting("Auto Save Interval", "Seconds between auto-save checks", 5, 1, 120, 1).suffix("s"));
    public final BoolSetting saveOnClose = add(new BoolSetting("Save On GUI Close", "Save immediately when the ClickGUI closes", true));
    private final BoolSetting saveNow = add(new BoolSetting("Save Now", "Toggle to save the active profile", false)).onChange(this::save);

    public ConfigModule() { super("Config", "Profile auto-save and manual save (profiles live in the right-hand panel)", Category.SETTINGS, true); }

    private void save() {
        if (!saveNow.isOn()) return;
        saveNow.set(false);
        ConfigManager.save();
        NotificationManager.post("Config", "Saved profile '" + ConfigManager.activeProfile() + "'", Notification.Type.SUCCESS, 2000);
    }
}
