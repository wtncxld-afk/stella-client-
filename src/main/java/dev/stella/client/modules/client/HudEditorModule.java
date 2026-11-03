package dev.stella.client.modules.client;

import dev.stella.client.hud.HudEditorScreen;
import dev.stella.client.module.Category;
import dev.stella.client.module.Module;

/** Toggle (or press its keybind) to open the HUD editor. */
public class HudEditorModule extends Module {
    public final dev.stella.client.setting.BoolSetting snap = add(new dev.stella.client.setting.BoolSetting("Snap", "Snap elements to screen edges and centre", true));
    public final dev.stella.client.setting.NumberSetting snapDistance = add(new dev.stella.client.setting.NumberSetting("Snap Distance", "How close before snapping (px)", 4, 1, 12, 1)).visibleWhen(snap::isOn);
    public final dev.stella.client.setting.BoolSetting guides = add(new dev.stella.client.setting.BoolSetting("Centre Guides", "Show centre lines while editing", true));

    public HudEditorModule() { super("HUD Editor", "Drag, resize, preview and reset HUD elements", Category.CLIENT); }

    @Override public boolean keybindToggles() { return false; }
    @Override protected boolean showTogglePopup() { return false; }

    @Override
    protected void onEnable() {
        mc.setScreen(new HudEditorScreen(mc.currentScreen));
        setEnabled(false);
    }
}
