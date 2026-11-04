package dev.stella.client.modules.combat;

import dev.stella.client.data.MediaDatabase;
import dev.stella.client.data.RegionConfig;
import dev.stella.client.hud.CardStyle;
import dev.stella.client.hud.HudElement;
import dev.stella.client.module.Category;
import dev.stella.client.notification.Notification;
import dev.stella.client.notification.NotificationManager;
import dev.stella.client.render.StellaFont;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.ColorSetting;
import dev.stella.client.setting.NumberSetting;
import dev.stella.client.setting.TextSetting;
import dev.stella.client.util.Sounds;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Lists media / content creators in your region: players in your server's tab
 * list whose displayed rank (tab name or team prefix/suffix) contains one of
 * the configured keywords, plus any names in media.json. Only players the
 * server lists in your tab are visible to the client, so "your region" means
 * the server instance you are connected to.
 */
public class MediaBaseFinder extends HudElement {
    public static final Identifier LOGO = Identifier.of(dev.stella.client.StellaClient.MOD_ID, "textures/gui/media.png");

    private final TextSetting keywords = add(new TextSetting("Rank Keywords", "Comma-separated words that mark a media rank in the tab list",
            "media, youtube, youtuber, streamer, twitch, creator", 200));
    private final BoolSetting useList = add(new BoolSetting("Use media.json", "Also include names listed in config/stella/media.json", true));
    private final BoolSetting reload = add(new BoolSetting("Reload media.json", "Toggle to re-read the file", false)).onChange(this::reloadNow);
    private final BoolSetting heads = add(new BoolSetting("Player Heads", "Show each player's head", true));
    private final BoolSetting showRegion = add(new BoolSetting("Show Region", "Show your current region next to the title", true));
    private final NumberSetting maxRows = add(new NumberSetting("Max Rows", "Names listed before '+N more'", 10, 1, 40, 1));
    private final BoolSetting notifyJoin = add(new BoolSetting("Notify Join", "Alert when a media player joins your region", true));
    private final BoolSetting notifyLeave = add(new BoolSetting("Notify Leave", "Alert when they leave", false));
    private final CardStyle card = card("Modern");
    private final ColorSetting titleColor = add(new ColorSetting("Title Color", "MEDIA BASE title colour (when Theme Colors is off)", 0xFFFF2BB5));
    private final ColorSetting nameColor = add(new ColorSetting("Name Color", "Player name colour", 0xFFF3F0F7));

    private List<PlayerListEntry> current = new ArrayList<>();
    private final Set<String> known = new HashSet<>();
    private boolean primed;
    private int timer;

    public MediaBaseFinder() {
        super("Media Base Finder", "Lists media / content creators in your region with their heads", Category.DONUTSMP,
                170, 340, false, "Scale", 4.0);
    }

    private void reloadNow() {
        if (!reload.isOn()) return;
        MediaDatabase.load();
        reload.set(false);
        NotificationManager.post("Media Base Finder", MediaDatabase.size() + " names in media.json", Notification.Type.SUCCESS, 2000);
    }

    @Override protected void onEnable() { primed = false; known.clear(); }

    private boolean isMedia(PlayerListEntry e) {
        String name = e.getProfile().name();
        if (useList.isOn() && MediaDatabase.contains(name)) return true;
        StringBuilder rank = new StringBuilder();
        if (e.getDisplayName() != null) rank.append(e.getDisplayName().getString()).append(' ');
        Team team = e.getScoreboardTeam();
        if (team != null) rank.append(team.getPrefix().getString()).append(' ').append(team.getSuffix().getString());
        String r = rank.toString().toLowerCase(Locale.ROOT);
        if (r.isBlank()) return false;
        for (String k : keywords.get().split(",")) {
            String kw = k.trim().toLowerCase(Locale.ROOT);
            if (!kw.isEmpty() && r.contains(kw)) return true;
        }
        return false;
    }

