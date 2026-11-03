package dev.stella.client.notification;

import dev.stella.client.util.ChatUtil;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Central queue for module alerts. The Notifications HUD renders the queue; if
 * that HUD element is disabled, alerts fall back to chat so nothing is lost.
 */
public final class NotificationManager {
    private static final List<Notification> ACTIVE = new ArrayList<>();
    private static final int MAX = 6;
    private static java.util.function.BooleanSupplier hudActive = () -> false;

    private NotificationManager() {}

    public static void setHudActiveSupplier(java.util.function.BooleanSupplier s) { hudActive = s; }

    public static void post(String title, String message, Notification.Type type) {
        post(title, message, type, 4000);
    }

    private static boolean togglePopups = true;

    public static void setTogglePopups(boolean on) { togglePopups = on; }

    /** Compact popup when a module is switched on or off; replaces the previous toggle popup for the same module. */
    public static void moduleToggled(String module, boolean enabled) {
        if (!togglePopups || !hudActive.getAsBoolean()) return;
        MinecraftClient.getInstance().execute(() -> {
            synchronized (ACTIVE) {
                ACTIVE.removeIf(n -> (n.type() == Notification.Type.ENABLED || n.type() == Notification.Type.DISABLED) && n.title().equals(module));
                ACTIVE.add(new Notification(module, enabled ? "Enabled" : "Disabled",
                        enabled ? Notification.Type.ENABLED : Notification.Type.DISABLED, System.currentTimeMillis(), 1600));
                while (ACTIVE.size() > MAX) ACTIVE.remove(0);
            }
        });
    }

    public static void post(String title, String message, Notification.Type type, long durationMs) {
        MinecraftClient.getInstance().execute(() -> {
            if (!hudActive.getAsBoolean()) {
                ChatUtil.info(title + ": " + message);
                return;
            }
            synchronized (ACTIVE) {
                ACTIVE.add(new Notification(title, message, type, System.currentTimeMillis(), durationMs));
                while (ACTIVE.size() > MAX) ACTIVE.remove(0);
            }
        });
    }

    public static List<Notification> active() {
        long now = System.currentTimeMillis();
        synchronized (ACTIVE) {
            ACTIVE.removeIf(n -> n.expired(now));
            return new ArrayList<>(ACTIVE);
        }
    }
}
