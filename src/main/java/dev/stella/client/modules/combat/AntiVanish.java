package dev.stella.client.modules.combat;

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
 * <li>MEDIUM — (opt-in) a name appears in the server's /msg tab-completion but not in the tab list.
 *     Only works on servers whose vanish plugin leaks hidden names into command suggestions.</li>
 * </ul>
 * LIMITATION: most vanish plugins stop sending the staff member's entity and
 * tab entry entirely. In that case the client receives nothing and no alert is
 * possible. Absence of alerts is NOT evidence that nobody is watching.
 */
public class AntiVanish extends Module {
    public record Evidence(String identity, String reason, String confidence, String time) {}

    private final NumberSetting range = add(new NumberSetting("Range", "Entity evidence radius", 64, 8, 256, 8));
    private final BoolSetting onlyStaff = add(new BoolSetting("Only Verified Staff", "Ignore non-staff spectators (entity checks)", false));
    private final BoolSetting sound = add(new BoolSetting("Sound", "Play a sound with alerts", true));
    private final BoolSetting chat = add(new BoolSetting("Chat Log", "Print each alert in chat", true));
    private final BoolSetting tabCompleteCheck = add(new BoolSetting("Tab-Complete Check",
            "Periodically ask the server for /msg name suggestions and flag names hidden from the tab list (sends a request to the server)", false));
    private final NumberSetting tabCompleteInterval = add(new NumberSetting("Check Interval", "Seconds between tab-complete checks", 60, 30, 600, 10).suffix("s"))
            .visibleWhen(tabCompleteCheck::isOn);
    private final dev.stella.client.setting.TextSetting tabCommand = add(new dev.stella.client.setting.TextSetting("Check Command",
            "Command whose player-name suggestions are checked", "msg", 24)).visibleWhen(tabCompleteCheck::isOn);
    private static final int COMPLETION_ID = 0x5E11A;

    // ---- on-screen popup
    private final BoolSetting popup = add(new BoolSetting("Popup", "Big on-screen popup when someone is detected", true));
    private final dev.stella.client.setting.ModeSetting popupPosition = add(new dev.stella.client.setting.ModeSetting("Popup Position",
            "Where the popup appears", "Center", "Center", "Top", "Bottom")).visibleWhen(popup::isOn);
    private final NumberSetting popupDuration = add(new NumberSetting("Popup Duration", "Seconds the popup stays on screen", 6, 2, 20, 0.5).suffix("s")).visibleWhen(popup::isOn);
    private final NumberSetting popupScale = add(new NumberSetting("Popup Scale", "Size of the popup", 1.5, 0.75, 3, 0.05).suffix("x")).visibleWhen(popup::isOn);
    private final dev.stella.client.setting.ColorSetting nameColor = add(new dev.stella.client.setting.ColorSetting("Name Color", "Colour of the [ name ] line", 0xFFFF5555)).visibleWhen(popup::isOn);
    private final dev.stella.client.setting.TextSetting confirmedText = add(new dev.stella.client.setting.TextSetting("Strong Evidence Text",
            "Shown under the name for HIGH confidence evidence", "is spectating you", 48)).visibleWhen(popup::isOn);
    private final dev.stella.client.setting.TextSetting possibleText = add(new dev.stella.client.setting.TextSetting("Weak Evidence Text",
            "Shown under the name for MEDIUM confidence evidence", "may be spectating you", 48)).visibleWhen(popup::isOn);
    private final dev.stella.client.setting.TextSetting signOff = add(new dev.stella.client.setting.TextSetting("Sign-off Text",
            "Last line of the popup", "Goodluck Stella user", 48)).visibleWhen(popup::isOn);
    private final NumberSetting popupOpacity = add(new NumberSetting("Popup Opacity", "Background see-through amount (text stays solid)", 0.55, 0.05, 1, 0.05)).visibleWhen(popup::isOn);
    private final BoolSetting popupReason = add(new BoolSetting("Show Reason", "Small line with the evidence and confidence", true)).visibleWhen(popup::isOn);
    private final BoolSetting preview = add(new BoolSetting("Preview Popup", "Toggle to show an example popup", false)).onChange(this::previewNow);