    @Override
    public void onTick() {
        if (mc.getNetworkHandler() == null) { current = new ArrayList<>(); known.clear(); primed = false; return; }
        if (++timer < 20) return;
        timer = 0;
        List<PlayerListEntry> list = new ArrayList<>();
        Set<String> now = new HashSet<>();
        for (PlayerListEntry e : mc.getNetworkHandler().getPlayerList()) {
            if (isMedia(e)) { list.add(e); now.add(e.getProfile().name()); }
        }
        list.sort((a, b) -> a.getProfile().name().compareToIgnoreCase(b.getProfile().name()));
        if (primed) {
            for (String n : now) if (!known.contains(n) && notifyJoin.isOn()) {
                NotificationManager.post("Media Base Finder", n + " is in your region", Notification.Type.WARNING, 5000);
                Sounds.ping(1.3f);
            }
            for (String n : known) if (!now.contains(n) && notifyLeave.isOn())
                NotificationManager.post("Media Base Finder", n + " left your region", Notification.Type.INFO, 4000);
        }
        primed = true;
        known.clear();
        known.addAll(now);
        current = list;
    }

    @Override
    public void render(DrawContext ctx, float tickDelta, boolean editor) {
        List<String> names = new ArrayList<>();
        List<PlayerListEntry> entries = new ArrayList<>(current);
        if (editor && entries.isEmpty()) names.addAll(List.of("ExampleMedia", "AnotherCreator"));
        for (PlayerListEntry e : entries) names.add(e.getProfile().name());

        String title = "MEDIA BASE";
        String region = "";
        if (showRegion.isOn() && mc.player != null) {
            RegionConfig.Region r = RegionConfig.regionAt(RegionConfig.colOf(mc.player.getX()), RegionConfig.rowOf(mc.player.getZ()));
            if (r != null) region = r.name().toUpperCase(Locale.ROOT);
        }
        int shown = Math.min(names.size(), maxRows.getInt());
        int w = 14 + StellaFont.width(title) + (region.isEmpty() ? 0 : 6 + (int) (StellaFont.width(region) * 0.7f)) + 6
                + StellaFont.width(String.valueOf(names.size()));
        for (int i = 0; i < shown; i++) w = Math.max(w, 13 + StellaFont.width(names.get(i)));
        String empty = "No media in your region";
        if (names.isEmpty()) w = Math.max(w, StellaFont.width(empty));
        String more = names.size() > shown ? "+" + (names.size() - shown) + " more" : "";
        int h = 12 + Math.max(1, shown) * 12 + (more.isEmpty() ? 0 : 10) - 2;
        card.draw(ctx, this, w, h);
        int px = card.padLeft(), py = card.pad();
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate((float) (px), (float) (py));
        // [media logo] MEDIA BASE  <region>  <count>
        ctx.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, LOGO, 0, -1, 0f, 0f, 11, 11, 11, 11, col(0xFFFFFFFF));
        int tc = col(card.accent(titleColor.argb()));
        StellaFont.draw(ctx, title, 14, 1, tc, true);
        int x = 14 + StellaFont.width(title) + 6;
        if (!region.isEmpty()) {
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate((float) (x), 2.5f);
            ctx.getMatrices().scale(0.7f, 0.7f);
            StellaFont.draw(ctx, region, 0, 0, col(0xFF9A93A6), false);
            ctx.getMatrices().popMatrix();
            x += (int) (StellaFont.width(region) * 0.7f) + 6;
        }
        StellaFont.draw(ctx, String.valueOf(names.size()), x, 1, col(nameColor.argb()), true);
        int y = 13;
        if (names.isEmpty()) StellaFont.draw(ctx, empty, 0, y, col(0xFF8E879A), false);
        for (int i = 0; i < shown; i++) {
            int tx = 0;
            if (heads.isOn()) {
                if (i < entries.size()) PlayerSkinDrawer.draw(ctx, entries.get(i).getSkinTextures(), 0, y, 9);
                else ctx.fill(0, y, 9, y + 9, col(0xFF3A3542));
                tx = 13;
            }
            StellaFont.draw(ctx, names.get(i), tx, y + 1, col(nameColor.argb()), true);
            y += 12;
        }
        if (!more.isEmpty()) StellaFont.draw(ctx, more, 0, y, col(0xFF8E879A), false);
        ctx.getMatrices().popMatrix();
        setSize(w + px + card.pad(), h + py * 2);
    }
}
