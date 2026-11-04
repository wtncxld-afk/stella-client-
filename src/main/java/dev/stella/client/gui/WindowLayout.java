package dev.stella.client.gui;

import dev.stella.client.gui.theme.Theme;
import dev.stella.client.gui.theme.ThemeManager;
import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.module.ModuleManager;
import dev.stella.client.modules.client.ClickGuiModule;
import dev.stella.client.render.Glass;
import dev.stella.client.render.Render2D;
import dev.stella.client.util.ColorUtil;
import dev.stella.client.util.Sounds;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Clean single-window ClickGUI: a category sidebar on the left, a header with the category name and
 * search, and a scrollable grid of module cards (name, one-line description, keybind, settings gear and
 * switch). Settings still open as floating windows. The Themes panel is hidden until you ask for it.
 */
final class WindowLayout {
    private static final int SIDEBAR = 112, CARD_H = 30, GAP = 6, HEADER = 30;

    private Category selected = Category.DONUTSMP;
    private final Map<Category, Double> scroll = new EnumMap<>(Category.class);
    private final Map<Module, dev.stella.client.util.Spring> toggleAnim = new HashMap<>();
    private final Map<Module, dev.stella.client.util.Spring> hoverAnim = new HashMap<>();
    private double searchScroll;
    boolean showThemes;

    // geometry of the last frame (for input)
    private int wx, wy, ww, wh;

    private void layout(int vw, int vh) {
        int avail = vw - (showThemes ? ThemesPanel.WIDTH + 12 : 0);
        ww = Math.min(avail - 24, 680);
        wh = Math.min(vh - 56, 390);
        wx = Math.max(8, (avail - ww) / 2);
        wy = Math.max(26, (vh - wh) / 2 + 4);
    }

    private static List<Module> modules(Category c) {
        List<Module> out = new ArrayList<>();
        for (Module m : ModuleManager.all()) if (m.getCategory() == c) out.add(m);
        return out;
    }

    private static List<Module> search(String q) {
        String s = q.toLowerCase(Locale.ROOT).trim();
        List<Module> out = new ArrayList<>();
        for (Module m : ModuleManager.all())
            if (m.getName().toLowerCase(Locale.ROOT).contains(s) || m.getDescription().toLowerCase(Locale.ROOT).contains(s)) out.add(m);
        out.sort((a, b) -> Boolean.compare(!a.getName().toLowerCase(Locale.ROOT).startsWith(s), !b.getName().toLowerCase(Locale.ROOT).startsWith(s)));
        return out;
    }

    private int columns() { return ww - SIDEBAR >= 420 ? 2 : 1; }

    private static String label(Module m) {
        ClickGuiModule g = ModuleManager.get(ClickGuiModule.class);
        return g != null && g.uppercase.isOn() ? m.getName().toUpperCase(Locale.ROOT) : m.getName();
    }

