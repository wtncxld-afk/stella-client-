package dev.stella.client.gui;

import dev.stella.client.config.ConfigManager;
import dev.stella.client.gui.theme.Theme;
import dev.stella.client.gui.theme.ThemeManager;
import dev.stella.client.gui.widget.TextInput;
import dev.stella.client.notification.Notification;
import dev.stella.client.notification.NotificationManager;
import dev.stella.client.render.Render2D;
import dev.stella.client.util.Animation;
import dev.stella.client.util.ColorUtil;
import dev.stella.client.util.Sounds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Bottom "CONFIGS" pill and its profile manager popup. */
public class ConfigsPopup {
    private static final int W = 176;
    private static final float TEXT = 0.75f;
    private final TextInput name = new TextInput("", 32, "profile name");
    private final Animation open = new Animation(0, 16);
    private boolean shown;
    private long resetArmedUntil;
    private final List<int[]> rects = new ArrayList<>();
    private final List<Runnable> actions = new ArrayList<>();
    private int px, py, ph;

    boolean isShown() { return shown; }

    void render(DrawContext ctx, int mx, int my, int sw, int sh, double alpha) {
        Theme t = ThemeManager.get();
        rects.clear();
        actions.clear();
        // pill button
        String label = "⚙ CONFIGS";
        int bw = GuiDraw.smallWidth(label, 0.8f) + 16, bx = sw / 2 - bw / 2, by = sh - 18;
        boolean bh = GuiDraw.in(mx, my, bx, by, bw, 13);
        Render2D.shadow(ctx, bx, by, bw, 13, 6.5f, 3, ColorUtil.mulAlpha(0x80000000, alpha));
        Render2D.roundRect(ctx, bx, by, bw, 13, 6.5f, ColorUtil.mulAlpha(shown || bh ? t.accent() : t.header(), alpha));
        GuiDraw.small(ctx, label, bx + 8, by + 3.5f, 0.8f, ColorUtil.mulAlpha(shown || bh ? 0xFFFFFFFF : t.text(), alpha));
        click(bx, by, bw, 13, () -> { shown = !shown; if (!shown) TextInput.unfocusAll(); });

        double a = open.target(shown ? 1 : 0).get() * alpha;
        if (a < 0.02) return;
        List<String> profiles = ConfigManager.listProfiles();
        ph = 20 + profiles.size() * 12 + 18 + 15 * 3 + 4;
        px = sw / 2 - W / 2;
        py = by - 6 - ph + (int) ((1 - a) * 10);
        Render2D.shadow(ctx, px, py, W, ph, 6, 5, ColorUtil.mulAlpha(0x90000000, a));
        Render2D.roundRect(ctx, px, py, W, ph, 6, ColorUtil.mulAlpha(t.background(), a));
        Render2D.roundRect(ctx, px, py, W, 16, 6, ColorUtil.mulAlpha(t.header(), a));
        ctx.fill(px, py + 12, px + W, py + 16, ColorUtil.mulAlpha(t.header(), a));
        GuiDraw.small(ctx, "CONFIGS", px + 8, py + 5.5f, 0.85f, ColorUtil.mulAlpha(t.text(), a));
        String act = "ACTIVE: " + ConfigManager.activeProfile().toUpperCase(Locale.ROOT);
        GuiDraw.small(ctx, act, px + W - 8 - GuiDraw.smallWidth(act, 0.65f), py + 6, 0.65f, ColorUtil.mulAlpha(t.accentSoft(), a));
        int cy = py + 19;
        for (String p : profiles) {
            boolean active = p.equals(ConfigManager.activeProfile());
            boolean hover = GuiDraw.in(mx, my, px + 4, cy, W - 8, 11);
            if (active) Render2D.roundRect(ctx, px + 4, cy, W - 8, 11, 3, ColorUtil.mulAlpha(ColorUtil.withAlpha(t.accent(), 0x50), a));
            else if (hover) Render2D.roundRect(ctx, px + 4, cy, W - 8, 11, 3, ColorUtil.mulAlpha(t.rowHover(), a));
            GuiDraw.small(ctx, p.toUpperCase(Locale.ROOT), px + 9, cy + 3, TEXT, ColorUtil.mulAlpha(active ? t.text() : t.textDim(), a));
            String r = active ? "ACTIVE" : "LOAD";
            GuiDraw.small(ctx, r, px + W - 9 - GuiDraw.smallWidth(r, 0.65f), cy + 3.5f, 0.65f, ColorUtil.mulAlpha(active ? t.accentSoft() : t.textDim(), a));
            final String n = p;
            click(px + 4, cy, W - 8, 11, () -> {
                if (!n.equals(ConfigManager.activeProfile())) {
                    ConfigManager.switchProfile(n);
                    NotificationManager.post("Profile", "Loaded '" + n + "'", Notification.Type.SUCCESS, 2000);
                }
            });
            cy += 12;
        }
        cy += 2;
        name.render(ctx, px + 4, cy, W - 8, 13, ColorUtil.mulAlpha(0xFF0C0B10, a), t.accent(), ColorUtil.mulAlpha(t.text(), a), ColorUtil.mulAlpha(t.textDim(), a));
        click(px + 4, cy, W - 8, 13, name::focus);
        cy += 16;
        int half = (W - 12) / 2;
        cy = row(ctx, cy, mx, my, a, t, half,
                "NEW", () -> {
                    String n = name.get().isBlank() ? "profile-" + (ConfigManager.listProfiles().size() + 1) : name.get();
                    ConfigManager.createProfile(n, true);
                    name.set("");
                    NotificationManager.post("Profile", "Created '" + ConfigManager.activeProfile() + "' (copy of current)", Notification.Type.SUCCESS, 2500);
                },
                "SAVE", () -> {
                    ConfigManager.save();
                    NotificationManager.post("Config", "Saved '" + ConfigManager.activeProfile() + "'", Notification.Type.SUCCESS, 2000);
                });
        cy = row(ctx, cy, mx, my, a, t, half,
                "EXPORT", () -> {
                    try {
                        var path = ConfigManager.exportProfile();
                        MinecraftClient.getInstance().keyboard.setClipboard(ConfigManager.exportString());
                        NotificationManager.post("Exported", "Clipboard + exports/" + path.getFileName(), Notification.Type.SUCCESS, 3500);
                    } catch (Exception e) {
                        NotificationManager.post("Export failed", String.valueOf(e.getMessage()), Notification.Type.ALERT);
                    }
                },
                "IMPORT", () -> {
                    try {
                        String clip = MinecraftClient.getInstance().keyboard.getClipboard();
                        String base = name.get().isBlank() ? "imported" : name.get();
                        String n = ConfigManager.importString(clip, base);
                        NotificationManager.post("Imported", "Clipboard -> '" + n + "' (click it to load)", Notification.Type.SUCCESS, 3500);
                    } catch (Exception e) {
                        NotificationManager.post("Import failed", "Clipboard is not a Stella profile", Notification.Type.ALERT);
                    }
                });
        boolean armed = System.currentTimeMillis() < resetArmedUntil;
        row(ctx, cy, mx, my, a, t, half,
                "DELETE", () -> {
                    String n = name.get().trim();
                    if (n.isEmpty()) { NotificationManager.post("Profile", "Type the profile name to delete", Notification.Type.WARNING, 2500); return; }
                    boolean ok = ConfigManager.deleteProfile(n);
                    NotificationManager.post("Profile", ok ? "Deleted '" + n + "'" : "Cannot delete '" + n + "' (active or missing)",
                            ok ? Notification.Type.SUCCESS : Notification.Type.WARNING, 2500);
                    if (ok) name.set("");
                },
                armed ? "CONFIRM?" : "RESET", () -> {
                    if (System.currentTimeMillis() < resetArmedUntil) {
                        ConfigManager.resetToDefaults();
                        resetArmedUntil = 0;
                        NotificationManager.post("Config", "Profile reset to defaults", Notification.Type.WARNING, 2500);
                    } else resetArmedUntil = System.currentTimeMillis() + 3000;
                });
    }

