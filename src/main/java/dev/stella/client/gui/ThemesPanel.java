package dev.stella.client.gui;

import dev.stella.client.config.ConfigManager;
import dev.stella.client.gui.theme.Theme;
import dev.stella.client.gui.theme.ThemeManager;
import dev.stella.client.hud.HudEditorScreen;
import dev.stella.client.module.ModuleManager;
import dev.stella.client.modules.client.ClickGuiModule;
import dev.stella.client.modules.settings.ConfigModule;
import dev.stella.client.render.Render2D;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.util.ColorUtil;
import dev.stella.client.util.Sounds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Right-hand THEMES / SOUNDS / CLIENT panel. */
public class ThemesPanel {
    static final int WIDTH = 112;
    private static final float TEXT = 0.75f;
    private final List<int[]> rects = new ArrayList<>();
    private final List<Runnable> actions = new ArrayList<>();
    private boolean listeningGuiKey;
    private boolean draggingVolume;
    private int volX, volW;
    private double scroll;
    private int contentHeight;

    int x(int sw) { return sw - WIDTH - 6; }

    void render(DrawContext ctx, int mx, int my, int sw, int sh, double a, ClickGuiScreen screen) {
        Theme t = ThemeManager.get();
        ClickGuiModule gui = ModuleManager.get(ClickGuiModule.class);
        rects.clear();
        actions.clear();
        int x = x(sw), y = 6;
        int h = Math.min(sh - 40, contentHeight + 20);
        Render2D.shadow(ctx, x, y, WIDTH, h, 5, 4, ColorUtil.mulAlpha(0x80000000, a));
        Render2D.roundRect(ctx, x, y, WIDTH, h, 5, ColorUtil.mulAlpha(t.background(), a));
        Render2D.roundRect(ctx, x, y, WIDTH, 16, 5, ColorUtil.mulAlpha(t.header(), a));
        ctx.fill(x, y + 12, x + WIDTH, y + 16, ColorUtil.mulAlpha(t.header(), a));
        GuiDraw.text(ctx, "◐", x + 6, y + 4, ColorUtil.mulAlpha(t.accent(), a));
        GuiDraw.small(ctx, "THEMES", x + 16, y + 5.5f, 0.85f, ColorUtil.mulAlpha(t.text(), a));

        ctx.enableScissor(x, y + 16, x + WIDTH, y + h);
        int cy = y + 19 - (int) scroll;
        int start = cy;
        // two-column swatch grid
        int colW = (WIDTH - 8) / 2, col = 0;
        for (int i = 0; i < Theme.NAMES.length; i++) {
            String name = Theme.NAMES[i];
            if (name.equals(Theme.CUSTOM) && !gui.theme.is(Theme.CUSTOM)) continue;
            boolean sel = gui.theme.is(name);
            boolean animated = name.equals(Theme.RAINBOW) || Theme.animatedHues(name) != null;
            int dot = name.equals(Theme.RAINBOW) ? ThemeManager.accentAt(0.33) | 0xFF000000
                    : name.equals(Theme.CUSTOM) ? gui.customAccent.argb() | 0xFF000000 : Theme.ACCENTS[i];
            if (animated && !name.equals(Theme.RAINBOW)) {
                float[] rng = Theme.animatedHues(name);
                double wave = 0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 1800.0);
                dot = ColorUtil.fromHsb((float) (rng[0] + (rng[1] - rng[0]) * wave), 0.6f, 1f, 255);
            }
            int cx = x + 3 + col * (colW + 2);
            boolean hover = GuiDraw.in(mx, my, cx, cy, colW, 11);
            if (sel) Render2D.roundRect(ctx, cx, cy, colW, 11, 3, ColorUtil.mulAlpha(ColorUtil.withAlpha(t.accent(), 0x40), a));
            else if (hover) Render2D.roundRect(ctx, cx, cy, colW, 11, 3, ColorUtil.mulAlpha(t.rowHover(), a));
            GuiDraw.radio(ctx, cx + 4, cy + 2, dot, sel, a);
            GuiDraw.small(ctx, name.toUpperCase(Locale.ROOT), cx + 14, cy + 3.5f, 0.66f, ColorUtil.mulAlpha(sel ? t.text() : t.textDim(), a));
            final String pick = name;
            click(cx, cy, colW, 11, () -> gui.theme.set(pick));
            if (++col == 2) { col = 0; cy += 12; }
        }
        if (col != 0) cy += 12;
        // + ADD CUSTOM
        boolean ch = GuiDraw.in(mx, my, x + 3, cy, WIDTH - 6, 11);
        Render2D.roundRect(ctx, x + 3, cy, WIDTH - 6, 11, 3, ColorUtil.mulAlpha(ch ? t.rowHover() : t.row(), a));
        String add = gui.theme.is(Theme.CUSTOM) ? "✎ EDIT CUSTOM" : "+ ADD CUSTOM";
        GuiDraw.small(ctx, add, x + WIDTH / 2f - GuiDraw.smallWidth(add, TEXT) / 2f, cy + 3, TEXT, ColorUtil.mulAlpha(t.accentSoft(), a));
        click(x + 3, cy, WIDTH - 6, 11, () -> {
            gui.theme.set(Theme.CUSTOM);
            screen.openSettingsFor(gui.getName());
        });
        cy += 16;