    void render(DrawContext ctx, int mx, int my, int vw, int vh, double a, String query, ClickGuiScreen screen) {
        layout(vw, vh);
        Theme t = ThemeManager.get();
        boolean glass = Glass.active();
        // window body
        if (glass) Glass.panel(ctx, wx, wy, ww, wh, 9, 0, a);
        else {
            Render2D.shadow(ctx, wx, wy, ww, wh, 9, 6, ColorUtil.mulAlpha(0x90000000, a));
            Render2D.roundRect(ctx, wx, wy, ww, wh, 9, ColorUtil.mulAlpha(t.background() | 0xF0000000, a));
        }
        // sidebar
        int sx = wx, sy = wy;
        Render2D.roundRect(ctx, sx, sy, SIDEBAR, wh, 9, ColorUtil.mulAlpha(glass ? 0x22FFFFFF : t.header(), a));
        Render2D.rect(ctx, sx + SIDEBAR - 1, sy + 8, 1, wh - 16, ColorUtil.mulAlpha(t.outline(), a * 0.6));
        // brand
        GuiDraw.text(ctx, "✦", sx + 10, sy + 10, ColorUtil.mulAlpha(t.accent(), a));
        GuiDraw.small(ctx, "STELLA", sx + 22, sy + 10.5f, 1.0f, ColorUtil.mulAlpha(t.text(), a));
        GuiDraw.small(ctx, "v" + dev.stella.client.StellaClient.VERSION, sx + 22, sy + 20, 0.6f, ColorUtil.mulAlpha(t.textDim(), a));
        int iy = sy + 36;
        boolean searching = !query.isBlank();
        for (Category c : Category.values()) {
            boolean sel = c == selected && !searching;
            boolean hover = GuiDraw.in(mx, my, sx + 5, iy, SIDEBAR - 10, 17);
            if (sel) Render2D.roundRect(ctx, sx + 5, iy, SIDEBAR - 10, 17, 4, ColorUtil.mulAlpha(ColorUtil.withAlpha(t.accent(), 0xD0), a));
            else if (hover) Render2D.roundRect(ctx, sx + 5, iy, SIDEBAR - 10, 17, 4, ColorUtil.mulAlpha(t.rowHover(), a));
            int tc = sel ? 0xFFFFFFFF : hover ? t.text() : t.textDim();
            GuiDraw.text(ctx, GuiDraw.icon(c), sx + 10, iy + 5, ColorUtil.mulAlpha(sel ? 0xFFFFFFFF : t.accent(), a));
            GuiDraw.small(ctx, c.displayName.toUpperCase(Locale.ROOT), sx + 23, iy + 5.5f, 0.78f, ColorUtil.mulAlpha(tc, a));
            List<Module> ms = modules(c);
            long on = ms.stream().filter(Module::isEnabled).count();
            String cnt = on + "/" + ms.size();
            GuiDraw.small(ctx, cnt, sx + SIDEBAR - 10 - GuiDraw.smallWidth(cnt, 0.6f), iy + 6.5f, 0.6f, ColorUtil.mulAlpha(sel ? 0xD0FFFFFF : t.textDim(), a));
            iy += 19;
        }
        // themes toggle at the bottom of the sidebar
        int ty = wy + wh - 24;
        boolean th = GuiDraw.in(mx, my, sx + 5, ty, SIDEBAR - 10, 17);
        Render2D.roundRect(ctx, sx + 5, ty, SIDEBAR - 10, 17, 4, ColorUtil.mulAlpha(showThemes ? ColorUtil.withAlpha(t.accent(), 0x90) : th ? t.rowHover() : 0x00000000, a));
        GuiDraw.text(ctx, "◐", sx + 10, ty + 5, ColorUtil.mulAlpha(t.accent(), a));
        GuiDraw.small(ctx, showThemes ? "HIDE THEMES" : "THEMES", sx + 23, ty + 5.5f, 0.78f, ColorUtil.mulAlpha(t.text(), a));

        // header
        int cx = wx + SIDEBAR + 12, cw = ww - SIDEBAR - 24;
        String title = searching ? "SEARCH" : selected.displayName.toUpperCase(Locale.ROOT);
        GuiDraw.small(ctx, title, cx, wy + 11, 1.15f, ColorUtil.mulAlpha(t.text(), a));
        String sub = searching ? "Results for \"" + query.trim() + "\"" : descriptionOf(selected);
        GuiDraw.small(ctx, GuiDraw.trimSmall(sub, cw - 10, 0.65f), cx, wy + 23, 0.65f, ColorUtil.mulAlpha(t.textDim(), a));

        // cards
        List<Module> list = searching ? search(query) : modules(selected);
        int cols = columns(), cardW = (cw - (cols - 1) * GAP) / cols;
        int top = wy + HEADER + 8, viewH = wh - HEADER - 14;
        int rows = (list.size() + cols - 1) / cols, content = rows * (CARD_H + GAP);
        double sc = searching ? searchScroll : scroll.getOrDefault(selected, 0.0);
        sc = Math.max(0, Math.min(sc, Math.max(0, content - viewH)));
        if (searching) searchScroll = sc; else scroll.put(selected, sc);
        ctx.enableScissor(cx - 2, top, cx + cw + 2, top + viewH);
        for (int i = 0; i < list.size(); i++) {
            Module m = list.get(i);
            int x = cx + (i % cols) * (cardW + GAP), y = top + (i / cols) * (CARD_H + GAP) - (int) sc;
            if (y + CARD_H < top || y > top + viewH) continue;
            card(ctx, t, m, x, y, cardW, mx, my, a, searching, screen, my >= top && my < top + viewH, glass);
        }
        ctx.disableScissor();
        if (list.isEmpty()) {
            String e = searching ? "No modules match" : "Nothing here";
            GuiDraw.small(ctx, e, cx + cw / 2f - GuiDraw.smallWidth(e, 0.8f) / 2f, top + 40, 0.8f, ColorUtil.mulAlpha(t.textDim(), a));
        }
        if (content > viewH) {
            int barH = Math.max(14, viewH * viewH / content);
            int barY = top + (int) ((viewH - barH) * (sc / (content - viewH)));
            Render2D.roundRect(ctx, wx + ww - 7, barY, 2, barH, 1, ColorUtil.mulAlpha(t.accent(), a * 0.8));
        }
    }