    private String popupName, popupLine, popupReasonText;
    private long popupAt;
    private final dev.stella.client.util.Animation popupAnim = new dev.stella.client.util.Animation(0, 10);
    private long lastTabCheck;
    private final NumberSetting cooldown = add(new NumberSetting("Cooldown", "Seconds before the same evidence alerts again", 60, 5, 600, 5).suffix("s"));

    private final Map<String, Long> lastAlert = new HashMap<>();
    private final List<Evidence> log = new ArrayList<>();
    private int timer;

    public AntiVanish() {
        super("Anti Vanish", "Alerts on observable evidence of vanished or spectating staff (cannot see staff whose vanish leaks nothing)", Category.DONUTSMP);
    }

    public List<Evidence> log() { return log; }

    @Override
    protected void onEnable() {
        ChatUtil.info("Anti Vanish only reports evidence the server actually sends. Staff whose vanish plugin sends nothing cannot be detected.");
    }

    private void previewNow() {
        if (!preview.isOn()) return;
        preview.set(false);
        showPopup("ExampleStaff", "HIGH", "example popup preview");
    }

    /** Shows the popup (also used by the CI smoke test). */
    public void showPopup(String name, String confidence, String reason) {
        popupName = name;
        popupLine = "HIGH".equals(confidence) ? confirmedText.get() : possibleText.get();
        popupReasonText = reason + " (" + confidence.toLowerCase(java.util.Locale.ROOT) + " confidence)";
        popupAt = System.currentTimeMillis();
        popupAnim.snap(0);
    }

    @Override
    public void onRenderOverlay(net.minecraft.client.gui.DrawContext ctx, float tickDelta) {
        if (!popup.isOn() || popupName == null) return;
        long age = System.currentTimeMillis() - popupAt;
        long dur = (long) (popupDuration.get() * 1000);
        if (age > dur) { popupName = null; return; }
        double in = dev.stella.client.util.Animation.easeOutCubic(Math.min(1, age / 300.0));
        double out = age > dur - 400 ? (dur - age) / 400.0 : 1;
        double a = Math.max(0, Math.min(in, out));
        var t = dev.stella.client.gui.theme.ThemeManager.get();
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        float sc = popupScale.getFloat() * (float) (0.85 + 0.15 * in);
        String l1 = "[ " + popupName + " ]";
        String l2 = popupLine;
        String l3 = signOff.get();
        int w1 = dev.stella.client.render.StellaFont.width(l1), w2 = dev.stella.client.render.StellaFont.width(l2);
        int w3 = dev.stella.client.render.StellaFont.width(l3);
        int wr = popupReason.isOn() ? (int) (dev.stella.client.render.StellaFont.width(popupReasonText) * 0.6f) : 0;
        int w = Math.max(Math.max(w1, w2), Math.max(w3, wr)) + 28;
        int h = 46 + (popupReason.isOn() ? 9 : 0);
        float cy = switch (popupPosition.get()) {
            case "Top" -> 20 + h * sc / 2f;
            case "Bottom" -> sh - 50 - h * sc / 2f;
            default -> sh / 2f - 30;
        };
        ctx.createNewRootLayer();
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate((float) (sw / 2f), (float) (cy));
        ctx.getMatrices().scale((float) (sc), (float) (sc));
        float x = -w / 2f, y = -h / 2f;
        double pulse = 0.6 + 0.4 * Math.sin(age / 180.0);
        dev.stella.client.render.Render2D.shadow(ctx, x, y, w, h, 7, 6, dev.stella.client.util.ColorUtil.mulAlpha(
                dev.stella.client.util.ColorUtil.withAlpha(nameColor.argb(), 0x90), a * pulse * Math.max(0.35, popupOpacity.get())));
        dev.stella.client.render.Render2D.roundRect(ctx, x, y, w, h, 7, dev.stella.client.util.ColorUtil.mulAlpha(0xFF0C0A10, a * popupOpacity.get()));
        dev.stella.client.render.Render2D.roundRect(ctx, x, y, w, 2, 1, dev.stella.client.util.ColorUtil.mulAlpha(nameColor.argb(), a));
        int c1 = dev.stella.client.util.ColorUtil.mulAlpha(nameColor.argb(), a);
        int c2 = dev.stella.client.util.ColorUtil.mulAlpha(0xFFF2EFF6, a);
        int c3 = dev.stella.client.util.ColorUtil.mulAlpha(t.accentSoft(), a);
        dev.stella.client.render.StellaFont.draw(ctx, l1, -w1 / 2, (int) y + 7, c1, true);
        dev.stella.client.render.StellaFont.draw(ctx, l2, -w2 / 2, (int) y + 20, c2, true);
        dev.stella.client.render.StellaFont.draw(ctx, l3, -w3 / 2, (int) y + 33, c3, true);
        if (popupReason.isOn()) {
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate((float) (-wr / 2f), (float) (y + 45));
            ctx.getMatrices().scale(0.6f, 0.6f);
            dev.stella.client.render.StellaFont.draw(ctx, popupReasonText, 0, 0, dev.stella.client.util.ColorUtil.mulAlpha(t.textDim(), a), false);
            ctx.getMatrices().popMatrix();
        }
        // remaining-time bar
        float left = 1f - age / (float) dur;
        dev.stella.client.render.Render2D.roundRect(ctx, x + 6, y + h - 3, (w - 12) * left, 1.5f, 0.75f, dev.stella.client.util.ColorUtil.mulAlpha(nameColor.argb(), a * 0.8));
        ctx.getMatrices().popMatrix();
    }