        cy = section(ctx, "SOUNDS", x, cy, t, a);
        // master volume slider
        GuiDraw.small(ctx, "MASTER VOLUME", x + 7, cy, TEXT, ColorUtil.mulAlpha(t.textDim(), a));
        String pct = (int) Math.round(gui.volume.get() * 100) + "%";
        GuiDraw.small(ctx, pct, x + WIDTH - 7 - GuiDraw.smallWidth(pct, TEXT), cy, TEXT, ColorUtil.mulAlpha(t.text(), a));
        cy += 8;
        volX = x + 7;
        volW = WIDTH - 14;
        Render2D.roundRect(ctx, volX, cy + 1, volW, 3, 1.5f, ColorUtil.mulAlpha(0xFF2A2533, a));
        Render2D.roundRect(ctx, volX, cy + 1, (float) (volW * gui.volume.get()), 3, 1.5f, ColorUtil.mulAlpha(t.accent(), a));
        Render2D.roundRect(ctx, (float) (volX + volW * gui.volume.get() - 2.5), cy - 0.5f, 5, 6, 2.5f, ColorUtil.mulAlpha(0xFFFFFFFF, a));
        click(volX - 2, cy - 2, volW + 4, 9, () -> draggingVolume = true);
        cy += 9;
        cy = check(ctx, "GUI OPEN/CLOSE", gui.soundOpen, x, cy, mx, my, t, a);
        cy = check(ctx, "HOVER", gui.soundHover, x, cy, mx, my, t, a);
        cy = check(ctx, "CLICKS & TOGGLES", gui.soundClick, x, cy, mx, my, t, a);
        cy = check(ctx, "NOTIFICATIONS", gui.soundNotify, x, cy, mx, my, t, a);
        cy += 5;