    private static String descriptionOf(Category c) {
        return switch (c) {
            case DONUTSMP -> "Finders, ESP and tools made for DonutSMP";
            case COMBAT -> "Totems, crystals, anchors and PvP info";
            case MISC -> "Quality of life: sprint, tools, friends, macros";
            case VISUALS -> "How the world looks: sky, outline, glow, zoom";
            case CLIENT -> "GUI, menus, performance and tools";
            case WORLD -> "Light levels and block / weather alerts";
            case HUD -> "On-screen elements - move them in the HUD Editor";
            case COSMETICS -> "Wings, capes, knives and more - only you see them";
            case ANTI_CHEAT_OFF -> "Singleplayer and your authorized test servers only";
            case SETTINGS -> "Config, animations and the auto updater";
        };
    }

    private void card(DrawContext ctx, Theme t, Module m, int x, int y, int w, int mx, int my, double a,
                      boolean showCategory, ClickGuiScreen screen, boolean inView, boolean glass) {
        boolean hover = inView && GuiDraw.in(mx, my, x, y, w, CARD_H);
        double hv = hoverAnim.computeIfAbsent(m, k -> new dev.stella.client.util.Spring(0, 300, 26)).target(hover ? 1 : 0).get();
        double tv = toggleAnim.computeIfAbsent(m, k -> new dev.stella.client.util.Spring(m.isEnabled() ? 1 : 0, 260, 22)).target(m.isEnabled() ? 1 : 0).get();
        int base = glass ? 0x14FFFFFF : t.row();
        Render2D.roundRect(ctx, x, y, w, CARD_H, 5, ColorUtil.mulAlpha(ColorUtil.lerp(base, t.rowHover(), hv), a));
        if (tv > 0.01) {
            Render2D.roundRect(ctx, x, y, w, CARD_H, 5, ColorUtil.mulAlpha(ColorUtil.withAlpha(ThemeManager.accentAt(0), (int) (0x38 * tv)), a));
            Render2D.roundRect(ctx, x + 2, y + 5, 2, CARD_H - 10, 1, ColorUtil.mulAlpha(ColorUtil.withAlpha(ThemeManager.accentAt(0.1), (int) (0xFF * tv)), a));
        }
        int nameC = ColorUtil.lerp(t.text(), 0xFFFFFFFF, tv);
        GuiDraw.small(ctx, GuiDraw.trimSmall(label(m), w - 60, 0.85f), x + 9, y + 6, 0.85f, ColorUtil.mulAlpha(nameC, a));
        String d = m.getDescription();
        if (showCategory) d = m.getCategory().displayName + "  •  " + d;
        GuiDraw.small(ctx, GuiDraw.trimSmall(d, w - 48, 0.6f), x + 9, y + 18, 0.6f, ColorUtil.mulAlpha(t.textDim(), a));
        if (m.keybind.isBound()) {
            String k = m.keybind.keyName().toUpperCase(Locale.ROOT);
            int kw = GuiDraw.smallWidth(k, 0.55f) + 6;
            int kx = x + 12 + GuiDraw.smallWidth(GuiDraw.trimSmall(label(m), w - 60, 0.85f), 0.85f);
            Render2D.roundRect(ctx, kx, y + 5.5f, kw, 9, 2, ColorUtil.mulAlpha(0x40000000, a));
            GuiDraw.small(ctx, k, kx + 3, y + 7.5f, 0.55f, ColorUtil.mulAlpha(t.textDim(), a));
        }
        boolean gearHover = hover && GuiDraw.in(mx, my, x + w - 34, y, 14, CARD_H);
        boolean open = screen.isSettingsOpen(m);
        GuiDraw.gear(ctx, x + w - 31, y + 11, ColorUtil.mulAlpha(gearHover || open ? t.accentSoft() : ColorUtil.withAlpha(t.textDim(), 0xA0 + (int) (0x5F * hv)), a));
        GuiDraw.toggle(ctx, t, x + w - 19, y + 11, tv, a);
    }

