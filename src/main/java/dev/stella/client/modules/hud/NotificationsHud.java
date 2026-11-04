package dev.stella.client.modules.hud;

import dev.stella.client.gui.theme.ThemeManager;
import dev.stella.client.hud.HudElement;
import dev.stella.client.notification.Notification;
import dev.stella.client.notification.NotificationManager;
import dev.stella.client.render.Render2D;
import dev.stella.client.setting.ColorSetting;
import dev.stella.client.setting.NumberSetting;
import dev.stella.client.util.Animation;
import dev.stella.client.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

public class NotificationsHud extends HudElement {
    private final NumberSetting cardWidth = add(new NumberSetting("Width", "Toast width", 160, 110, 260, 5));
    private final ColorSetting background = add(new ColorSetting("Background", "Toast background", 0xE00E0C12));
    private final ColorSetting textColor = add(new ColorSetting("Text Color", "Message colour", 0xFFE6E1EE));
    private final dev.stella.client.setting.BoolSetting togglePopups = add(new dev.stella.client.setting.BoolSetting(
            "Module Toggle Popups", "Show a popup whenever a module is turned on or off", true))
            .onChange(() -> NotificationManager.setTogglePopups(this.togglePopups.isOn()));

    public NotificationsHud() {
        super("Notifications HUD", "Toasts for module alerts (falls back to chat when disabled)", -4, -40, true);
        NotificationManager.setHudActiveSupplier(this::isEnabled);
    }

    @Override
    public void render(DrawContext ctx, float tickDelta, boolean editor) {
        List<Notification> list = NotificationManager.active();
        if (editor && list.isEmpty()) {
            list = List.of(new Notification("Stella", "Notifications appear here", Notification.Type.INFO, System.currentTimeMillis(), 100000));
        }
        int w = cardWidth.getInt(), h = 28, y = 0;
        long now = System.currentTimeMillis();
        NotificationManager.setTogglePopups(togglePopups.isOn());
        for (Notification n : list) {
            float p = n.progress(now);
            if (n.type() == Notification.Type.ENABLED || n.type() == Notification.Type.DISABLED) {
                y = togglePill(ctx, n, now, w, y);
                continue;
            }
            double slide = Animation.easeOutBack(Math.min(1, (now - n.createdAt()) / 380.0));
            double fade = p > 0.9 ? (1 - p) / 0.1 : 1;
            double exit = p > 0.86 ? Math.pow((p - 0.86) / 0.14, 2) : 0; // accelerate back out to the side
            float ox = (float) ((1 - slide) * (w + 10) + exit * (w + 10));
            int accent = switch (n.type()) {
                case SUCCESS -> 0xFF6EE7A0;
                case WARNING -> 0xFFF5D76E;
                case ALERT -> 0xFFF2777A;
                default -> ThemeManager.get().accent();
            };
            String icon = switch (n.type()) {
                case SUCCESS -> "✔";
                case WARNING -> "⚠";
                case ALERT -> "!";
                default -> "✦";
            };
            Render2D.shadow(ctx, ox, y, w, h, 5, 3, col(ColorUtil.mulAlpha(0x70000000, fade)));
            Render2D.roundRect(ctx, ox, y, w, h, 5, col(ColorUtil.mulAlpha(background.argb(), fade)));
            // icon bubble
            Render2D.roundRect(ctx, ox + 5, y + 5, 18, 18, 9, col(ColorUtil.mulAlpha(ColorUtil.withAlpha(accent, 0x40), fade)));
            dev.stella.client.render.StellaFont.draw(ctx, icon, (int) (ox + 14 - dev.stella.client.render.StellaFont.width(icon) / 2f), y + 10, col(ColorUtil.mulAlpha(accent, fade)), false);
            dev.stella.client.render.StellaFont.draw(ctx, trim(n.title(), w - 34), (int) ox + 28, y + 5, col(ColorUtil.mulAlpha(accent, fade)), true);
            dev.stella.client.render.StellaFont.draw(ctx, trim(n.message(), w - 34), (int) ox + 28, y + 16, col(ColorUtil.mulAlpha(textColor.argb(), fade)), false);
            // progress line
            Render2D.rect(ctx, ox + 5, y + h - 2, (w - 10) * (1 - p), 1, col(ColorUtil.mulAlpha(accent, fade * 0.8)));
            y += h + 4;
        }
        setSize(w, Math.max(h, y - 4));
    }

    /** Compact pill: "● MODULE NAME   ENABLED". */
    private int togglePill(DrawContext ctx, Notification n, long now, int w, int y) {
        float p = n.progress(now);
        double slide = Animation.easeOutBack(Math.min(1, (now - n.createdAt()) / 320.0));
        double fade = p > 0.8 ? (1 - p) / 0.2 : 1;
        boolean on = n.type() == Notification.Type.ENABLED;
        int accent = on ? ThemeManager.get().accent() : 0xFF8C8696;
        String name = n.title().toUpperCase(java.util.Locale.ROOT);
        String state = on ? "ENABLED" : "DISABLED";
        int pw = Math.min(w, 22 + dev.stella.client.render.StellaFont.width(name) + 8 + dev.stella.client.render.StellaFont.width(state) + 8);
        float ox = (float) (w - pw + (1 - slide) * (pw + 10));
        int h = 16;
        Render2D.shadow(ctx, ox, y, pw, h, 8, 3, col(ColorUtil.mulAlpha(0x60000000, fade)));
        Render2D.roundRect(ctx, ox, y, pw, h, 8, col(ColorUtil.mulAlpha(background.argb(), fade)));
        Render2D.roundRect(ctx, ox + 5, y + 5, 6, 6, 3, col(ColorUtil.mulAlpha(accent, fade)));
        dev.stella.client.render.StellaFont.draw(ctx, name, (int) ox + 16, y + 4, col(ColorUtil.mulAlpha(textColor.argb(), fade)), false);
        dev.stella.client.render.StellaFont.draw(ctx, state, (int) (ox + pw - 8 - dev.stella.client.render.StellaFont.width(state)), y + 4,
                col(ColorUtil.mulAlpha(on ? accent : 0xFFB0A8BC, fade)), false);
        return y + h + 3;
    }

    private String trim(String s, int w) {
        if (dev.stella.client.render.StellaFont.width(s) <= w) return s;
        return dev.stella.client.render.StellaFont.trimToWidth(s, w - 6) + "..";
    }
}