    private int row(DrawContext ctx, int cy, int mx, int my, double a, Theme t, int half, String l1, Runnable r1, String l2, Runnable r2) {
        btn(ctx, px + 4, cy, half, mx, my, a, t, l1, r1);
        btn(ctx, px + 8 + half, cy, half, mx, my, a, t, l2, r2);
        return cy + 15;
    }

    private void btn(DrawContext ctx, int x, int y, int w, int mx, int my, double a, Theme t, String label, Runnable r) {
        boolean hover = GuiDraw.in(mx, my, x, y, w, 12);
        Render2D.roundRect(ctx, x, y, w, 12, 3, ColorUtil.mulAlpha(hover ? t.accent() : t.row(), a));
        GuiDraw.small(ctx, label, x + w / 2f - GuiDraw.smallWidth(label, TEXT) / 2f, y + 3.5f, TEXT, ColorUtil.mulAlpha(hover ? 0xFFFFFFFF : t.text(), a));
        click(x, y, w, 12, r);
    }

    private void click(int x, int y, int w, int h, Runnable r) {
        rects.add(new int[]{x, y, w, h});
        actions.add(r);
    }

    boolean mouseClicked(double mx, double my) {
        for (int i = 0; i < rects.size(); i++) {
            int[] r = rects.get(i);
            if (GuiDraw.in(mx, my, r[0], r[1], r[2], r[3])) {
                TextInput.unfocusAll();
                actions.get(i).run();
                Sounds.click();
                return true;
            }
        }
        if (shown && GuiDraw.in(mx, my, px, py, W, ph)) return true;
        return false;
    }

    boolean keyPressed(int key) { return name.keyPressed(key, 0); }
    boolean charTyped(char c) { return name.charTyped(c); }
}