    boolean mouseClicked(double mx, double my, int button, int vw, int vh, String query, ClickGuiScreen screen) {
        layout(vw, vh);
        if (!GuiDraw.in(mx, my, wx, wy, ww, wh)) return false;
        // sidebar
        if (mx < wx + SIDEBAR) {
            int iy = wy + 36;
            for (Category c : Category.values()) {
                if (GuiDraw.in(mx, my, wx + 5, iy, SIDEBAR - 10, 17)) {
                    selected = c;
                    screen.clearSearch();
                    Sounds.click();
                    return true;
                }
                iy += 19;
            }
            if (GuiDraw.in(mx, my, wx + 5, wy + wh - 24, SIDEBAR - 10, 17)) { showThemes = !showThemes; Sounds.click(); }
            return true;
        }
        boolean searching = !query.isBlank();
        List<Module> list = searching ? search(query) : modules(selected);
        int cx = wx + SIDEBAR + 12, cw = ww - SIDEBAR - 24;
        int cols = columns(), cardW = (cw - (cols - 1) * GAP) / cols;
        int top = wy + HEADER + 8, viewH = wh - HEADER - 14;
        if (my < top || my >= top + viewH) return true;
        double sc = searching ? searchScroll : scroll.getOrDefault(selected, 0.0);
        for (int i = 0; i < list.size(); i++) {
            int x = cx + (i % cols) * (cardW + GAP), y = top + (i / cols) * (CARD_H + GAP) - (int) sc;
            if (!GuiDraw.in(mx, my, x, y, cardW, CARD_H)) continue;
            Module m = list.get(i);
            boolean gear = mx >= x + cardW - 34 && mx < x + cardW - 20;
            if (button == 1 || button == 2 || gear) { screen.toggleSettings(m, (int) mx, (int) my); Sounds.click(); }
            else if (button == 0) { m.toggle(); Sounds.toggle(m.isEnabled()); }
            return true;
        }
        return true;
    }

    boolean mouseScrolled(double mx, double my, double amount, int vw, int vh, String query) {
        layout(vw, vh);
        if (!GuiDraw.in(mx, my, wx, wy, ww, wh)) return false;
        double step = amount * (CARD_H + GAP) * 1.2;
        if (!query.isBlank()) searchScroll -= step;
        else scroll.put(selected, scroll.getOrDefault(selected, 0.0) - step);
        return true;
    }

    boolean isOver(double mx, double my, int vw, int vh) {
        layout(vw, vh);
        return GuiDraw.in(mx, my, wx, wy, ww, wh);
    }

    /** Settings windows open beside the window rather than over the cards when there is room. */
    int settingsX(int mx, int vw) {
        int right = wx + ww + 8;
        return right + SettingsWindow.WIDTH < vw - (showThemes ? ThemesPanel.WIDTH + 8 : 0) ? right : mx + 12;
    }
}
