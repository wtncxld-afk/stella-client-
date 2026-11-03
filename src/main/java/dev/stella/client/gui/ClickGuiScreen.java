package dev.stella.client.gui;

import dev.stella.client.gui.theme.Theme;
import dev.stella.client.gui.theme.ThemeManager;
import dev.stella.client.gui.widget.TextInput;
import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.module.ModuleManager;
import dev.stella.client.modules.client.ClickGuiModule;
import dev.stella.client.setting.SettingsDirty;
import dev.stella.client.util.Animation;
import dev.stella.client.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Compact multi-panel ClickGUI: narrow category columns across the top, a
 * search bar, floating per-module settings windows and a theme/profile panel
 * on the right. Right Shift (configurable) opens and closes it.
 */
public class ClickGuiScreen extends Screen {
    private final List<CategoryPanel> panels = new ArrayList<>();
    private final List<SettingsWindow> windows = new ArrayList<>();
    private final SidePanel side = new SidePanel();
    private final TextInput search = new TextInput("", 32, "Search modules...  (Ctrl+F)");
    private final Animation open = new Animation(0, 12).target(1);
    private Module hovered;
    private boolean closing;

    public ClickGuiScreen() {
        super(Text.literal("Stella ClickGUI"));
        search.onChange(q -> panels.forEach(p -> p.filter(q)));
    }

    @Override
    protected void init() {
        if (panels.isEmpty()) {
            int x = 6, y = 30;
            int usable = width - SidePanel.WIDTH - 16;
            for (Category c : Category.values()) {
                if (x + CategoryPanel.WIDTH > usable) { x = 6; y += 140; }
                panels.add(new CategoryPanel(c, x, y));
                x += CategoryPanel.WIDTH + 5;
            }
            panels.forEach(p -> p.filter(""));
        }
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Custom dim instead of vanilla blur (keeps the GUI crisp and cheap).
        if (ModuleManager.get(ClickGuiModule.class).dimBackground.isOn()) {
            int a = (int) (0x90 * open.get());
            ctx.fillGradient(0, 0, width, height, (a << 24) | 0x05030A, (a << 24) | 0x140A24);
        }
    }

    public void setHovered(Module m) { this.hovered = m; }
    public boolean isSettingsOpen(Module m) { return windows.stream().anyMatch(w -> w.module == m); }

    public void toggleSettings(Module m, int mx, int my) {
        for (SettingsWindow w : windows) {
            if (w.module == m) { windows.remove(w); return; }
        }
        int wx = mx + 12;
        if (wx + SettingsWindow.WIDTH > width - SidePanel.WIDTH - 8) wx = mx - SettingsWindow.WIDTH - 12;
        windows.add(new SettingsWindow(m, wx, Math.max(4, my - 20)));
        if (windows.size() > 4) windows.remove(0);
    }

    /** Used by the CI smoke test. */
    public void openSettingsFor(String moduleName) {
        for (Module m : ModuleManager.all()) if (m.getName().equals(moduleName) && !isSettingsOpen(m)) toggleSettings(m, 110 + windows.size() * (SettingsWindow.WIDTH + 8), 70);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        Theme t = ThemeManager.get();
        double a = open.get();
        if (closing && a < 0.05) { super.close(); return; }
        int slide = (int) ((1 - a) * -12);
        hovered = null;

        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, slide, 0);
        // search bar
        int sw = 180, sx = (width - SidePanel.WIDTH) / 2 - sw / 2, sy = 8;
        search.render(ctx, sx, sy, sw, 14, ColorUtil.mulAlpha(t.background(), a), t.accent(), ColorUtil.mulAlpha(t.text(), a), ColorUtil.mulAlpha(t.textDim(), a));
        boolean searching = !search.get().isBlank();
        for (CategoryPanel p : panels) {
            if (searching && !p.hasMatches()) continue;
            p.render(ctx, mx, my - slide, height, a, searching, this);
        }
        for (CategoryPanel p : panels) if (searching && !p.hasMatches()) p.render(ctx, mx, my - slide, height, a, true, this);
        ctx.getMatrices().pop();

