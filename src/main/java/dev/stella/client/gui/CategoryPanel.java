package dev.stella.client.gui;

import dev.stella.client.config.GuiLayout;
import dev.stella.client.gui.theme.Theme;
import dev.stella.client.gui.theme.ThemeManager;
import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.module.ModuleManager;
import dev.stella.client.setting.SettingsDirty;
import dev.stella.client.util.Animation;
import dev.stella.client.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** One narrow, draggable, scrollable, collapsible category column. */
public class CategoryPanel {
    public static final int WIDTH = 104;
    static final int HEADER = 16;
    static final int ROW = 14;

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

    CategoryPanel(Category category, int defaultX, int defaultY) {
        this.category = category;
        this.modules = ModuleManager.byCategory(category);
        this.state = GuiLayout.panel(category.displayName);
        if (!state.set) { state.x = defaultX; state.y = defaultY; }
        this.openAnim = new Animation(state.open ? 1 : 0, 16);
        for (Module m : modules) {
            toggleAnim.put(m, new Animation(m.isEnabled() ? 1 : 0, 18));
            hoverAnim.put(m, new Animation(0, 20));
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

    int maxBodyHeight(int screenHeight) { return Math.max(ROW * 3, screenHeight - state.y - HEADER - 8); }

    int bodyHeight(int screenHeight) {
        int content = visible.size() * ROW + 2;
        return (int) (Math.min(content, maxBodyHeight(screenHeight)) * openAnim.get());
    }

    void render(DrawContext ctx, int mx, int my, int screenHeight, double alpha, boolean searching, ClickGuiScreen screen) {
        Theme t = ThemeManager.get();
        int x = state.x, y = state.y;
        double a = alpha * (searching && visible.isEmpty() ? 0.35 : 1.0);
        openAnim.target(state.open ? 1 : 0);

        // header
        ctx.fill(x, y, x + WIDTH, y + HEADER, ColorUtil.mulAlpha(t.header(), a));
        ctx.fill(x, y + HEADER - 1, x + WIDTH, y + HEADER, ColorUtil.mulAlpha(t.accent(), a));
        GuiDraw.text(ctx, category.displayName, x + 5, y + 4, ColorUtil.mulAlpha(t.text(), a));
        String count = String.valueOf(visible.size());
        GuiDraw.text(ctx, count, x + WIDTH - 5 - GuiDraw.width(count), y + 4, ColorUtil.mulAlpha(t.textDim(), a));

        int bh = bodyHeight(screenHeight);
        if (bh <= 0) return;
        int bodyY = y + HEADER;
        ctx.fill(x, bodyY, x + WIDTH, bodyY + bh, ColorUtil.mulAlpha(t.background(), a));
        int content = visible.size() * ROW + 2;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, content - maxBodyHeight(screenHeight))));

        ctx.enableScissor(x, bodyY, x + WIDTH, bodyY + bh);
        int ry = bodyY + 1 - (int) scroll;
        for (Module m : visible) {
            if (ry + ROW >= bodyY && ry <= bodyY + bh) {
                boolean hover = GuiDraw.in(mx, my, x, ry, WIDTH, ROW) && my >= bodyY && my < bodyY + bh;
                double hv = hoverAnim.get(m).target(hover ? 1 : 0).get();
                double tv = toggleAnim.get(m).target(m.isEnabled() ? 1 : 0).get();
                int rowColor = ColorUtil.lerp(t.row(), t.rowHover(), hv);
                ctx.fill(x + 1, ry, x + WIDTH - 1, ry + ROW - 1, ColorUtil.mulAlpha(rowColor, a));
                if (tv > 0.01) ctx.fill(x + 1, ry, x + 2, ry + ROW - 1, ColorUtil.mulAlpha(t.accent(), a * tv));
                int nameColor = ColorUtil.lerp(t.textDim(), t.text(), Math.max(tv, hv * 0.6));
                GuiDraw.text(ctx, GuiDraw.trim(m.getName(), WIDTH - 36), x + 5, ry + 3, ColorUtil.mulAlpha(nameColor, a));
                boolean gearHover = GuiDraw.in(mx, my, x + WIDTH - 30, ry, 10, ROW);
                GuiDraw.gear(ctx, x + WIDTH - 29, ry + 3, ColorUtil.mulAlpha(gearHover || screen.isSettingsOpen(m) ? t.accentSoft() : t.textDim(), a));
                GuiDraw.toggle(ctx, t, x + WIDTH - 18, ry + 3, tv, a);
                if (hover) screen.setHovered(m);
            }
            ry += ROW;
        }
        ctx.disableScissor();
        // scrollbar
        int maxBody = maxBodyHeight(screenHeight);
        if (content > maxBody && bh > 10) {
            int barH = Math.max(10, bh * bh / content);
            int barY = bodyY + (int) ((bh - barH) * (scroll / (content - maxBody)));
            ctx.fill(x + WIDTH - 1, barY, x + WIDTH, barY + barH, ColorUtil.mulAlpha(t.accent(), a * 0.8));
        }
    }

    boolean mouseClicked(double mx, double my, int button, int screenHeight, ClickGuiScreen screen) {
        int x = state.x, y = state.y;
        if (GuiDraw.in(mx, my, x, y, WIDTH, HEADER)) {
            if (button == 0) { dragging = true; dragX = mx - x; dragY = my - y; }
            else if (button == 1) { state.open = !state.open; state.set = true; SettingsDirty.mark(); }
            return true;
        }
        int bh = bodyHeight(screenHeight);
        int bodyY = y + HEADER;
        if (!GuiDraw.in(mx, my, x, bodyY, WIDTH, bh)) return false;
        int idx = (int) ((my - bodyY - 1 + scroll) / ROW);
        if (idx < 0 || idx >= visible.size()) return true;
        Module m = visible.get(idx);
        boolean gear = mx >= x + WIDTH - 31 && mx < x + WIDTH - 19;
        if (button == 1 || gear) screen.toggleSettings(m, (int) mx, (int) my);
        else if (button == 0) { m.toggle(); dev.stella.client.util.Sounds.click(); }
        else if (button == 2) screen.toggleSettings(m, (int) mx, (int) my);
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
