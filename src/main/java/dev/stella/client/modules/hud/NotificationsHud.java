package dev.stella.client.modules.hud;

import dev.stella.client.gui.theme.ThemeManager;
import dev.stella.client.hud.HudElement;
import dev.stella.client.notification.Notification;
import dev.stella.client.notification.NotificationManager;
import dev.stella.client.setting.ColorSetting;
import dev.stella.client.util.Animation;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

public class NotificationsHud extends HudElement {
    private final ColorSetting background = add(new ColorSetting("Background", "Toast background", 0xE00D0B12));
    private final ColorSetting textColor = add(new ColorSetting("Text Color", "Message colour", 0xFFE6E1EE));

    public NotificationsHud() {
        super("Notifications HUD", "Toasts for module alerts (falls back to chat when disabled)", 380, 200, true);
        NotificationManager.setHudActiveSupplier(this::isEnabled);
    }

    @Override
    public void render(DrawContext ctx, float tickDelta, boolean editor) {
        List<Notification> list = NotificationManager.active();
        if (editor && list.isEmpty()) {
            list = List.of(new Notification("Stella", "Notifications appear here", Notification.Type.INFO, System.currentTimeMillis(), 100000));
        }
        int w = 150, h = 26, y = 0;
        long now = System.currentTimeMillis();
        for (Notification n : list) {
            float p = n.progress(now);
            double slide = Animation.easeOutCubic(Math.min(1, (now - n.createdAt()) / 250.0));
            double fade = p > 0.9 ? (1 - p) / 0.1 : 1;
            int ox = (int) ((1 - slide) * 40);
            int accent = switch (n.type()) {
                case SUCCESS -> 0xFF7CF29A;
                case WARNING -> 0xFFF5D76E;
                case ALERT -> 0xFFF2777A;
                default -> ThemeManager.get().accent();
            };
            ctx.fill(ox, y, ox + w, y + h, col(mul(background.argb(), fade)));
            ctx.fill(ox, y, ox + 2, y + h, col(mul(accent, fade)));
            ctx.fill(ox + 2, y + h - 1, ox + 2 + (int) ((w - 2) * (1 - p)), y + h, col(mul(accent, fade * 0.7)));
            ctx.drawText(mc.textRenderer, trim(n.title(), w - 10), ox + 6, y + 3, col(mul(accent, fade)), true);
            ctx.drawText(mc.textRenderer, trim(n.message(), w - 10), ox + 6, y + 14, col(mul(textColor.argb(), fade)), false);
            y += h + 3;
        }
        setSize(w, Math.max(h, y - 3));
    }

    private static int mul(int c, double f) { return dev.stella.client.util.ColorUtil.mulAlpha(c, f); }

    private String trim(String s, int w) {
        if (mc.textRenderer.getWidth(s) <= w) return s;
        return mc.textRenderer.trimToWidth(s, w - 6) + "...";
    }
}
