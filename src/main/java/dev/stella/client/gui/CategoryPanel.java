package dev.stella.client.gui;

import dev.stella.client.config.GuiLayout;
import dev.stella.client.gui.theme.Theme;
import dev.stella.client.gui.theme.ThemeManager;
import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.module.ModuleManager;
import dev.stella.client.modules.client.ClickGuiModule;
import dev.stella.client.render.Render2D;
import dev.stella.client.setting.SettingsDirty;
import dev.stella.client.util.Animation;
import dev.stella.client.util.ColorUtil;
import dev.stella.client.util.Sounds;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** One narrow, draggable, scrollable, collapsible category column. */
public class CategoryPanel {
    public static final int WIDTH = 100;
    static final int HEADER = 16;
    static final int ROW = 12;
    static final float TEXT = 0.75f;

    final Category category;
    final GuiLayout.PanelState state;
    private final List<Module> modules;
    private final Map<Module, Animation> toggleAnim = new HashMap<>();
    private final Map<Module, Animation> hoverAnim = new HashMap<>();
    private final Animation openAnim;
    private double scroll;
    private boolean dragging;
    private double dragX, dragY;
    private List<Module> visible = new ArrayList<>();
    private Module lastHovered;

    CategoryPanel(Category category, int defaultX, int defaultY) {
        this.category = category;
        this.modules = ModuleManager.byCategory(category);
        this.state = GuiLayout.panel(category.displayName);
        if (!state.set) { state.x = defaultX; state.y = defaultY; }
        this.openAnim = new Animation(state.open ? 1 : 0, 16);
        for (Module m : modules) {
            toggleAnim.put(m, new Animation(m.isEnabled() ? 1 : 0, 18));
            hoverAnim.put(m, new Animation(0, 22));
        }
    }

    void filter(String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        visible = new ArrayList<>();
        for (Module m : modules) {
            if (q.isEmpty() || m.getName().toLowerCase(Locale.ROOT).contains(q) || m.getDescription().toLowerCase(Locale.ROOT).contains(q)) visible.add(m);
        }
    }

    boolean hasMatches() { return !visible.isEmpty(); }

    int maxBodyHeight(int screenHeight) { return Math.max(ROW * 3, screenHeight - state.y - HEADER - 30); }

    int bodyHeight(int screenHeight) {
        int content = visible.size() * ROW + 6;
        return (int) (Math.min(content, maxBodyHeight(screenHeight)) * openAnim.get());
    }

    private static String label(Module m) {
        boolean upper = ModuleManager.get(ClickGuiModule.class).uppercase.isOn();
        return upper ? m.getName().toUpperCase(Locale.ROOT).replace(" ", "") : m.getName();
    }