        cy = section(ctx, "CLIENT", x, cy, t, a);
        cy = check(ctx, "BLUR BACKGROUND", gui.blur, x, cy, mx, my, t, a);
        cy = check(ctx, "CUSTOM FONT", gui.customFont, x, cy, mx, my, t, a);
        cy = check(ctx, "UPPERCASE NAMES", gui.uppercase, x, cy, mx, my, t, a);
        cy = check(ctx, "DESCRIPTIONS", gui.descriptions, x, cy, mx, my, t, a);
        cy = check(ctx, "AUTO SAVE", ModuleManager.get(ConfigModule.class).autoSave, x, cy, mx, my, t, a);
        cy = button(ctx, "GUI KEY: " + (listeningGuiKey ? "PRESS..." : gui.keybind.keyName().toUpperCase(Locale.ROOT)), x, cy, mx, my, t, a,
                () -> listeningGuiKey = !listeningGuiKey);
        cy = button(ctx, "▣ HUD EDITOR", x, cy, mx, my, t, a,
                () -> MinecraftClient.getInstance().setScreen(new HudEditorScreen(MinecraftClient.getInstance().currentScreen)));
        cy += 2;
        GuiDraw.small(ctx, "PROFILE: " + ConfigManager.activeProfile().toUpperCase(Locale.ROOT), x + 7, cy, 0.65f, ColorUtil.mulAlpha(t.textDim(), a));
        cy += 8;
        contentHeight = cy - start;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - (h - 20))));
        ctx.disableScissor();
    }

    private int section(DrawContext ctx, String title, int x, int cy, Theme t, double a) {
        GuiDraw.small(ctx, title, x + 7, cy, 0.8f, ColorUtil.mulAlpha(t.accent(), a));
        int tw = GuiDraw.smallWidth(title, 0.8f);
        ctx.fill(x + 10 + tw, cy + 3, x + WIDTH - 7, cy + 4, ColorUtil.mulAlpha(t.outline(), a));
        return cy + 10;
    }

    private int check(DrawContext ctx, String label, BoolSetting s, int x, int cy, int mx, int my, Theme t, double a) {
        boolean hover = GuiDraw.in(mx, my, x + 3, cy - 1, WIDTH - 6, 10);
        if (hover) Render2D.roundRect(ctx, x + 3, cy - 1, WIDTH - 6, 10, 2, ColorUtil.mulAlpha(t.rowHover(), a));
        GuiDraw.small(ctx, label, x + 7, cy + 1.5f, TEXT, ColorUtil.mulAlpha(s.isOn() ? t.text() : t.textDim(), a));
        GuiDraw.checkbox(ctx, t, x + WIDTH - 15, cy, s.isOn(), a);
        click(x + 3, cy - 1, WIDTH - 6, 10, s::toggle);
        return cy + 10;
    }

    private int button(DrawContext ctx, String label, int x, int cy, int mx, int my, Theme t, double a, Runnable r) {
        boolean hover = GuiDraw.in(mx, my, x + 3, cy, WIDTH - 6, 12);
        Render2D.roundRect(ctx, x + 3, cy, WIDTH - 6, 12, 3, ColorUtil.mulAlpha(hover ? t.accent() : t.row(), a));
        GuiDraw.small(ctx, label, x + WIDTH / 2f - GuiDraw.smallWidth(label, TEXT) / 2f, cy + 3.5f, TEXT, ColorUtil.mulAlpha(hover ? 0xFFFFFFFF : t.text(), a));
        click(x + 3, cy, WIDTH - 6, 12, r);
        return cy + 14;
    }

    private void click(int x, int y, int w, int h, Runnable r) {
        rects.add(new int[]{x, y, w, h});
        actions.add(r);
    }

    boolean isOver(double mx, double my, int sw, int sh) {
        return GuiDraw.in(mx, my, x(sw), 6, WIDTH, Math.min(sh - 40, contentHeight + 20));
    }

    boolean mouseClicked(double mx, double my, int sw, int sh) {
        if (!isOver(mx, my, sw, sh)) return false;
        for (int i = 0; i < rects.size(); i++) {
            int[] r = rects.get(i);
            if (GuiDraw.in(mx, my, r[0], r[1], r[2], r[3])) {
                actions.get(i).run();
                if (draggingVolume) updateVolume(mx);
                Sounds.click();
                return true;
            }
        }
        return true;
    }

    boolean mouseDragged(double mx) {
        if (!draggingVolume) return false;
        updateVolume(mx);
        return true;
    }

    void mouseReleased() { draggingVolume = false; }

    private void updateVolume(double mx) {
        ClickGuiModule gui = ModuleManager.get(ClickGuiModule.class);
        gui.volume.set(Math.max(0, Math.min(1, (mx - volX) / volW)));
    }

    boolean mouseScrolled(double mx, double my, double amount, int sw, int sh) {
        if (!isOver(mx, my, sw, sh)) return false;
        scroll -= amount * 15;
        return true;
    }

    boolean isListening() { return listeningGuiKey; }

    boolean keyPressed(int key) {
        if (!listeningGuiKey) return false;
        ClickGuiModule gui = ModuleManager.get(ClickGuiModule.class);
        if (key != GLFW.GLFW_KEY_ESCAPE) gui.keybind.set(key);
        listeningGuiKey = false;
        return true;
    }
}