    /** Called from the network mixin for every command-suggestion response. */
    public void onSuggestions(int id, java.util.List<String> names) {
        if (id != COMPLETION_ID || !isEnabled() || mc.getNetworkHandler() == null) return;
        java.util.Set<String> tab = new java.util.HashSet<>();
        for (PlayerListEntry e : mc.getNetworkHandler().getPlayerList()) tab.add(e.getProfile().getName().toLowerCase(java.util.Locale.ROOT));
        for (String n : names) {
            if (n.isBlank() || n.startsWith("@") || !n.matches("[A-Za-z0-9_]{3,16}")) continue;
            if (tab.contains(n.toLowerCase(java.util.Locale.ROOT))) continue;
            if (onlyStaff.isOn() && !StaffDatabase.isStaff(n)) continue;
            StaffDatabase.StaffMember m = StaffDatabase.find(n);
            String who = m != null ? n + " [" + m.rank() + "]" : n;
            raise(who, "suggested by /" + tabCommand.get() + " but hidden from the tab list", "MEDIUM", "tabc:" + n);
        }
    }

    private void tabCompleteCheck() {
        if (!tabCompleteCheck.isOn() || mc.getNetworkHandler() == null || mc.isInSingleplayer()) return;
        long now = System.currentTimeMillis();
        if (now - lastTabCheck < tabCompleteInterval.get() * 1000) return;
        lastTabCheck = now;
        String cmd = tabCommand.get().trim().replace("/", "");
        if (cmd.isEmpty()) return;
        mc.getNetworkHandler().sendPacket(new net.minecraft.network.packet.c2s.play.RequestCommandCompletionsC2SPacket(COMPLETION_ID, "/" + cmd + " "));
    }

    @Override
    public void onTick() {
        tabCompleteCheck();
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
        if (popup.isOn()) showPopup(identity.split(" ")[0], confidence, reason);
    }
}
