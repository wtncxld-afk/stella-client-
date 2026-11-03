package dev.stella.client.modules.world;

import dev.stella.client.mixin.WorldAccessor;
import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.notification.Notification;
import dev.stella.client.notification.NotificationManager;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.util.ChatUtil;
import dev.stella.client.util.Sounds;

/**
 * Alerts when rain or a thunderstorm starts or stops, using the weather state
 * the server sends to the client (Weather Customizer's visual override does not
 * affect this). Servers do not send forecasts, so changes are reported as they
 * happen, never in advance.
 */
public class WeatherNotifier extends Module {
    private final BoolSetting rainStart = add(new BoolSetting("Rain Start", "Notify when rain starts", true));
    private final BoolSetting rainStop = add(new BoolSetting("Rain Stop", "Notify when rain stops", true));
    private final BoolSetting thunderStart = add(new BoolSetting("Thunder Start", "Notify when a thunderstorm starts", true));
    private final BoolSetting thunderStop = add(new BoolSetting("Thunder Stop", "Notify when a thunderstorm ends", true));
    private final BoolSetting sound = add(new BoolSetting("Sound", "Play a sound with each alert", true));
    private final BoolSetting chat = add(new BoolSetting("Chat Message", "Also print alerts in chat", false));

    private Boolean raining, thundering;
    private Object lastWorld;

    public WeatherNotifier() { super("Weather Notifier", "Alerts when rain or thunderstorms start and stop", Category.WORLD); }

    @Override protected void onEnable() { raining = null; thundering = null; }

    @Override
    public void onTick() {
        if (!inGame()) { raining = null; thundering = null; return; }
        if (mc.world != lastWorld) { lastWorld = mc.world; raining = null; thundering = null; }
        WorldAccessor w = (WorldAccessor) mc.world;
        boolean rain = w.stella$rainGradient() > 0.2f;
        boolean thunder = rain && w.stella$thunderGradient() > 0.9f;
        if (raining != null) {
            if (rain && !raining && rainStart.isOn()) alert("🌧 Rain started", Notification.Type.INFO, 1.2f);
            if (!rain && raining && rainStop.isOn()) alert("☀ Rain stopped", Notification.Type.SUCCESS, 1.6f);
            if (thunder && !thundering && thunderStart.isOn()) alert("⚡ Thunderstorm started", Notification.Type.WARNING, 0.7f);
            if (!thunder && thundering && thunderStop.isOn()) alert("Thunderstorm ended", Notification.Type.SUCCESS, 1.4f);
        }
        raining = rain;
        thundering = thunder;
    }

    private void alert(String msg, Notification.Type type, float pitch) {
        NotificationManager.post("Weather", msg, type, 5000);
        if (chat.isOn()) ChatUtil.info(msg);
        if (sound.isOn()) Sounds.ping(pitch);
    }
}
