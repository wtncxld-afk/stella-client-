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
    private final ThemesPanel side = new ThemesPanel();
    private final ConfigsPopup configs = new ConfigsPopup();
    private final TextInput search = new TextInput("", 32, "SEARCH MODULES...");
    private final Animation open = new Animation(0, 12).target(1);
    private Module hovered;
    private boolean closing;
    /** The GUI is laid out on a virtual canvas of at least ~1000x540 and scaled to the window,
     *  so every panel fits regardless of Minecraft's GUI scale. */
    private float ui = 1f;
    private int vw, vh;

    private void computeScale() {
        double size = ModuleManager.get(ClickGuiModule.class).guiSize.get();
        ui = (float) (Math.min(1.0, Math.min(width / 1000.0, height / 540.0)) * size);
        vw = (int) (width / ui);
        vh = (int) (height / ui);
    }

    public ClickGuiScreen() {
        super(Text.literal("Stella ClickGUI"));
        search.onChange(q -> panels.forEach(p -> p.filter(q)));
    }

    @Override
    protected void init() {
        computeScale();
        if (panels.isEmpty()) {
            int x = 6, y = 28;
            int usable = vw - ThemesPanel.WIDTH - 12;
            for (Category c : Category.values()) {
                if (x + CategoryPanel.WIDTH > usable) { x = 6; y += 180; }
                panels.add(new CategoryPanel(c, x, y));
                x += CategoryPanel.WIDTH + 5;
            }
            panels.forEach(p -> p.filter(""));
            dev.stella.client.util.Sounds.open(true);
        }
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (client != null && client.world != null && ModuleManager.get(ClickGuiModule.class).blur.isOn()) {
            this.applyBlur();
        }
        int a = (int) (0x70 * open.get());
        ctx.fillGradient(0, 0, width, height, (a << 24) | 0x05030A, (Math.min(255, a + 0x20) << 24) | 0x120822);
    }

    public void setHovered(Module m) { this.hovered = m; }
    public boolean isSettingsOpen(Module m) { return windows.stream().anyMatch(w -> w.module == m); }

    public void toggleSettings(Module m, int mx, int my) {
        for (SettingsWindow w : windows) {
            if (w.module == m) { windows.remove(w); return; }
        }
        int wx = mx + 12;
        if (wx + SettingsWindow.WIDTH > vw - ThemesPanel.WIDTH - 8) wx = mx - SettingsWindow.WIDTH - 12;
        windows.add(new SettingsWindow(m, wx, Math.max(4, my - 20)));
        if (windows.size() > 4) windows.remove(0);
    }

    /** Used by the CI smoke test. */
    public void openSettingsFor(String moduleName) {
        if (vw == 0) computeScale();
        for (Module m : ModuleManager.all()) if (m.getName().equals(moduleName) && !isSettingsOpen(m)) toggleSettings(m, 110 + windows.size() * (SettingsWindow.WIDTH + 8), 70);
    }

    @Override
    public void render(DrawContext ctx, int rawX, int rawY, float delta) {
        super.render(ctx, rawX, rawY, delta);
        computeScale();
        ctx.getMatrices().push();
        ctx.getMatrices().scale(ui, ui, 1);
        int mx = (int) (rawX / ui), my = (int) (rawY / ui);
        try {
            renderScaled(ctx, mx, my);
        } finally {
            ctx.getMatrices().pop();
        }
    }

    private void renderScaled(DrawContext ctx, int mx, int my) {
        Theme t = ThemeManager.get();
        double a = open.get();
        if (closing && a < 0.05) { super.close(); return; }
        int slide = (int) ((1 - a) * -12);
        hovered = null;

        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, slide, 0);
        // search bar (rounded pill with magnifier)
        int sw = 140, sx = (vw - ThemesPanel.WIDTH) / 2 - sw / 2, sy = 6;
        dev.stella.client.render.Render2D.shadow(ctx, sx, sy, sw, 15, 7.5f, 3, ColorUtil.mulAlpha(0x80000000, a));
        dev.stella.client.render.Render2D.roundRect(ctx, sx, sy, sw, 15, 7.5f, ColorUtil.mulAlpha(t.header(), a));
        if (search.isFocused()) dev.stella.client.render.Render2D.roundRect(ctx, sx, sy + 14, sw, 1, 0.5f, ColorUtil.mulAlpha(t.accent(), a));
        GuiDraw.magnifier(ctx, sx + 8, sy + 4, ColorUtil.mulAlpha(t.textDim(), a));
        search.render(ctx, sx + 16, sy + 1, sw - 22, 13, 0, 0, ColorUtil.mulAlpha(t.text(), a), ColorUtil.mulAlpha(t.textDim(), a));
        boolean searching = !search.get().isBlank();
        for (CategoryPanel p : panels) {
            if (searching && !p.hasMatches()) continue;
            p.render(ctx, mx, my - slide, vh, a, searching, this);
        }
        for (CategoryPanel p : panels) if (searching && !p.hasMatches()) p.render(ctx, mx, my - slide, vh, a, true, this);
        ctx.getMatrices().pop();

        side.render(ctx, mx, my, vw, vh, a, this);
        configs.render(ctx, mx, my, vw, vh, a);
        windows.removeIf(SettingsWindow::isClosed);
        for (SettingsWindow w : windows) w.render(ctx, mx, my, vw, vh);

        // hover description
        if (hovered != null && windows.stream().noneMatch(w -> w.isOver(mx, my, vh))
                && ModuleManager.get(ClickGuiModule.class).descriptions.isOn()) {
            String d = hovered.getDescription();
            List<String> lines = SettingsWindow.wrap(d, 170);
            int w = 0;
            for (String l : lines) w = Math.max(w, GuiDraw.width(l));
            int tx = mx + 10, ty = my + 10;
            if (tx + w + 8 > vw) tx = mx - w - 14;
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
        mx /= ui;
        my /= ui;
        for (int i = windows.size() - 1; i >= 0; i--) {
            SettingsWindow w = windows.get(i);
            if (w.mouseClicked(mx, my, button, vh)) {
                windows.remove(w);
                if (!w.isClosed()) windows.add(w); // bring to front
                return true;
            }
        }
        TextInput.unfocusAll();
        if (configs.mouseClicked(mx, my)) return true;
        if (side.mouseClicked(mx, my, vw, vh)) return true;
        int sw = 140, sx = (vw - ThemesPanel.WIDTH) / 2 - sw / 2;
        if (GuiDraw.in(mx, my, sx, 6, sw, 15)) {
            if (button == 1) { search.set(""); panels.forEach(p -> p.filter("")); }
            search.focus();
            return true;
        }
        for (int i = panels.size() - 1; i >= 0; i--) {
            if (panels.get(i).mouseClicked(mx, my, button, vh, this)) {
                CategoryPanel p = panels.remove(i);
                panels.add(p); // dragged panel renders on top
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        mx /= ui;
        my /= ui;
        if (side.mouseDragged(mx)) return true;
        for (SettingsWindow w : windows) if (w.mouseDragged(mx, my)) return true;
        for (CategoryPanel p : panels) if (p.mouseDragged(mx, my)) return true;
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        mx /= ui;
        my /= ui;
        windows.forEach(SettingsWindow::mouseReleased);
        side.mouseReleased();
        panels.forEach(CategoryPanel::mouseReleased);
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        mx /= ui;
        my /= ui;
        for (int i = windows.size() - 1; i >= 0; i--) if (windows.get(i).mouseScrolled(mx, my, v, vh)) return true;
        if (side.mouseScrolled(mx, my, v, vw, vh)) return true;
        for (int i = panels.size() - 1; i >= 0; i--) if (panels.get(i).mouseScrolled(mx, my, v, vh)) return true;
        return super.mouseScrolled(mx, my, h, v);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        for (SettingsWindow w : windows) if (w.keyPressed(key)) return true;
        if (side.keyPressed(key)) return true;
        if (configs.keyPressed(key)) return true;
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
        if (configs.charTyped(c)) return true;
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
        dev.stella.client.util.Sounds.open(false);
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
