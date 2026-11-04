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
public class ClickGuiScreen extends dev.stella.client.gui.StellaScreen {
    private final List<CategoryPanel> panels = new ArrayList<>();
    private final List<SettingsWindow> windows = new ArrayList<>();
    private final ThemesPanel side = new ThemesPanel();
    private final ConfigsPopup configs = new ConfigsPopup();
    private final TextInput search = new TextInput("", 32, "SEARCH MODULES...");
    private final WindowLayout win = new WindowLayout();

    private boolean windowMode() {
        ClickGuiModule g = ModuleManager.get(ClickGuiModule.class);
        return g == null || g.layout.is("Window");
    }

    private boolean themesVisible() { return !windowMode() || win.showThemes; }

    /** Bottom pill next to Configs: flips between the window layout and the classic dropdown panels. */
    private int[] layoutPill() {
        String label = layoutLabel();
        int cw = GuiDraw.smallWidth("\u2699 CONFIGS", 0.8f) + 16;
        int bw = GuiDraw.smallWidth(label, 0.8f) + 16;
        return new int[]{vw / 2 + cw / 2 + 6, vh - 18, bw, 13};
    }

    private String layoutLabel() { return windowMode() ? "\u2630 DROPDOWN VIEW" : "\u25A3 WINDOW VIEW"; }

    private void renderLayoutPill(DrawContext ctx, int mx, int my, double a) {
        Theme t = ThemeManager.get();
        int[] r = layoutPill();
        boolean h = GuiDraw.in(mx, my, r[0], r[1], r[2], r[3]);
        dev.stella.client.render.Render2D.shadow(ctx, r[0], r[1], r[2], r[3], 6.5f, 3, ColorUtil.mulAlpha(0x80000000, a));
        dev.stella.client.render.Render2D.roundRect(ctx, r[0], r[1], r[2], r[3], 6.5f, ColorUtil.mulAlpha(h ? t.accent() : t.header(), a));
        GuiDraw.small(ctx, layoutLabel(), r[0] + 8, r[1] + 3.5f, 0.8f, ColorUtil.mulAlpha(h ? 0xFFFFFFFF : t.text(), a));
    }

    private void switchLayout() {
        ClickGuiModule g = ModuleManager.get(ClickGuiModule.class);
        g.layout.set(windowMode() ? "Panels" : "Window");
        windows.clear();
        SettingsDirty.mark();
        dev.stella.client.util.Sounds.click();
    }

    void clearSearch() { search.set(""); panels.forEach(p -> p.filter("")); }

