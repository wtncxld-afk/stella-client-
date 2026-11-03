package dev.stella.client.modules.client;

import dev.stella.client.gui.ClickGuiScreen;
import dev.stella.client.gui.theme.Theme;
import dev.stella.client.gui.theme.ThemeManager;
import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.ModeSetting;
import org.lwjgl.glfw.GLFW;

/** Owns the GUI open key (Right Shift by default) and the theme. */
public class ClickGuiModule extends Module {
    public final ModeSetting theme = add(new ModeSetting("Theme", "Colour theme", "Stella", Theme.names()))
            .onChange(() -> ThemeManager.set(Theme.byName(this.theme.get())));
    public final BoolSetting descriptions = add(new BoolSetting("Descriptions", "Show module descriptions on hover", true));
    public final BoolSetting dimBackground = add(new BoolSetting("Dim Background", "Darken the game behind the GUI", true));

    public ClickGuiModule() {
        super("ClickGUI", "Opens this menu. Change the keybind to change the GUI key", Category.CLIENT);
        keybind.set(GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    @Override public boolean keybindToggles() { return false; }

    public void open() {
        if (mc.currentScreen == null) mc.setScreen(new ClickGuiScreen());
    }

    @Override
    public void resetSettings() {
        super.resetSettings();
        keybind.set(GLFW.GLFW_KEY_RIGHT_SHIFT);
        ThemeManager.set(Theme.byName(theme.get()));
    }
}