        side.render(ctx, mx, my, width, height, a);
        windows.removeIf(SettingsWindow::isClosed);
        for (SettingsWindow w : windows) w.render(ctx, mx, my, width, height);

        // hover description
        if (hovered != null && windows.stream().noneMatch(w -> w.isOver(mx, my, height))
                && ModuleManager.get(ClickGuiModule.class).descriptions.isOn()) {
            String d = hovered.getDescription();
            List<String> lines = SettingsWindow.wrap(d, 170);
            int w = 0;
            for (String l : lines) w = Math.max(w, GuiDraw.width(l));
            int tx = mx + 10, ty = my + 10;
            if (tx + w + 8 > width) tx = mx - w - 14;
            ctx.getMatrices().push();
            ctx.getMatrices().translate(0, 0, 400);
            ctx.fill(tx - 3, ty - 3, tx + w + 3, ty + lines.size() * 9 + 1, 0xF0100D16);
            ctx.drawBorder(tx - 4, ty - 4, w + 8, lines.size() * 9 + 6, t.outline());
            for (int i = 0; i < lines.size(); i++) GuiDraw.text(ctx, lines.get(i), tx, ty + i * 9, t.text());
            ctx.getMatrices().pop();
        }
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int i = windows.size() - 1; i >= 0; i--) {
            SettingsWindow w = windows.get(i);
            if (w.mouseClicked(mx, my, button, height)) {
                windows.remove(w);
                if (!w.isClosed()) windows.add(w); // bring to front
                return true;
            }
        }
        TextInput.unfocusAll();
        if (side.mouseClicked(mx, my, width, height)) return true;
        int sw = 180, sx = (width - SidePanel.WIDTH) / 2 - sw / 2;
        if (GuiDraw.in(mx, my, sx, 8, sw, 14)) {
            if (button == 1) { search.set(""); panels.forEach(p -> p.filter("")); }
            search.focus();
            return true;
        }
        for (int i = panels.size() - 1; i >= 0; i--) {
            if (panels.get(i).mouseClicked(mx, my, button, height, this)) {
                CategoryPanel p = panels.remove(i);
                panels.add(p); // dragged panel renders on top
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        for (SettingsWindow w : windows) if (w.mouseDragged(mx, my)) return true;
        for (CategoryPanel p : panels) if (p.mouseDragged(mx, my)) return true;
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        windows.forEach(SettingsWindow::mouseReleased);
        panels.forEach(CategoryPanel::mouseReleased);
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        for (int i = windows.size() - 1; i >= 0; i--) if (windows.get(i).mouseScrolled(mx, my, v, height)) return true;
        if (side.mouseScrolled(mx, my, v, width, height)) return true;
        for (int i = panels.size() - 1; i >= 0; i--) if (panels.get(i).mouseScrolled(mx, my, v, height)) return true;
        return super.mouseScrolled(mx, my, h, v);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        for (SettingsWindow w : windows) if (w.keyPressed(key)) return true;
        if (side.keyPressed(key)) return true;
        TextInput f = TextInput.focused();
        if (f != null && f.keyPressed(key, mods)) return true;
        if (key == GLFW.GLFW_KEY_F && (mods & GLFW.GLFW_MOD_CONTROL) != 0) { search.focus(); return true; }
        ClickGuiModule gui = ModuleManager.get(ClickGuiModule.class);
        if (key == GLFW.GLFW_KEY_ESCAPE || (gui.keybind.isBound() && key == gui.keybind.key())) {
            close();
            return true;
        }
        // typing anywhere starts a search
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (side.charTyped(c)) return true;
        TextInput f = TextInput.focused();
        if (f != null) return f.charTyped(c);
        if (Character.isLetterOrDigit(c)) {
            search.focus();
            return search.charTyped(c);
        }
        return super.charTyped(c, mods);
    }

    @Override
    public void close() {
        if (closing) return;
        closing = true;
        TextInput.unfocusAll();
        open.target(0);
        SettingsDirty.mark();
    }

    @Override
    public void removed() {
        TextInput.unfocusAll();
        super.removed();
    }
}
