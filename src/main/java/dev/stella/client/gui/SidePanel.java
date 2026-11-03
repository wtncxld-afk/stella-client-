package dev.stella.client.gui;

import dev.stella.client.config.ConfigManager;
import dev.stella.client.gui.theme.Theme;
import dev.stella.client.gui.theme.ThemeManager;
import dev.stella.client.gui.widget.TextInput;
import dev.stella.client.hud.HudEditorScreen;
import dev.stella.client.module.ModuleManager;
import dev.stella.client.modules.client.ClickGuiModule;
import dev.stella.client.modules.hud.BrandingHud;
import dev.stella.client.notification.Notification;
import dev.stella.client.notification.NotificationManager;
import dev.stella.client.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Right-hand theme / profile / client settings panel. */
public class SidePanel {
    static final int WIDTH = 132;
    private final TextInput profileName = new TextInput("", 32, "new profile name");
    private boolean listeningGuiKey;
    private long resetArmedUntil;
    private double scroll;
    private final List<Runnable> clickActions = new ArrayList<>();
    private final List<int[]> clickRects = new ArrayList<>();

    int x(int sw) { return sw - WIDTH - 6; }

    boolean isListening() { return listeningGuiKey; }

    void render(DrawContext ctx, int mx, int my, int sw, int sh, double alpha) {
        Theme t = ThemeManager.get();
        clickActions.clear();
        clickRects.clear();
        int x = x(sw), y = 6;
        int h = sh - 12;
        ctx.fill(x, y, x + WIDTH, y + h, ColorUtil.mulAlpha(t.background(), alpha));
        ctx.fill(x, y, x + WIDTH, y + 22, ColorUtil.mulAlpha(t.header(), alpha));
        ctx.fill(x, y + 21, x + WIDTH, y + 22, ColorUtil.mulAlpha(t.accent(), alpha));
        ctx.drawTexture(RenderLayer::getGuiTextured, BrandingHud.LOGO, x + 5, y + 3, 0f, 0f, 16, 16, 16, 16, ColorUtil.withAlpha(0xFFFFFF, (int) (255 * alpha)));
        GuiDraw.text(ctx, "STELLA", x + 24, y + 4, ColorUtil.mulAlpha(t.accent(), alpha));
        GuiDraw.text(ctx, "client " + dev.stella.client.StellaClient.VERSION, x + 24, y + 12, ColorUtil.mulAlpha(t.textDim(), alpha));

        ctx.enableScissor(x, y + 22, x + WIDTH, y + h);
        int cy = y + 26 - (int) scroll;
        cy = section(ctx, "THEME", x, cy, t, alpha);
        ClickGuiModule gui = ModuleManager.get(ClickGuiModule.class);
        for (Theme th : Theme.ALL) {
            boolean sel = th == t;
            final Theme pick = th;
            cy = row(ctx, x, cy, mx, my, alpha, t, th.name(), sel ? "●" : "", sel, () -> gui.theme.set(pick.name()), th.accent());
        }
        cy += 4;
        cy = section(ctx, "PROFILES", x, cy, t, alpha);
        for (String p : ConfigManager.listProfiles()) {
            boolean active = p.equals(ConfigManager.activeProfile());
            final String name = p;
            cy = row(ctx, x, cy, mx, my, alpha, t, p, active ? "active" : "load", active, () -> {
                if (!name.equals(ConfigManager.activeProfile())) {
                    ConfigManager.switchProfile(name);
                    NotificationManager.post("Profile", "Loaded '" + name + "'", Notification.Type.SUCCESS, 2000);
                }
            }, 0);
        }
        profileName.render(ctx, x + 4, cy + 1, WIDTH - 8, 12, ColorUtil.mulAlpha(0xFF15121B, alpha), t.accent(), t.text(), t.textDim());
        final int inputY = cy + 1;
        clickRects.add(new int[]{x + 4, inputY, WIDTH - 8, 12});
        clickActions.add(profileName::focus);
        cy += 16;
        int half = (WIDTH - 12) / 2;
        cy = buttons(ctx, x, cy, mx, my, alpha, t,
                "New", () -> {
                    String n = profileName.get().isBlank() ? "profile-" + (ConfigManager.listProfiles().size() + 1) : profileName.get();
                    ConfigManager.createProfile(n, true);
                    profileName.set("");
                    NotificationManager.post("Profile", "Created '" + ConfigManager.activeProfile() + "' (copy of current)", Notification.Type.SUCCESS, 2500);
                },
                "Delete", () -> {
                    String n = profileName.get().trim();
                    if (n.isEmpty()) { NotificationManager.post("Profile", "Type the profile name to delete above", Notification.Type.WARNING, 2500); return; }
                    boolean ok = ConfigManager.deleteProfile(n);
                    NotificationManager.post("Profile", ok ? "Deleted '" + n + "'" : "Cannot delete '" + n + "' (active or missing)", ok ? Notification.Type.SUCCESS : Notification.Type.WARNING, 2500);
                    if (ok) profileName.set("");
                }, half);
        cy = buttons(ctx, x, cy, mx, my, alpha, t,
                "Save", () -> { ConfigManager.save(); NotificationManager.post("Config", "Saved '" + ConfigManager.activeProfile() + "'", Notification.Type.SUCCESS, 2000); },
                "Export", () -> {
                    try {
                        var path = ConfigManager.exportProfile();
                        MinecraftClient.getInstance().keyboard.setClipboard(ConfigManager.exportString());
                        NotificationManager.post("Exported", "Copied to clipboard + " + path.getFileName(), Notification.Type.SUCCESS, 3500);
                    } catch (Exception e) {
                        NotificationManager.post("Export failed", e.getMessage(), Notification.Type.ALERT);
                    }
                }, half);
        cy = buttons(ctx, x, cy, mx, my, alpha, t,
                "Import", () -> {
                    try {
                        String clip = MinecraftClient.getInstance().keyboard.getClipboard();
                        String base = profileName.get().isBlank() ? "imported" : profileName.get();
                        String n = ConfigManager.importString(clip, base);
                        NotificationManager.post("Imported", "Clipboard -> profile '" + n + "' (click it to load)", Notification.Type.SUCCESS, 3500);
                    } catch (Exception e) {
                        NotificationManager.post("Import failed", "Clipboard is not a Stella profile", Notification.Type.ALERT);
                    }
                },
                System.currentTimeMillis() < resetArmedUntil ? "Confirm?" : "Reset", () -> {
                    if (System.currentTimeMillis() < resetArmedUntil) {
                        ConfigManager.resetToDefaults();
                        resetArmedUntil = 0;
                        NotificationManager.post("Config", "Profile reset to defaults", Notification.Type.WARNING, 2500);
                    } else resetArmedUntil = System.currentTimeMillis() + 3000;
                }, half);
        cy += 4;
        cy = section(ctx, "CLIENT", x, cy, t, alpha);
        cy = row(ctx, x, cy, mx, my, alpha, t, "HUD Editor", "open", false,
                () -> MinecraftClient.getInstance().setScreen(new HudEditorScreen(MinecraftClient.getInstance().currentScreen)), 0);
        cy = row(ctx, x, cy, mx, my, alpha, t, "GUI Key", listeningGuiKey ? "press..." : gui.keybind.keyName(), listeningGuiKey,
                () -> listeningGuiKey = !listeningGuiKey, 0);
        cy = row(ctx, x, cy, mx, my, alpha, t, "Auto Save", ConfigManager.isAutoSave() ? "on" : "off", ConfigManager.isAutoSave(), () -> {
            var cm = ModuleManager.get(dev.stella.client.modules.settings.ConfigModule.class);
            cm.autoSave.toggle();
        }, 0);
        cy += 4;
        GuiDraw.text(ctx, "Left: toggle  Right: settings", x + 5, cy, ColorUtil.mulAlpha(t.textDim(), alpha * 0.8));
        GuiDraw.text(ctx, "Right-click header: collapse", x + 5, cy + 9, ColorUtil.mulAlpha(t.textDim(), alpha * 0.8));
        int content = cy + 20 - (y + 26 - (int) scroll);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, content - (h - 26))));
        ctx.disableScissor();
    }

    private int section(DrawContext ctx, String title, int x, int cy, Theme t, double a) {
        GuiDraw.text(ctx, title, x + 5, cy + 1, ColorUtil.mulAlpha(t.accentSoft(), a));
        ctx.fill(x + 6 + GuiDraw.width(title) + 3, cy + 5, x + WIDTH - 5, cy + 6, ColorUtil.mulAlpha(t.outline(), a));
        return cy + 12;
    }

    private int row(DrawContext ctx, int x, int cy, int mx, int my, double a, Theme t, String label, String right, boolean active, Runnable action, int dot) {
        boolean hover = GuiDraw.in(mx, my, x + 2, cy, WIDTH - 4, 12);
        ctx.fill(x + 2, cy, x + WIDTH - 2, cy + 12, ColorUtil.mulAlpha(hover ? t.rowHover() : t.row(), a));
        int tx = x + 6;
        if (dot != 0) { ctx.fill(tx, cy + 3, tx + 6, cy + 9, ColorUtil.mulAlpha(dot, a)); tx += 10; }
        GuiDraw.text(ctx, GuiDraw.trim(label, WIDTH - 50), tx, cy + 2, ColorUtil.mulAlpha(active ? t.text() : t.textDim(), a));
        GuiDraw.text(ctx, right, x + WIDTH - 6 - GuiDraw.width(right), cy + 2, ColorUtil.mulAlpha(active ? t.accent() : t.textDim(), a));
        clickRects.add(new int[]{x + 2, cy, WIDTH - 4, 12});
        clickActions.add(action);
        return cy + 13;
    }

    private int buttons(DrawContext ctx, int x, int cy, int mx, int my, double a, Theme t, String l1, Runnable r1, String l2, Runnable r2, int half) {
        button(ctx, x + 4, cy, half, mx, my, a, t, l1, r1);
        button(ctx, x + 8 + half, cy, half, mx, my, a, t, l2, r2);
        return cy + 15;
    }

    private void button(DrawContext ctx, int bx, int cy, int w, int mx, int my, double a, Theme t, String label, Runnable action) {
        boolean hover = GuiDraw.in(mx, my, bx, cy, w, 13);
        ctx.fill(bx, cy, bx + w, cy + 13, ColorUtil.mulAlpha(hover ? t.accent() : t.row(), a));
        ctx.drawBorder(bx, cy, w, 13, ColorUtil.mulAlpha(t.outline(), a));
        GuiDraw.text(ctx, label, bx + w / 2 - GuiDraw.width(label) / 2, cy + 3, ColorUtil.mulAlpha(hover ? 0xFFFFFFFF : t.text(), a));
        clickRects.add(new int[]{bx, cy, w, 13});
        clickActions.add(action);
    }

    boolean isOver(double mx, double my, int sw, int sh) { return GuiDraw.in(mx, my, x(sw), 6, WIDTH, sh - 12); }

    boolean mouseClicked(double mx, double my, int sw, int sh) {
        if (!isOver(mx, my, sw, sh)) return false;
        if (my < 28) return true;
        for (int i = 0; i < clickRects.size(); i++) {
            int[] r = clickRects.get(i);
            if (GuiDraw.in(mx, my, r[0], r[1], r[2], r[3])) {
                TextInput.unfocusAll();
                clickActions.get(i).run();
                dev.stella.client.util.Sounds.click();
                return true;
            }
        }
        return true;
    }

    boolean mouseScrolled(double mx, double my, double amount, int sw, int sh) {
        if (!isOver(mx, my, sw, sh)) return false;
        scroll -= amount * 15;
        return true;
    }

    boolean keyPressed(int key) {
        if (profileName.keyPressed(key, 0)) return true;
        if (!listeningGuiKey) return false;
        ClickGuiModule gui = ModuleManager.get(ClickGuiModule.class);
        if (key != GLFW.GLFW_KEY_ESCAPE) gui.keybind.set(key);
        listeningGuiKey = false;
        return true;
    }

    boolean charTyped(char c) { return profileName.charTyped(c); }
}
