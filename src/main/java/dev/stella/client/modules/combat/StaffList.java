package dev.stella.client.modules.combat;

import dev.stella.client.data.StaffDatabase;
import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.notification.Notification;
import dev.stella.client.notification.NotificationManager;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.util.ChatUtil;

import java.util.HashSet;
import java.util.Set;

/**
 * Join/leave alerts for verified staff, based purely on tab-list changes.
 * Roster: config/stella/staff.json (empty by default — Stella never invents staff).
 * A staff member leaving the tab list may have logged off OR vanished; the alert says so.
 */
public class StaffList extends Module {
    private final BoolSetting joinAlerts = add(new BoolSetting("Join Alerts", "Notify when a verified staff member appears in the tab list", true));
    private final BoolSetting leaveAlerts = add(new BoolSetting("Leave Alerts", "Notify when they disappear from the tab list", true));
    private final BoolSetting chat = add(new BoolSetting("Chat Messages", "Also print alerts in chat", false));
    private final BoolSetting reload = add(new BoolSetting("Reload staff.json", "Toggle to re-read the roster file", false)).onChange(this::reloadNow);

    private final Set<String> visible = new HashSet<>();
    private boolean primed;
    private int timer;

    public StaffList() { super("Staff List", "Verified DonutSMP staff roster with tab-list join/leave alerts", Category.DONUTSMP); }

    private void reloadNow() {
        if (!reload.isOn()) return;
        StaffDatabase.load();
        reload.set(false);
        NotificationManager.post("Staff List", StaffDatabase.all().size() + " verified staff loaded", Notification.Type.SUCCESS);
    }

    @Override protected void onEnable() { visible.clear(); primed = false; }

    @Override
    public void onTick() {
        if (mc.getNetworkHandler() == null) { visible.clear(); primed = false; return; }
        if (++timer < 20) return;
        timer = 0;
        Set<String> now = new HashSet<>();
        for (StaffDatabase.StaffMember m : StaffDatabase.visibleInTab()) now.add(m.name());
        if (primed) {
            for (String n : now) if (!visible.contains(n) && joinAlerts.isOn()) alert(n + " (" + rank(n) + ") joined the tab list", Notification.Type.WARNING);
            for (String n : visible) if (!now.contains(n) && leaveAlerts.isOn()) alert(n + " left the tab list (logged off or vanished)", Notification.Type.INFO);
        }
        primed = true;
        visible.clear();
        visible.addAll(now);
    }

    private static String rank(String n) {
        StaffDatabase.StaffMember m = StaffDatabase.find(n);
        return m == null ? "Staff" : m.rank();
    }

    private void alert(String msg, Notification.Type type) {
        NotificationManager.post("Staff", msg, type, 5000);
        if (chat.isOn()) ChatUtil.info(msg);
    }
}
