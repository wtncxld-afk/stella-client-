package dev.stella.client.modules.client;

import dev.stella.client.gui.ClickGuiScreen;
import dev.stella.client.gui.theme.Theme;
import dev.stella.client.gui.theme.ThemeManager;
import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.ColorSetting;
import dev.stella.client.setting.ModeSetting;
import dev.stella.client.setting.NumberSetting;
import org.lwjgl.glfw.GLFW;

/** Owns the GUI open key (Right Shift by default), the theme and UI sounds. */
public class ClickGuiModule extends Module {
    public final ModeSetting theme = add(new ModeSetting("Theme", "Colour theme", "Stella", Theme.names())).onChange(this::applyTheme);
    public final ColorSetting customAccent = add(new ColorSetting("Custom Accent", "Accent used by the Custom theme", 0xFFA855F7))
            .visibleWhen(() -> this.theme.is(Theme.CUSTOM)).onChange(this::applyTheme);
    public final dev.stella.client.setting.NumberSetting guiSize = add(new dev.stella.client.setting.NumberSetting("GUI Size",
            "Size of the ClickGUI (1.0 = fit the window automatically)", 1.0, 0.6, 1.4, 0.05));
    public final BoolSetting blur = add(new BoolSetting("Blur Background", "Blur the game behind the GUI (uses the vanilla Menu Background Blurriness option)", true));
    public final BoolSetting descriptions = add(new BoolSetting("Descriptions", "Show module descriptions on hover", true));
    public final BoolSetting customFont = add(new BoolSetting("Custom Font", "Use the Stella (TNH Xuong) font for the GUI and HUD", true))
            .onChange(() -> dev.stella.client.render.StellaFont.setEnabled(this.customFont.isOn()));
    public final BoolSetting uppercase = add(new BoolSetting("Uppercase Names", "Compact uppercase module names", true));

    // ---- UI sounds
    public final NumberSetting volume = add(new NumberSetting("Master Volume", "Volume of Stella UI sounds", 0.6, 0, 1, 0.05));
    public final BoolSetting soundOpen = add(new BoolSetting("GUI Open/Close", "Sound when opening and closing", true));
    public final BoolSetting soundHover = add(new BoolSetting("Hover", "Soft tick when hovering modules", false));
    public final BoolSetting soundClick = add(new BoolSetting("Clicks & Toggles", "Sound on clicks and toggles", true));
    public final BoolSetting soundNotify = add(new BoolSetting("Notifications", "Sound on module alerts", true));

    public ClickGuiModule() {
        super("ClickGUI", "Opens this menu. Change the keybind to change the GUI key", Category.CLIENT);
        keybind.set(GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    public void applyTheme() {
        ThemeManager.select(theme.get(), customAccent.argb());
        dev.stella.client.render.StellaFont.setEnabled(customFont.isOn());
    }

    @Override public boolean keybindToggles() { return false; }

    public void open() {
        if (mc.currentScreen == null) mc.setScreen(new ClickGuiScreen());
    }

    @Override
    public void resetSettings() {
        super.resetSettings();
        keybind.set(GLFW.GLFW_KEY_RIGHT_SHIFT);
        applyTheme();
    }
}
