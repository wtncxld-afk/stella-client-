package dev.stella.client.modules.misc;

import dev.stella.client.data.StaffDatabase;
import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.notification.Notification;
import dev.stella.client.notification.NotificationManager;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.NumberSetting;
import dev.stella.client.util.ChatUtil;
import dev.stella.client.util.Sounds;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.GameMode;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Raises alerts only for evidence the client can actually observe:
 * <ul>
 *   <li>HIGH — a verified staff member's tab-list entry reports spectator mode.</li>
 *   <li>HIGH — a player entity in spectator mode is within range.</li>
 *   <li>MEDIUM — a player entity is nearby but absent from the tab list.</li>
 *   <li>MEDIUM — a verified staff member's entity is nearby and invisible.</li>
 * </ul>
 * LIMITATION: most vanish plugins stop sending the staff member's entity and
 * tab entry entirely. In that case the client receives nothing and no alert is
 * possible. Absence of alerts is NOT evidence that nobody is watching.
 */
public class StaffSpectatorAlerts extends Module {
    public record Evidence(String identity, String reason, String confidence, String time) {}

    private final NumberSetting range = add(new NumberSetting("Range", "Entity evidence radius", 64, 8, 256, 8));
    private final BoolSetting onlyStaff = add(new BoolSetting("Only Verified Staff", "Ignore non-staff spectators (entity checks)", false));
    private final BoolSetting sound = add(new BoolSetting("Sound", "Play a sound with alerts", true));
    private final BoolSetting chat = add(new BoolSetting("Chat Log", "Print each alert in chat", true));
    private final NumberSetting cooldown = add(new NumberSetting("Cooldown", "Seconds before the same evidence alerts again", 60, 5, 600, 5).suffix("s"));

    private final Map<String, Long> lastAlert = new HashMap<>();
    private final List<Evidence> log = new ArrayList<>();
    private int timer;

    public StaffSpectatorAlerts() {
        super("Staff Spectator Alerts", "Alerts on observable evidence of staff spectating (cannot detect fully vanished staff)", Category.MISC);
    }

    public List<Evidence> log() { return log; }

    @Override
    protected void onEnable() {
        ChatUtil.info("Staff Spectator Alerts only reports evidence the server actually sends. Fully vanished staff send nothing and cannot be detected.");
    }

    @Override
    public void onTick() {
        if (!inGame() || mc.getNetworkHandler() == null || ++timer < 10) return;
        timer = 0;

        for (StaffDatabase.StaffMember m : StaffDatabase.all()) {
            PlayerListEntry e = StaffDatabase.tabEntry(m);
            if (e != null && e.getGameMode() == GameMode.SPECTATOR) {
                raise(m.name() + " [" + m.rank() + "]", "tab list reports spectator mode", "HIGH", "tab:" + m.name());
            }
        }

        double r2 = range.get() * range.get();
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || mc.player.squaredDistanceTo(p) > r2) continue;
            String name = p.getGameProfile().getName();
            boolean staff = StaffDatabase.isStaff(name);
            if (onlyStaff.isOn() && !staff) continue;
            int dist = (int) Math.sqrt(mc.player.squaredDistanceTo(p));
            String who = staff ? name + " [" + StaffDatabase.find(name).rank() + "]" : name;
            if (p.isSpectator()) raise(who, "spectator-mode player " + dist + "m away", "HIGH", "spec:" + name);
            else if (mc.getNetworkHandler().getPlayerListEntry(p.getUuid()) == null) raise(who, "player entity " + dist + "m away but hidden from tab list", "MEDIUM", "notab:" + name);
            else if (staff && p.isInvisible()) raise(who, "invisible staff member " + dist + "m away", "MEDIUM", "invis:" + name);
        }
    }

    private void raise(String identity, String reason, String confidence, String key) {
        long now = System.currentTimeMillis();
        Long last = lastAlert.get(key);
        if (last != null && now - last < cooldown.get() * 1000) return;
        lastAlert.put(key, now);
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        log.add(0, new Evidence(identity, reason, confidence, time));
        while (log.size() > 50) log.remove(log.size() - 1);
        NotificationManager.post("Spectator evidence (" + confidence + ")", identity + ": " + reason, Notification.Type.ALERT, 7000);
        if (chat.isOn()) ChatUtil.info("[" + time + "] " + confidence + " confidence - " + identity + ": " + reason);
        if (sound.isOn()) Sounds.ping(0.6f);
    }
}