    void render(DrawContext ctx, int mx, int my, int screenHeight, double alpha, boolean searching, ClickGuiScreen screen) {
        Theme t = ThemeManager.get();
        int x = state.x, y = state.y;
        double a = alpha * (searching && visible.isEmpty() ? 0.35 : 1.0);
        openAnim.target(state.open ? 1 : 0);
        int bh = bodyHeight(screenHeight);
        int total = HEADER + bh;

        // card + shadow
        Render2D.shadow(ctx, x, y, WIDTH, total, 5, 4, ColorUtil.mulAlpha(0x80000000, a));
        Render2D.roundRect(ctx, x, y, WIDTH, total, 5, ColorUtil.mulAlpha(t.background(), a));
        Render2D.roundRect(ctx, x, y, WIDTH, HEADER, 5, ColorUtil.mulAlpha(t.header(), a));
        if (bh > 0) ctx.fill(x, y + HEADER - 4, x + WIDTH, y + HEADER, ColorUtil.mulAlpha(t.header(), a));
        // header: icon + uppercase title
        String icon = GuiDraw.icon(category);
        GuiDraw.text(ctx, icon, x + 6, y + 4, ColorUtil.mulAlpha(t.accent(), a));
        GuiDraw.small(ctx, category.displayName.toUpperCase(Locale.ROOT), x + 8 + GuiDraw.width(icon), y + 5.5f, 0.85f, ColorUtil.mulAlpha(t.text(), a));
        long on = visible.stream().filter(Module::isEnabled).count();
        String count = on + "/" + visible.size();
        GuiDraw.small(ctx, count, x + WIDTH - 6 - GuiDraw.smallWidth(count, 0.7f), y + 6, 0.7f, ColorUtil.mulAlpha(t.textDim(), a));
        if (bh <= 0) return;

        int bodyY = y + HEADER;
        int content = visible.size() * ROW + 6;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, content - maxBodyHeight(screenHeight))));

        ctx.enableScissor(x, bodyY, x + WIDTH, bodyY + bh);
        int ry = bodyY + 3 - (int) scroll;
        Module hoveredNow = null;
        for (Module m : visible) {
            if (ry + ROW >= bodyY && ry <= bodyY + bh) {
                boolean hover = GuiDraw.in(mx, my, x + 3, ry, WIDTH - 6, ROW) && my >= bodyY && my < bodyY + bh;
                double hv = hoverAnim.get(m).target(hover ? 1 : 0).get();
                double tv = toggleAnim.get(m).target(m.isEnabled() ? 1 : 0).get();
                // row: transparent -> hover tint; enabled rows are filled with the accent
                if (hv > 0.01) Render2D.roundRect(ctx, x + 3, ry, WIDTH - 6, ROW - 1, 3, ColorUtil.mulAlpha(t.rowHover(), a * hv));
                if (tv > 0.01) {
                    int left = ColorUtil.withAlpha(ThemeManager.accentAt(0), (int) (0xE0 * tv));
                    Render2D.roundRect(ctx, x + 3, ry, WIDTH - 6, ROW - 1, 3, ColorUtil.mulAlpha(left, a));
                    Render2D.hGradient(ctx, x + WIDTH / 2f, ry + 1, WIDTH / 2f - 6, ROW - 3, 0x00000000, ColorUtil.mulAlpha(0x38000000, a * tv));
                }
                int nameColor = ColorUtil.lerp(ColorUtil.lerp(t.textDim(), t.text(), hv * 0.7), 0xFFFFFFFF, tv);
                GuiDraw.small(ctx, GuiDraw.trimSmall(label(m), WIDTH - 34, TEXT), x + 7, ry + 3, TEXT, ColorUtil.mulAlpha(nameColor, a));
                boolean gearHover = GuiDraw.in(mx, my, x + WIDTH - 28, ry, 10, ROW);
                boolean open = screen.isSettingsOpen(m);
                if (hover || open || gearHover) {
                    int gc = gearHover || open ? (tv > 0.5 ? 0xFFFFFFFF : t.accentSoft()) : (tv > 0.5 ? 0xC0FFFFFF : t.textDim());
                    GuiDraw.gear(ctx, x + WIDTH - 27, ry + 2, ColorUtil.mulAlpha(gc, a));
                }
                GuiDraw.miniToggle(ctx, t, x + WIDTH - 17, ry + 3, tv, a, tv > 0.5);
                if (hover) hoveredNow = m;
            }
            ry += ROW;
        }
        ctx.disableScissor();
        if (hoveredNow != null) {
            screen.setHovered(hoveredNow);
            if (hoveredNow != lastHovered) Sounds.hover();
        }
        lastHovered = hoveredNow;
        // scrollbar
        int maxBody = maxBodyHeight(screenHeight);
        if (content > maxBody && bh > 10) {
            int barH = Math.max(10, bh * bh / content);
            int barY = bodyY + (int) ((bh - barH) * (scroll / (content - maxBody)));
            Render2D.roundRect(ctx, x + WIDTH - 2.5f, barY, 1.5f, barH, 0.75f, ColorUtil.mulAlpha(t.accent(), a * 0.8));
        }
    }

    boolean mouseClicked(double mx, double my, int button, int screenHeight, ClickGuiScreen screen) {
        int x = state.x, y = state.y;
        if (GuiDraw.in(mx, my, x, y, WIDTH, HEADER)) {
            if (button == 0) { dragging = true; dragX = mx - x; dragY = my - y; }
            else if (button == 1) { state.open = !state.open; state.set = true; SettingsDirty.mark(); Sounds.click(); }
            return true;
        }
        int bh = bodyHeight(screenHeight);
        int bodyY = y + HEADER;
        if (!GuiDraw.in(mx, my, x, bodyY, WIDTH, bh)) return false;
        int idx = (int) ((my - bodyY - 3 + scroll) / ROW);
        if (idx < 0 || idx >= visible.size()) return true;
        Module m = visible.get(idx);
        boolean gear = mx >= x + WIDTH - 29 && mx < x + WIDTH - 18;
        if (button == 1 || button == 2 || gear) { screen.toggleSettings(m, (int) mx, (int) my); Sounds.click(); }
        else if (button == 0) { m.toggle(); Sounds.toggle(m.isEnabled()); }
        return true;
    }

    boolean mouseDragged(double mx, double my) {
        if (!dragging) return false;
        state.x = (int) (mx - dragX);
        state.y = (int) Math.max(0, my - dragY);
        state.set = true;
        return true;
    }

    void mouseReleased() {
        if (dragging) SettingsDirty.mark();
        dragging = false;
    }

    boolean mouseScrolled(double mx, double my, double amount, int screenHeight) {
        int bh = bodyHeight(screenHeight);
        if (!GuiDraw.in(mx, my, state.x, state.y, WIDTH, HEADER + bh)) return false;
        scroll -= amount * ROW * 1.5;
        return true;
    }

    boolean isOver(double mx, double my, int screenHeight) {
        return GuiDraw.in(mx, my, state.x, state.y, WIDTH, HEADER + bodyHeight(screenHeight));
    }
}
