package dev.stella.client.modules.client;

import dev.stella.client.hud.HudEditorScreen;
import dev.stella.client.module.Category;
import dev.stella.client.module.Module;

/** Toggle (or press its keybind) to open the HUD editor. */
public class HudEditorModule extends Module {
    public HudEditorModule() { super("HUD Editor", "Drag, resize, preview and reset HUD elements", Category.CLIENT); }

    @Override public boolean keybindToggles() { return false; }
    @Override protected boolean showTogglePopup() { return false; }

    @Override
    protected void onEnable() {
        mc.setScreen(new HudEditorScreen(mc.currentScreen));
        setEnabled(false);
    }
}
