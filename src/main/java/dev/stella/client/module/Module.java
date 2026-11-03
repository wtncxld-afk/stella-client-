package dev.stella.client.module;

import dev.stella.client.render.Render3D;
import dev.stella.client.setting.KeySetting;
import dev.stella.client.setting.Setting;
import dev.stella.client.setting.SettingsDirty;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A toggleable feature. Each module owns its settings; nothing is shared between
 * modules so disabling one can never affect another.
 */
public abstract class Module {
    protected static final MinecraftClient mc = MinecraftClient.getInstance();

    private final String name;
    private final String description;
    private final Category category;
    private final List<Setting<?>> settings = new ArrayList<>();
    private boolean enabled;
    private final boolean defaultEnabled;
    public final KeySetting keybind;

    protected Module(String name, String description, Category category) {
        this(name, description, category, false);
    }

    protected Module(String name, String description, Category category, boolean defaultEnabled) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.defaultEnabled = defaultEnabled;
        this.keybind = new KeySetting("Keybind", "Key that toggles this module", GLFW.GLFW_KEY_UNKNOWN);
    }

    protected <S extends Setting<?>> S add(S setting) {
        settings.add(setting);
        return setting;
    }

    /** Called once after all modules are constructed. */
    public void finishSetup() {
        settings.add(keybind);
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public Category getCategory() { return category; }
    public List<Setting<?>> getSettings() { return Collections.unmodifiableList(settings); }
    public boolean isEnabled() { return enabled; }
    public boolean isDefaultEnabled() { return defaultEnabled; }

    /** Whether pressing the keybind should toggle (false: e.g. Zoom handles hold itself). */
    public boolean keybindToggles() { return true; }

    public void setEnabled(boolean value) {
        if (value == enabled) return;
        enabled = value;
        SettingsDirty.mark();
        try {
            if (value) onEnable(); else onDisable();
        } catch (Throwable t) {
            dev.stella.client.StellaClient.reportError((value ? "enable " : "disable ") + name, t);
        }
    }

    /** Restores state without firing callbacks loudly (used by config load). */
    public void loadEnabled(boolean value) { setEnabled(value); }

    public void toggle() { setEnabled(!enabled); }

    public void resetSettings() {
        for (Setting<?> s : settings) s.reset();
    }

    protected void onEnable() {}
    protected void onDisable() {}
    public void onTick() {}
    public void onRender3D(Render3D r) {}
    /** 2D overlay drawn in world-space-projected coordinates (labels, tracers). */
    public void onRenderOverlay(DrawContext ctx, float tickDelta) {}

    /** Height of an optional live preview drawn at the top of this module's settings window (0 = none). */
    public int previewHeight() { return 0; }
    public void renderPreview(DrawContext ctx, int x, int y, int w, int h) {}

    protected boolean inGame() { return mc.player != null && mc.world != null; }
}