    private int searchX(int sw) {
        int avail = vw - (themesVisible() ? ThemesPanel.WIDTH : 0);
        return avail / 2 - sw / 2;
    }
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
            this.applyBlur(ctx);
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
        int wx = windowMode() ? win.settingsX(mx, vw) : mx + 12;
        if (wx + SettingsWindow.WIDTH > vw - (themesVisible() ? ThemesPanel.WIDTH : 0) - 8) wx = mx - SettingsWindow.WIDTH - 12;
        int wy = windowMode() ? win.settingsY() + windows.size() * 14 : Math.max(4, my - 20);
        windows.add(new SettingsWindow(m, wx, wy));
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
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().scale((float) (ui), (float) (ui));
        double oa = open.get();
        float zoom = (float) (0.965 + 0.035 * Math.min(1, oa));
        ctx.getMatrices().translate((float) (vw / 2f), (float) (vh / 2f));
        ctx.getMatrices().scale((float) (zoom), (float) (zoom));
        ctx.getMatrices().translate((float) (-vw / 2f), (float) (-vh / 2f));
        int mx = (int) (rawX / ui), my = (int) (rawY / ui);
        try {
            renderScaled(ctx, mx, my);
        } finally {
            ctx.getMatrices().popMatrix();
        }
    }

    private final long openedAt = System.currentTimeMillis();
    private long closedAt;

    /** Staggered entrance: each panel drops in slightly after the previous one with a soft overshoot. */
    private void renderPanelAnimated(DrawContext ctx, CategoryPanel p, int mx, int my, double a, boolean searching) {
        var anims = ModuleManager.get(dev.stella.client.modules.settings.AnimationsModule.class);
        boolean animate = anims == null || !anims.isEnabled() || anims.guiSlide.isOn();
        double pa = 1, dy = 0, sc = 1;
        long now = System.currentTimeMillis();
        if (animate && !closing) {
            // open: each panel drops in after the previous one, scaling up with a soft overshoot
            double tt = (now - openedAt - p.category.ordinal() * 35) / 340.0;
            pa = Math.max(0, Math.min(1, tt));
            double e = easeOutBack(pa);
            dy = (1 - e) * -18;
            sc = 0.86 + 0.14 * e;
        } else if (animate) {
            // close: the same choreography in reverse, panels lift away one after another
            double tc = (now - closedAt - p.category.ordinal() * 18) / 190.0;
            pa = Math.max(0, Math.min(1, 1 - tc));
            double e = 1 - Math.pow(1 - pa, 3);
            dy = (1 - e) * -14;
            sc = 0.92 + 0.08 * e;
        }
        ctx.createNewRootLayer();
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(0, (float) (dy));
        float pcx = p.panelX() + CategoryPanel.WIDTH / 2f, pcy = p.panelY();
        ctx.getMatrices().translate(pcx, pcy);
        ctx.getMatrices().scale((float) sc, (float) sc);
        ctx.getMatrices().translate(-pcx, -pcy);
        p.render(ctx, mx, (int) (my - dy), vh, a * Math.min(1, pa * 1.6), searching, this);
        ctx.getMatrices().popMatrix();
    }

    private static double easeOutBack(double x) {
        double c1 = 1.70158, c3 = c1 + 1;
        return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2);
    }

    private void renderScaled(DrawContext ctx, int mx, int my) {
        Theme t = ThemeManager.get();
        double a = open.get();
        if (closing && a < 0.05 && System.currentTimeMillis() - closedAt > 300) {
            if (client != null && client.world == null) client.setScreen(new net.minecraft.client.gui.screen.TitleScreen(false));
            else super.close();
            return;
        }
        var anims = ModuleManager.get(dev.stella.client.modules.settings.AnimationsModule.class);
        int slide = anims != null && anims.isEnabled() && !anims.guiSlide.isOn() ? 0 : (int) ((1 - a) * -12);
        hovered = null;

        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(0, (float) (slide));
        // search bar (rounded pill with magnifier)
        int sw = 140, sx = searchX(sw), sy = 6;
        dev.stella.client.render.Render2D.shadow(ctx, sx, sy, sw, 15, 7.5f, 3, ColorUtil.mulAlpha(0x80000000, a));
        dev.stella.client.render.Render2D.roundRect(ctx, sx, sy, sw, 15, 7.5f, ColorUtil.mulAlpha(t.header(), a));
        if (search.isFocused()) dev.stella.client.render.Render2D.roundRect(ctx, sx, sy + 14, sw, 1, 0.5f, ColorUtil.mulAlpha(t.accent(), a));
        GuiDraw.magnifier(ctx, sx + 8, sy + 4, ColorUtil.mulAlpha(t.textDim(), a));
        search.render(ctx, sx + 16, sy + 1, sw - 22, 13, 0, 0, ColorUtil.mulAlpha(t.text(), a), ColorUtil.mulAlpha(t.textDim(), a));
        boolean searching = !search.get().isBlank();
        if (windowMode()) {
            ctx.createNewRootLayer();
            win.render(ctx, mx, my - slide, vw, vh, a, search.get(), this);
        } else {
            for (CategoryPanel p : panels) {
                if (searching && !p.hasMatches()) continue;
                renderPanelAnimated(ctx, p, mx, my - slide, a, searching);
            }
            for (CategoryPanel p : panels) if (searching && !p.hasMatches()) renderPanelAnimated(ctx, p, mx, my - slide, a, true);
        }
        ctx.getMatrices().popMatrix();

        ctx.createNewRootLayer();
        if (themesVisible()) side.render(ctx, mx, my, vw, vh, a, this);
        configs.render(ctx, mx, my, vw, vh, a);
        renderLayoutPill(ctx, mx, my, a);
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
            ctx.getMatrices().pushMatrix();
            ctx.createNewRootLayer();
            ctx.fill(tx - 3, ty - 3, tx + w + 3, ty + lines.size() * 9 + 1, 0xF0100D16);
            ctx.drawStrokedRectangle(tx - 4, ty - 4, w + 8, lines.size() * 9 + 6, t.outline());
            for (int i = 0; i < lines.size(); i++) GuiDraw.text(ctx, lines.get(i), tx, ty + i * 9, t.text());
            ctx.getMatrices().popMatrix();
        }
    }

    // ---------------------------------------------------------------- input

    @Override
    protected boolean onMouseClicked(double mx, double my, int button) {
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
        int[] lp = layoutPill();
        if (GuiDraw.in(mx, my, lp[0], lp[1], lp[2], lp[3])) { switchLayout(); return true; }
        if (configs.mouseClicked(mx, my)) return true;
        if (themesVisible() && side.mouseClicked(mx, my, vw, vh)) return true;
        int sw = 140, sx = searchX(sw);
        if (GuiDraw.in(mx, my, sx, 6, sw, 15)) {
            if (button == 1) clearSearch();
            search.focus();
            return true;
        }
        if (windowMode()) {
            if (win.mouseClicked(mx, my, button, vw, vh, search.get(), this)) return true;
            return super.onMouseClicked(mx, my, button);
        }
        for (int i = panels.size() - 1; i >= 0; i--) {
            if (panels.get(i).mouseClicked(mx, my, button, vh, this)) {
                CategoryPanel p = panels.remove(i);
                panels.add(p); // dragged panel renders on top
                return true;
            }
        }
        return super.onMouseClicked(mx, my, button);
    }

    @Override
    protected boolean onMouseDragged(double mx, double my, int button, double dx, double dy) {
        mx /= ui;
        my /= ui;
        if (themesVisible() && side.mouseDragged(mx)) return true;
        for (SettingsWindow w : windows) if (w.mouseDragged(mx, my)) return true;
        for (CategoryPanel p : panels) if (p.mouseDragged(mx, my)) return true;
        return super.onMouseDragged(mx, my, button, dx, dy);
    }

    @Override
    protected boolean onMouseReleased(double mx, double my, int button) {
        mx /= ui;
        my /= ui;
        windows.forEach(SettingsWindow::mouseReleased);
        side.mouseReleased();
        panels.forEach(CategoryPanel::mouseReleased);
        return super.onMouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        mx /= ui;
        my /= ui;
        for (int i = windows.size() - 1; i >= 0; i--) if (windows.get(i).mouseScrolled(mx, my, v, vh)) return true;
        if (themesVisible() && side.mouseScrolled(mx, my, v, vw, vh)) return true;
        if (windowMode()) { if (win.mouseScrolled(mx, my, v, vw, vh, search.get())) return true; }
        else for (int i = panels.size() - 1; i >= 0; i--) if (panels.get(i).mouseScrolled(mx, my, v, vh)) return true;
        return super.mouseScrolled(mx, my, h, v);
    }

    @Override
    protected boolean onKeyPressed(int key, int scan, int mods) {
        for (SettingsWindow w : windows) if (w.keyPressed(key)) return true;
        if (themesVisible() && side.keyPressed(key)) return true;
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
        return super.onKeyPressed(key, scan, mods);
    }

    @Override
    protected boolean onCharTyped(char c, int mods) {
        if (configs.charTyped(c)) return true;
        TextInput f = TextInput.focused();
        if (f != null) return f.charTyped(c);
        if (Character.isLetterOrDigit(c)) {
            search.focus();
            return search.charTyped(c);
        }
        return super.onCharTyped(c, mods);
    }

    @Override
    public void close() {
        if (closing) return;
        closing = true;
        closedAt = System.currentTimeMillis();
        dev.stella.client.util.Sounds.open(false);
        TextInput.unfocusAll();
        open.target(0);
        SettingsDirty.mark();
    }

    @Override
    public void removed() {
        TextInput.unfocusAll();
        var cm = ModuleManager.get(dev.stella.client.modules.settings.ConfigModule.class);
        if (cm != null && cm.saveOnClose.isOn()) dev.stella.client.config.ConfigManager.save();
        super.removed();
    }
}
