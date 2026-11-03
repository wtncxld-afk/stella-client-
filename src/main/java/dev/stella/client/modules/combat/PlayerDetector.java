package dev.stella.client.modules.combat;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.notification.Notification;
import dev.stella.client.notification.NotificationManager;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.NumberSetting;
import dev.stella.client.util.Sounds;
import net.minecraft.entity.player.PlayerEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Lists players whose entities the server has sent to this client within range.
 * Servers only send nearby, non-vanished players, so this cannot see everyone.
 */
public class PlayerDetector extends Module {
    public record Seen(String name, double distance) {}

    private final NumberSetting range = add(new NumberSetting("Range", "Detection radius", 128, 8, 512, 8));
    private final BoolSetting notifyEnter = add(new BoolSetting("Notify Enter", "Alert when a player enters range", true));
    private final BoolSetting notifyLeave = add(new BoolSetting("Notify Leave", "Alert when a player leaves range", false));
    private final BoolSetting sound = add(new BoolSetting("Sound", "Play a sound on enter", true));

    private final Set<UUID> inRange = new HashSet<>();
    private List<Seen> current = new ArrayList<>();

    public PlayerDetector() { super("Player Detector", "Detects observable players nearby with names and distances", Category.COMBAT); }

    public List<Seen> current() { return current; }

    @Override protected void onEnable() { inRange.clear(); current = new ArrayList<>(); }

    @Override
    public void onTick() {
        if (!inGame()) { inRange.clear(); current = new ArrayList<>(); return; }
        double r2 = range.get() * range.get();
        Set<UUID> now = new HashSet<>();
        List<Seen> list = new ArrayList<>();
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            double d2 = mc.player.squaredDistanceTo(p);
            if (d2 > r2) continue;
            now.add(p.getUuid());
            list.add(new Seen(p.getGameProfile().getName(), Math.sqrt(d2)));
            if (!inRange.contains(p.getUuid()) && notifyEnter.isOn()) {
                NotificationManager.post("Player Detected", p.getGameProfile().getName() + " (" + (int) Math.sqrt(d2) + "m)", Notification.Type.WARNING);
                if (sound.isOn()) Sounds.ping(0.8f);
            }
        }
        if (notifyLeave.isOn()) {
            for (UUID id : inRange) {
                if (!now.contains(id)) {
                    var e = mc.getNetworkHandler() == null ? null : mc.getNetworkHandler().getPlayerListEntry(id);
                    NotificationManager.post("Player Left Range", e != null ? e.getProfile().getName() : "A player", Notification.Type.INFO);
                }
            }
        }
        inRange.clear();
        inRange.addAll(now);
        list.sort((a, b) -> Double.compare(a.distance(), b.distance()));
        current = list;
    }
}
