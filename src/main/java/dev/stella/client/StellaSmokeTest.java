package dev.stella.client;

import dev.stella.client.gui.ClickGuiScreen;
import dev.stella.client.hud.HudEditorScreen;
import dev.stella.client.mixin.CreateWorldScreenAccessor;
import dev.stella.client.module.Module;
import dev.stella.client.module.ModuleManager;
import dev.stella.client.modules.client.HudEditorModule;
import dev.stella.client.modules.misc.AutoRelog;
import dev.stella.client.modules.visuals.Zoom;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.screen.world.WorldCreator;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.MixinEnvironment;

/**
 * CI-only (-Dstella.smokeTest=true): applies every mixin, renders the GUIs,
 * creates a creative test world with every module enabled, takes screenshots
 * and prints STELLA_SMOKE_OK / STELLA_SMOKE_FAIL before exiting.
 */
public final class StellaSmokeTest {
    private static int tick;
    private static int menuShownAt = -1;
    private static int stage;
    private static int stageStart;

    private StellaSmokeTest() {}

    static void start() {
        StellaClient.LOGGER.info("[smoke] starting");
        ClientTickEvents.END_CLIENT_TICK.register(StellaSmokeTest::onTick);
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register((ctx, tc) -> drawCaption(ctx));
    }

    // ---- DonutSMP tour: on-screen caption (smoke test / preview video only) ----
    private static String caption, captionSub;
    private static long captionAt;

    private static void caption(String title, String sub) {
        caption = title; captionSub = sub; captionAt = System.currentTimeMillis();
        mc().inGameHud.getChatHud().clear(false);
        StellaClient.LOGGER.info("[smoke] tour: {} - {}", title, sub);
    }

    private static void drawCaption(net.minecraft.client.gui.DrawContext ctx) {
        if (caption == null) return;
        float t = Math.min(1f, (System.currentTimeMillis() - captionAt) / 350f);
        float e = 1f - (1f - t) * (1f - t) * (1f - t);
        int sw = ctx.getScaledWindowWidth();
        String top = "DONUTSMP  \u2022  " + caption;
        int w = Math.max(dev.stella.client.render.StellaFont.width(top) * 2, dev.stella.client.render.StellaFont.width(captionSub == null ? "" : captionSub)) + 28;
        int x = (sw - w) / 2, y = (int) (-40 + 52 * e);
        int a = (int) (255 * e);
        dev.stella.client.render.Render2D.roundRect(ctx, x, y, w, 36, 8, (Math.min(a, 215) << 24) | 0x120A1C);
        dev.stella.client.render.Render2D.roundRect(ctx, x + 10, y + 33, (w - 20) * e, 2, 1, (a << 24) | 0xB46CFF);
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(sw / 2f - dev.stella.client.render.StellaFont.width(top), y + 5);
        m.scale(2f, 2f);
        dev.stella.client.render.StellaFont.draw(ctx, top, 0, 0, (a << 24) | 0xF3E8FF, true);
        m.popMatrix();
        if (captionSub != null && !captionSub.isEmpty())
            dev.stella.client.render.StellaFont.draw(ctx, captionSub, (sw - dev.stella.client.render.StellaFont.width(captionSub)) / 2, y + 23, (a << 24) | 0xB9A7D6, true);
    }

    // Sus Chunk Finder stays on for the whole tour: re-enabling it would throw away its findings and rescan
    private static final String[] TOUR = {"Storage ESP", "Block ESP", "Spawner Finder", "Player ESP", "Player Detector",
            "Staff List", "Anti Vanish", "Media Base Finder", "Auto Tool", "Region Map HUD", "Staff List HUD", "Notifications HUD", "Radar HUD"};

    /** Turns on exactly these tour modules (the rest of TOUR off). */
    private static void only(String... on) {
        java.util.Set<String> want = java.util.Set.of(on);
        for (Module m : ModuleManager.all())
            for (String n : TOUR) if (m.getName().equals(n)) m.setEnabled(want.contains(n) || n.equals("Notifications HUD"));
    }

    private static float camYaw, camPitch;
    private static boolean jumpLogged;
    private static int landedAt = -1;

    /** Spectator camera that looks at (tx,ty,tz) from height camY, pitched down by {@code pitch} degrees, from the north. */
    private static void aerial(MinecraftClient mc, double tx, double ty, double tz, double camY, float pitch) {
        double back = (camY - ty) / Math.tan(Math.toRadians(pitch));
        camYaw = 0f; camPitch = pitch;
        serverCmd(mc, "tp " + mc.player.getGameProfile().name() + " " + tx + " " + camY + " " + (tz - back) + " 0 " + pitch);
    }

    private static void tourPlayers(MinecraftClient mc, boolean build, int hx, int hy, int hz, int px, int py, int pz) {
        var server = mc.getServer();
        if (server == null) return;
        server.execute(() -> {
            var world = server.getOverworld();
            var pm = server.getPlayerManager();
            if (build) {
                Object[][] spec = {{"HiddenPlayer", HIDDEN_UUID, hx, hy, hz}, {"OpenPlayer", OPEN_UUID, px, py, pz}};
                for (Object[] o : spec) {
                    var fp = net.fabricmc.fabric.api.entity.FakePlayer.get(world, new com.mojang.authlib.GameProfile((java.util.UUID) o[1], (String) o[0]));
                    fp.refreshPositionAndAngles((int) o[2] + 0.5, (int) o[3], (int) o[4] + 0.5, 180f, 0f);
                    pm.sendToAll(net.minecraft.network.packet.s2c.play.PlayerListS2CPacket.entryFromPlayer(java.util.List.of(fp)));
                    world.onPlayerConnected(fp);
                    scenePlayers.add(fp);
                }
            } else {
                for (var fp : scenePlayers) world.removePlayer(fp, net.minecraft.entity.Entity.RemovalReason.DISCARDED);
                pm.sendToAll(new net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket(java.util.List.of(HIDDEN_UUID, OPEN_UUID)));
                scenePlayers.clear();
            }
        });
    }

    private static final java.util.UUID MEDIA_UUID = java.util.UUID.fromString("5e1a5a11-0000-4000-8000-0000000000e1");

    private static void mediaPlayer(MinecraftClient mc, boolean add) {
        var server = mc.getServer();
        if (server == null) return;
        server.execute(() -> {
            var pm = server.getPlayerManager();
            if (add) {
                var fp = net.fabricmc.fabric.api.entity.FakePlayer.get(server.getOverworld(), new com.mojang.authlib.GameProfile(MEDIA_UUID, "MediaStreamer"));
                pm.sendToAll(net.minecraft.network.packet.s2c.play.PlayerListS2CPacket.entryFromPlayer(java.util.List.of(fp)));
            } else pm.sendToAll(new net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket(java.util.List.of(MEDIA_UUID)));
        });
    }

    private static void next() { stage++; stageStart = tick; StellaClient.LOGGER.info("[smoke] stage {}", stage); }
    private static int inStage() { return tick - stageStart; }

    private static void shot(MinecraftClient mc, String name) {
        ScreenshotRecorder.saveScreenshot(mc.runDirectory, "stella-" + name + ".png", mc.getFramebuffer(), 1,
                msg -> StellaClient.LOGGER.info("[smoke] screenshot {}: {}", name, msg.getString()));
    }

    private static int slow;
    private static boolean loadingShot;
    private static int ox, oy, oz, spawnX, spawnZ, stashX, stashZ;
    private static net.minecraft.util.math.BlockPos breakPos;
    private static net.minecraft.util.math.Direction breakSide;
    private static boolean autoToolPicked;

    /** Logs every chunk the Sus Chunk Finder scored, flagged or not, so false positives are visible in CI. */
    private static final java.util.UUID STAFF_UUID = java.util.UUID.fromString("5e1a5a11-0000-4000-8000-00000000057a");
    private static net.fabricmc.fabric.api.entity.FakePlayer staffPlayer;
    private static final java.util.UUID OTHER_UUID = java.util.UUID.fromString("5e1a5a11-0000-4000-8000-0000000000b2");

    /**
     * Anti Vanish test on the real integrated server: a server-side player named in staff.json joins the tab
     * list, then (1) goes into spectator mode near us, (2) turns visible but is removed from the tab list,
     * like a leaky vanish plugin, (3) leaves. The client only sees the real packets the server sends.
     */
    private static void staffTest(MinecraftClient mc, int phase) {
        var server = mc.getServer();
        if (server == null) { StellaClient.LOGGER.warn("[smoke] anti vanish: no integrated server"); return; }
        if (phase == 1) {
            try {
                java.nio.file.Files.writeString(dev.stella.client.data.StaffDatabase.file(),
                        "{\"source\": \"smoke test\", \"staff\": [{\"name\": \"StaffTester\", \"rank\": \"Moderator\"}]}");
            } catch (Exception e) { StellaClient.LOGGER.warn("[smoke] could not write staff.json", e); }
            dev.stella.client.data.StaffDatabase.load();
        }
        var look = mc.player.getRotationVec(1f);
        double x = mc.player.getX() + look.x * 5, y = mc.player.getY(), z = mc.player.getZ() + look.z * 5;
        server.execute(() -> {
            var world = server.getOverworld();
            var pm = server.getPlayerManager();
            if (phase == 1) {
                staffPlayer = net.fabricmc.fabric.api.entity.FakePlayer.get(world, new com.mojang.authlib.GameProfile(STAFF_UUID, "StaffTester"));
                staffPlayer.refreshPositionAndAngles(x, y, z, 90f, 0f);
                staffPlayer.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);
                pm.sendToAll(net.minecraft.network.packet.s2c.play.PlayerListS2CPacket.entryFromPlayer(java.util.List.of(staffPlayer)));
                world.onPlayerConnected(staffPlayer);
            } else if (phase == 2 && staffPlayer != null) {
                // out of spectator: the server now sends the body to nearby players
                staffPlayer.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
                staffPlayer.refreshPositionAndAngles(x, y, z, 90f, 0f);
            } else if (phase == 4 && staffPlayer != null) {
                // the vanish plugin removes them from the tab list but keeps sending the body
                pm.sendToAll(new net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket(java.util.List.of(STAFF_UUID)));
            } else if (phase == 5) {
                // Case 3: someone NOT in staff.json runs /gmsp (same server call as /gamemode spectator)
                var other = net.fabricmc.fabric.api.entity.FakePlayer.get(world, new com.mojang.authlib.GameProfile(OTHER_UUID, "UnlistedMod"));
                other.refreshPositionAndAngles(x, y, z, 90f, 0f);
                pm.sendToAll(net.minecraft.network.packet.s2c.play.PlayerListS2CPacket.entryFromPlayer(java.util.List.of(other)));
                other.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);
            } else if (phase == 6) {
                pm.sendToAll(new net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket(java.util.List.of(OTHER_UUID)));
            } else if (phase == 3 && staffPlayer != null) {
                world.removePlayer(staffPlayer, net.minecraft.entity.Entity.RemovalReason.DISCARDED);
                pm.sendToAll(new net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket(java.util.List.of(STAFF_UUID)));
                staffPlayer = null;
            }
        });
    }

    private static void logAntiVanish(String when) {
        var av = ModuleManager.get(dev.stella.client.modules.combat.AntiVanish.class);
        if (av == null) return; // Anti Vanish is currently removed
        var tab = mc().getNetworkHandler().getPlayerListEntry(STAFF_UUID);
        boolean entity = mc().world.getPlayers().stream().anyMatch(p -> p.getUuid().equals(STAFF_UUID));
        StellaClient.LOGGER.info("[smoke] anti vanish {}: tab entry={} gamemode={} entity visible to client={} alerts={}", when,
                tab != null, tab == null ? "-" : tab.getGameMode(), entity, av.log().size());
        for (var e : av.log()) StellaClient.LOGGER.info("[smoke] anti vanish alert: {} {} - {}", e.confidence(), e.identity(), e.reason());
    }

    private static MinecraftClient mc() { return MinecraftClient.getInstance(); }

    /** Runs a command on the integrated server without chat feedback. */
    private static void serverCmd(MinecraftClient mc, String cmd) {
        var server = mc.getServer();
        if (server == null) return;
        server.execute(() -> server.getCommandManager().parseAndExecute(server.getCommandSource().withSilent(), cmd));
    }

    private static final java.util.UUID HIDDEN_UUID = java.util.UUID.fromString("5e1a5a11-0000-4000-8000-0000000000d1");
    private static final java.util.UUID OPEN_UUID = java.util.UUID.fromString("5e1a5a11-0000-4000-8000-0000000000d2");
    private static final java.util.List<net.fabricmc.fabric.api.entity.FakePlayer> scenePlayers = new java.util.ArrayList<>();

    /** Builds (or removes) the Player Detector scene on the integrated server: a wall with a player behind it, and a player in the open. */
    private static void detectorScene(MinecraftClient mc, boolean build) {
        var server = mc.getServer();
        if (server == null) return;
        // fixed spot (not wherever the player happens to be after the respawn earlier in the test)
        int x = ox, y = oy, z = oz;
        server.execute(() -> {
            var world = server.getOverworld();
            var pm = server.getPlayerManager();
            if (build) {
                var cm = server.getCommandManager();
                var src = server.getCommandSource().withSilent();
                cm.parseAndExecute(src, "tp " + mc.player.getGameProfile().name() + " " + (x + 0.5) + " " + y + " " + (z + 0.5) + " -90 10");
                // flat, clear test area so only the wall can block the view
                cm.parseAndExecute(src, "fill " + (x + 1) + " " + y + " " + (z - 11) + " " + (x + 13) + " " + (y + 6) + " " + (z + 5) + " minecraft:air");
                cm.parseAndExecute(src, "fill " + (x + 1) + " " + (y - 1) + " " + (z - 11) + " " + (x + 13) + " " + (y - 1) + " " + (z + 5) + " minecraft:smooth_stone");
                cm.parseAndExecute(src, "fill " + (x + 5) + " " + y + " " + (z - 3) + " " + (x + 5) + " " + (y + 4) + " " + (z + 3) + " minecraft:stone");
                Object[][] spec = {{"HiddenPlayer", HIDDEN_UUID, x + 9, z}, {"OpenPlayer", OPEN_UUID, x + 9, z - 8}};
                for (Object[] o : spec) {
                    var fp = net.fabricmc.fabric.api.entity.FakePlayer.get(world, new com.mojang.authlib.GameProfile((java.util.UUID) o[1], (String) o[0]));
                    fp.refreshPositionAndAngles((int) o[2] + 0.5, y, (int) o[3] + 0.5, 90f, 0f);
                    pm.sendToAll(net.minecraft.network.packet.s2c.play.PlayerListS2CPacket.entryFromPlayer(java.util.List.of(fp)));
                    world.onPlayerConnected(fp);
                    scenePlayers.add(fp);
                }
            } else {
                for (var fp : scenePlayers) world.removePlayer(fp, net.minecraft.entity.Entity.RemovalReason.DISCARDED);
                pm.sendToAll(new net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket(java.util.List.of(HIDDEN_UUID, OPEN_UUID)));
                scenePlayers.clear();
            }
        });
    }

    private static void logDetector(String when) {
        var pd = ModuleManager.get(dev.stella.client.modules.combat.PlayerDetector.class);
        StellaClient.LOGGER.info("[smoke] player detector ({}): {}", when, pd.current().stream()
                .map(s -> s.name() + " " + (int) s.distance() + "m " + dev.stella.client.modules.combat.PlayerDetector.compass(s.dx(), s.dz()) + " " + s.status())
                .collect(java.util.stream.Collectors.joining(", ")));
        boolean hiddenFound = pd.current().stream().anyMatch(s -> s.name().equals("HiddenPlayer") && s.status() == dev.stella.client.modules.combat.PlayerDetector.Status.BEHIND_WALL);
        boolean openListed = pd.current().stream().anyMatch(s -> s.name().equals("OpenPlayer"));
        if (when.equals("hidden only") && (!hiddenFound || openListed)) fail("Player Detector hidden-only result wrong: hidden=" + hiddenFound + " open listed=" + openListed);
        if (when.equals("all players") && (!hiddenFound || !openListed)) fail("Player Detector all-players result wrong");
    }

    private static void logDetectorTour() {
        var pd = ModuleManager.get(dev.stella.client.modules.combat.PlayerDetector.class);
        StellaClient.LOGGER.info("[smoke] tour player detector: {}", pd.current().stream()
                .map(x -> x.name() + " " + (int) x.distance() + "m " + x.status()).collect(java.util.stream.Collectors.joining(", ")));
    }

    private static void logSus(String when) {
        var sus = ModuleManager.get(dev.stella.client.modules.combat.SusChunkFinder.class);
        StellaClient.LOGGER.info("[smoke] sus {}: base chunk {} {}, trial chamber chunk {} {}", when, (ox + 40) >> 4, (oz - 56) >> 4, (ox + 40) >> 4, (oz + 70) >> 4);
        StellaClient.LOGGER.info("[smoke] sus structure blocks seen: {}, findings dropped as natural: {}", sus.structureMarkers(), sus.explainedByStructures().size());
        for (String e : sus.explainedByStructures()) StellaClient.LOGGER.info("[smoke] sus dropped (natural structure): {}", e);
        for (var c : sus.chunks()) {
            StringBuilder why = new StringBuilder();
            for (var f : c.findings()) why.append(" | ").append(f.type()).append(" ").append(f.score()).append(": ").append(String.join("; ", f.reasons()));
            StellaClient.LOGGER.info("[smoke] sus chunk {} {} score {} flagged={}{}", c.cx(), c.cz(), c.score(), sus.suspectChunks().stream().anyMatch(x -> x.cx() == c.cx() && x.cz() == c.cz()), why);
        }
    }

    private static void onTick(MinecraftClient mc) {
        // In-world showcase runs at half speed so the recorded preview video is watchable.
        if (stage == 5 && (slow++ & 1) == 1) return;
        tick++;
        try {
            switch (stage) {
                case 0 -> {
                    if (inStage() == 1) mc.options.getViewDistance().setValue(6); // software rendering in CI: keep FPS up
                    if (tick < 40 || mc.getOverlay() != null) return;
                    // a fresh install first shows vanilla's one-time accessibility screen: continue past it
                    if (mc.currentScreen != null && mc.currentScreen.getClass().getSimpleName().contains("Onboarding")) {
                        mc.options.onboardAccessibility = false;
                        mc.setScreen(new net.minecraft.client.gui.screen.TitleScreen(false));
                        return;
                    }
                    if (menuShownAt < 0) {
                        mc.options.tutorialStep = net.minecraft.client.tutorial.TutorialStep.NONE;
                        MixinEnvironment.getCurrentEnvironment().audit();
                        StellaClient.LOGGER.info("[smoke] mixin audit passed; title screen = {}",
                                mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getSimpleName());
                        menuShownAt = tick;
                    }
                    if (tick - menuShownAt == 70) shot(mc, "main-menu");
                    if (tick - menuShownAt < 76) return;
                    mc.setScreen(new ClickGuiScreen());
                    next();
                }
                case 1 -> {
                    if (inStage() == 30 && mc.currentScreen instanceof ClickGuiScreen gui) gui.openSettingsFor("Custom Crosshair");
                    if (inStage() == 50) shot(mc, "clickgui");
                    // Animation showcase in the main menu (much higher FPS than in a world, so the video is smoother):
                    // close + reopen the GUI, toggles sweeping on and off, a settings window popping in
                    if (inStage() == 54 && mc.currentScreen != null) mc.currentScreen.close();
                    if (inStage() == 74) mc.setScreen(new ClickGuiScreen());
                    if (inStage() >= 96 && inStage() <= 150 && (inStage() - 96) % 9 == 0) {
                        String[] showcase = {"Zoom", "Item Physics", "Swing Speed", "Zoom", "Item Physics", "Swing Speed", "Custom FOV"};
                        String n = showcase[(inStage() - 96) / 9];
                        ModuleManager.all().stream().filter(m -> m.getName().equals(n)).forEach(Module::toggle);
                    }
                    if (inStage() == 156) ModuleManager.all().stream().filter(m -> m.getName().equals("Custom FOV")).forEach(Module::toggle);
                    if (inStage() == 162 && mc.currentScreen instanceof ClickGuiScreen gui) gui.openSettingsFor("Custom Sky");
                    if (inStage() == 186 && mc.currentScreen != null) mc.currentScreen.close();
                    if (inStage() == 206) { mc.setScreen(new HudEditorScreen(null)); next(); }
                }
                case 2 -> {
                    if (inStage() == 30) shot(mc, "hudeditor");
                    // Music HUD close-ups (example song data; CI has no media player)
                    if (inStage() == 32) {
                        var music = ModuleManager.get(dev.stella.client.modules.hud.MusicHud.class);
                        music.setEnabled(true);
                        music.setPos(250, 120);
                    }
                    if (inStage() == 46) shot(mc, "music-hud");
                    if (inStage() == 48) setting("Music HUD", "Style", "Compact");
                    if (inStage() == 58) shot(mc, "music-hud-compact");
                    if (inStage() == 60) { setting("Music HUD", "Style", "Card"); ModuleManager.get(dev.stella.client.modules.hud.MusicHud.class).setEnabled(false); }
                    if (inStage() == 62) {
                        mc.setScreen(null);
                        CreateWorldScreen.show(mc, null);
                        next();
                    }
                }
                case 3 -> {
                    if (inStage() == 20 && mc.currentScreen instanceof CreateWorldScreen cws) {
                        WorldCreator wc = ((CreateWorldScreenAccessor) cws).getWorldCreator();
                        wc.setCheatsEnabled(true);
                        wc.setGameMode(WorldCreator.Mode.CREATIVE);
                        wc.setSeed("stella");
                        press(cws, "selectWorld.create");
                        next();
                    }
                }
                case 4 -> {
                    if (!loadingShot && mc.currentScreen instanceof net.minecraft.client.gui.screen.world.LevelLoadingScreen && inStage() > 6) {
                        loadingShot = true;
                        shot(mc, "loading-world");
                    }
                    if (mc.world != null && mc.player != null && mc.currentScreen == null) {
                        for (Module m : ModuleManager.all()) {
                            if (m instanceof AutoRelog || m instanceof HudEditorModule || m instanceof Zoom
                                    || m instanceof dev.stella.client.modules.misc.Freecam
                                    || m instanceof dev.stella.client.modules.pvp.DoubleAnchor) continue;
                            m.setEnabled(true);
                        }
                        // disable modules that would clutter the preview shots
                        for (String n : new String[]{"Fullbright", "Ambience", "Fog Customizer", "World Time", "Motion Blur", "Weather Customizer", "Damage Tint"})
                            ModuleManager.all().stream().filter(m -> m.getName().equals(n)).forEach(m -> m.setEnabled(false));
                        setting("Block ESP", "Tracers", true);
                        setting("Storage ESP", "Tracers", true);
                        setting("Sus Chunk Finder", "Tracers", true);
                        var h = mc.player.networkHandler;
                        h.sendChatCommand("time set 18000");
                        h.sendChatCommand("gamerule doDaylightCycle false");
                        h.sendChatCommand("fill ~3 ~-1 ~-4 ~12 ~-1 ~4 minecraft:deepslate[axis=x]");
                        h.sendChatCommand("fill ~-14 ~ ~18 ~-12 ~1 ~20 minecraft:white_concrete");
                        h.sendChatCommand("setblock ~-13 ~2 ~19 minecraft:observer");
                        h.sendChatCommand("setblock ~5 ~ ~-2 minecraft:chest");
                        h.sendChatCommand("setblock ~5 ~ ~ minecraft:barrel");
                        h.sendChatCommand("setblock ~5 ~ ~2 minecraft:shulker_box");
                        h.sendChatCommand("setblock ~8 ~ ~-2 minecraft:spawner{SpawnData:{entity:{id:\"minecraft:zombie\"}}}");
                        h.sendChatCommand("setblock ~10 ~ ~5 minecraft:spawner{SpawnData:{entity:{id:\"minecraft:skeleton\"}}}");
                        h.sendChatCommand("setblock ~14 ~ ~-6 minecraft:spawner{SpawnData:{entity:{id:\"minecraft:cave_spider\"}}}");
                        h.sendChatCommand("setblock ~8 ~ ~1 minecraft:amethyst_block");
                        h.sendChatCommand("setblock ~8 ~1 ~1 minecraft:amethyst_cluster");
                        h.sendChatCommand("summon minecraft:item ~4 ~ ~1 {Item:{id:\"minecraft:diamond_sword\",count:1}}");
                        // A realistic hidden base below Y 0 (sideways-deepslate room, storage, small redstone farm)
                        ox = mc.player.getBlockX(); oy = mc.player.getBlockY(); oz = mc.player.getBlockZ();
                        spawnX = ox; spawnZ = oz;
                        int bx = ox + 40, by = -40, bz = oz - 56;
                        h.sendChatCommand("fill " + bx + " " + by + " " + bz + " " + (bx + 9) + " " + (by + 5) + " " + (bz + 9) + " minecraft:deepslate[axis=z] hollow");
                        h.sendChatCommand("setblock " + (bx + 2) + " " + (by + 1) + " " + (bz + 2) + " minecraft:chest");
                        h.sendChatCommand("setblock " + (bx + 3) + " " + (by + 1) + " " + (bz + 2) + " minecraft:chest");
                        h.sendChatCommand("setblock " + (bx + 4) + " " + (by + 1) + " " + (bz + 2) + " minecraft:barrel");
                        h.sendChatCommand("setblock " + (bx + 2) + " " + (by + 1) + " " + (bz + 6) + " minecraft:observer[facing=up]");
                        h.sendChatCommand("setblock " + (bx + 2) + " " + (by + 2) + " " + (bz + 6) + " minecraft:sticky_piston[facing=up]");
                        h.sendChatCommand("setblock " + (bx + 3) + " " + (by + 1) + " " + (bz + 6) + " minecraft:redstone_wire");
                        h.sendChatCommand("setblock " + (bx + 4) + " " + (by + 1) + " " + (bz + 6) + " minecraft:repeater[facing=west]");
                        h.sendChatCommand("setblock " + (bx + 5) + " " + (by + 1) + " " + (bz + 6) + " minecraft:comparator[facing=west]");
                        h.sendChatCommand("setblock " + (bx + 6) + " " + (by + 1) + " " + (bz + 6) + " minecraft:hopper");
                        h.sendChatCommand("setblock " + (bx + 7) + " " + (by + 1) + " " + (bz + 6) + " minecraft:redstone_lamp");
                        h.sendChatCommand("setblock " + (bx + 6) + " " + (by + 1) + " " + (bz + 4) + " minecraft:white_concrete");
                        // keep the trial chamber area loaded; the chamber itself is placed a few seconds later
                        h.sendChatCommand("forceload add " + (ox - 32) + " " + (oz + 16) + " " + (ox + 112) + " " + (oz + 140));
                        setting("Sus Chunk Finder", "Range", 192.0);
                        mc.player.setYaw(-90f);
                        mc.player.setPitch(18f);
                        next();
                    } else if (inStage() > 6000) {
                        fail("world did not load");
                    }
                }
                case 5 -> {
                    if (inStage() == 5) { mc.player.setYaw(-90f); mc.player.setPitch(18f); }
                    // A real, naturally generated trial chamber: must NOT be flagged
                    if (inStage() == 40) mc.player.networkHandler.sendChatCommand("place structure minecraft:trial_chambers " + (ox + 40) + " -30 " + (oz + 78));
                    if (inStage() == 100) shot(mc, "world-cosmic-night");
                    if (inStage() == 110) { mc.player.setPitch(-35f); }
                    if (inStage() == 130) shot(mc, "sky-cosmic");
                    if (inStage() == 135) ModuleManager.all().stream().filter(m -> m.getName().equals("Custom Sky")).findFirst()
                            .ifPresent(m -> m.getSettings().stream().filter(s -> s.getName().equals("Preset")).findFirst()
                                    .ifPresent(s -> ((dev.stella.client.setting.ModeSetting) s).set("Arctic Aurora")));
                    if (inStage() == 160) shot(mc, "sky-arctic-aurora");
                    if (inStage() == 162) setting("Custom Sky", "Preset", "Cosmic Stella");
                    if (inStage() == 165) { mc.player.setPitch(18f); mc.setScreen(new ClickGuiScreen()); }
                    if (inStage() == 178) mc.inGameHud.getChatHud().clear(false);
                    if (inStage() == 182) shot(mc, "gui-clean");
                    if (inStage() == 185 && mc.currentScreen instanceof ClickGuiScreen gui) { gui.openSettingsFor("Player ESP"); gui.openSettingsFor("Custom Sky"); }
                    if (inStage() == 210) shot(mc, "world-gui");
                    if (inStage() == 212) setting("ClickGUI", "Theme", "Pink");
                    if (inStage() == 225) shot(mc, "world-gui-pink");
                    if (inStage() == 228) { setting("ClickGUI", "Theme", "Stella"); mc.setScreen(null); }
                    if (inStage() == 229) mc.setScreen(new HudEditorScreen(null));
                    if (inStage() == 245) shot(mc, "world-hudeditor");
                    if (inStage() == 247) {
                        mc.setScreen(null);
                        mc.inGameHud.getChatHud().clear(false);
                        java.util.Set<String> keep = java.util.Set.of("Sus Chunk Finder", "Storage ESP", "Block ESP", "Custom Sky",
                                "Stella Branding HUD", "FPS HUD", "Coordinates HUD", "Notifications HUD", "Amethyst Detector");
                        for (Module m : ModuleManager.all()) {
                            if (m.getCategory() == dev.stella.client.module.Category.CLIENT || m.getCategory() == dev.stella.client.module.Category.SETTINGS) continue;
                            if (!keep.contains(m.getName())) m.setEnabled(false);
                        }
                        // real-world settings: only below Y 0, default sensitivity
                        setting("Sus Chunk Finder", "Flat Y", (double) oy + 1);
                        setting("Sus Chunk Finder", "Only Below Y", true);
                        // spectator camera in the air, looking down (north) at the hidden base's chunk
                        mc.player.networkHandler.sendChatCommand("gamemode spectator");
                        mc.player.networkHandler.sendChatCommand("tp @s " + (ox + 44) + " " + (oy + 45) + " " + (oz - 26));
                    }
                    if (inStage() == 252) { mc.player.setYaw(180f); mc.player.setPitch(58f); }
                    if (inStage() == 276) logSus("hidden base shot");
                    if (inStage() == 278) shot(mc, "sus-chunk-finder");
                    if (inStage() == 256) { mc.inGameHud.getChatHud().clear(false); ModuleManager.get(dev.stella.client.modules.combat.SusChunkFinder.class).resetChatMessages(); }
                    if (inStage() == 285) shot(mc, "sus-chunk-chat");
                    if (inStage() == 287) mc.player.networkHandler.sendChatCommand("gamemode creative");
                    if (inStage() == 288) ModuleManager.all().stream().filter(m -> m.getName().equals("Region Map HUD")).forEach(m -> m.setEnabled(true));
                    if (inStage() == 298) mc.inGameHud.getChatHud().clear(false);
                    if (inStage() == 300) shot(mc, "region-map");
                    if (inStage() == 302) {
                        // every HUD element on, world modules off: a clean look at the HUD
                        for (Module m : ModuleManager.all()) {
                            if (m.getCategory() == dev.stella.client.module.Category.HUD) m.setEnabled(true);
                            else if (m.getCategory() != dev.stella.client.module.Category.CLIENT
                                    && m.getCategory() != dev.stella.client.module.Category.SETTINGS && !m.getName().equals("Custom Sky")) m.setEnabled(false);
                        }
                        ModuleManager.get(dev.stella.client.modules.combat.MediaBaseFinder.class).setEnabled(true);
                        dev.stella.client.notification.NotificationManager.post("Block Notifier", "Spawner at 16 113 8",
                                dev.stella.client.notification.Notification.Type.INFO, 20000);
                        dev.stella.client.notification.NotificationManager.post("Staff", "Example alert toast",
                                dev.stella.client.notification.Notification.Type.WARNING, 20000);
                    }
                    if (inStage() == 312) {
                        // user-style toggles show the compact toggle popups
                        ModuleManager.get(dev.stella.client.modules.visuals.Fullbright.class).toggle();
                        ModuleManager.get(dev.stella.client.modules.combat.PlayerESP.class).toggle();
                    }
                    if (inStage() == 318) mc.inGameHud.getChatHud().clear(false);
                    if (inStage() == 320) shot(mc, "hud-all");
                    if (inStage() == 322) mc.setScreen(new HudEditorScreen(null));
                    if (inStage() == 335) shot(mc, "hud-editor-all");
                    if (inStage() == 337) {
                        mc.setScreen(null);
                        ModuleManager.get(dev.stella.client.modules.misc.Freecam.class).setEnabled(true);
                    }
                    if (inStage() == 340) {
                        var fc = ModuleManager.get(dev.stella.client.modules.misc.Freecam.class);
                        fc.nudge(-6, 3, 0);
                        fc.setLook(-90f, 20f);
                        mc.inGameHud.getChatHud().clear(false);
                    }
                    if (inStage() == 352) shot(mc, "freecam");
                    if (inStage() == 354) ModuleManager.get(dev.stella.client.modules.misc.Freecam.class).setEnabled(false);
                    if (inStage() == 356) {
                        for (Module m : ModuleManager.all())
                            if (m.getCategory() == dev.stella.client.module.Category.HUD && !m.getName().equals("Stella Branding HUD")) m.setEnabled(false);
                        var av = ModuleManager.get(dev.stella.client.modules.combat.AntiVanish.class);
                        if (av != null) { av.setEnabled(false); av.setEnabled(true); av.log().clear(); }
                        ModuleManager.get(dev.stella.client.modules.combat.MediaBaseFinder.class).setEnabled(true);
                        mc.inGameHud.getChatHud().clear(false);
                        // Case 1: a verified staff member switches to spectator mode near us (real server-side player)
                        staffTest(mc, 1);
                    }
                    if (inStage() == 366) logAntiVanish("case 1: staff in spectator mode");
                    if (inStage() == 368) shot(mc, "staff-join");
                    if (inStage() == 370) staffTest(mc, 2); // Case 2: vanish plugin hides them from tab but still sends their body
                    if (inStage() == 380) staffTest(mc, 4);
                    if (inStage() == 400) logAntiVanish("case 2: hidden from tab list, entity still sent");
                    if (inStage() == 401) staffTest(mc, 3); // clean up
                    if (inStage() == 402) staffTest(mc, 5); // Case 3: unlisted player does /gmsp
                    if (inStage() == 409) { logAntiVanish("case 3: player not in staff.json does /gmsp"); staffTest(mc, 6); }
                    if (inStage() == 372) mc.setScreen(new ClickGuiScreen());
                    if (inStage() == 385 && mc.currentScreen instanceof ClickGuiScreen gui) gui.openSettingsFor("Radar HUD");
                    if (inStage() == 405) shot(mc, "radar-gui");
                    if (inStage() == 410) {
                        mc.setScreen(null);
                        ModuleManager.get(dev.stella.client.modules.visuals.GlowOutline.class).setEnabled(true);
                        ModuleManager.get(dev.stella.client.modules.visuals.ItemScale.class).setEnabled(true);
                        ModuleManager.get(dev.stella.client.modules.visuals.SwingSpeed.class).setEnabled(true);
                        // back on the ground, so the dropped items land right in front of the camera
                        var h2 = mc.player.networkHandler;
                        h2.sendChatCommand("gamemode creative");
                        h2.sendChatCommand("tp @s " + ox + " " + (oy + 1) + " " + oz);
                        setting("Glow Outline", "Hostile Mobs", true);
                        setting("Glow Outline", "Passive Mobs", true);
                    }
                    if (inStage() == 413) {
                        var h2 = mc.player.networkHandler;
                        String[] items = {"diamond", "nether_star", "golden_apple", "enchanted_golden_apple", "netherite_ingot", "totem_of_undying"};
                        for (int i = 0; i < items.length; i++)
                            h2.sendChatCommand("summon item " + (ox + 3 + (i % 2)) + " " + (oy + 2) + " " + (oz - 2 + i) + " {Item:{id:\"minecraft:" + items[i] + "\",count:1},PickupDelay:32767}");
                        h2.sendChatCommand("summon pig " + (ox + 6) + " " + (oy + 1) + " " + (oz + 3) + " {NoAI:1b}");
                        h2.sendChatCommand("summon zombie " + (ox + 6) + " " + (oy + 1) + " " + (oz - 3) + " {NoAI:1b,PersistenceRequired:1b}");
                        // hold a totem in both hands for the shot
                        mc.player.getInventory().setSelectedSlot(0);
                        h2.sendChatCommand("item replace entity @s weapon.mainhand with minecraft:totem_of_undying");
                        h2.sendChatCommand("item replace entity @s weapon.offhand with minecraft:totem_of_undying");
                        mc.player.setYaw(-90f);
                        mc.player.setPitch(30f);
                    }
                    if (inStage() == 428) mc.inGameHud.getChatHud().clear(false);
                    if (inStage() == 432) shot(mc, "glow-outline");
                    if (inStage() == 433) StellaClient.LOGGER.info("[smoke] held item outlines applied: {}", dev.stella.client.modules.visuals.GlowOutline.heldOutlineHits);
                    if (inStage() == 436) {
                        mc.setScreen(null);
                        mc.setScreen(null);
                        for (Module m : ModuleManager.all()) if (m.isEnabled()) m.setEnabled(false);
                        for (Module m : ModuleManager.all()) if (m.isDefaultEnabled()) m.setEnabled(true);
                    }
                    if (inStage() == 440) mc.setScreen(new net.minecraft.client.gui.screen.GameMenuScreen(true));
                    if (inStage() == 462) shot(mc, "pause-menu");
                    if (inStage() == 466) mc.setScreen(null);
                    if (inStage() == 468) {
                        // Clean tracer shot: storage / block ESP with tracers from the bottom of the screen, HUD elements off.
                        for (Module m : ModuleManager.all())
                            if (m.getCategory() == dev.stella.client.module.Category.HUD) m.setEnabled(false);
                        setting("Storage ESP", "Tracer Origin", "Bottom");
                        setting("Block ESP", "Tracer Origin", "Bottom");
                        for (String n : new String[]{"Storage ESP", "Block ESP", "Custom Sky", "Fullbright"})
                            ModuleManager.all().stream().filter(m -> m.getName().equals(n)).forEach(m -> m.setEnabled(true));
                        mc.player.setYaw(-90f);
                        mc.player.setPitch(12f);
                    }
                    if (inStage() == 482) mc.inGameHud.getChatHud().clear(false);
                    if (inStage() == 486) shot(mc, "tracers");
                    if (inStage() == 488) {
                        // Trial chamber check: Block ESP marks its trial spawners / vaults, the Sus Chunk Finder must stay quiet there
                        setting("Block ESP", "Blocks", new java.util.ArrayList<>(java.util.List.of("minecraft:trial_spawner", "minecraft:vault")));
                        setting("Block ESP", "Tracers", false);
                        setting("Storage ESP", "Tracers", false);
                        for (String n : new String[]{"Storage ESP", "Block ESP", "Sus Chunk Finder"})
                            ModuleManager.all().stream().filter(m -> m.getName().equals(n)).forEach(m -> { m.setEnabled(false); m.setEnabled(true); });
                        ModuleManager.all().stream().filter(m -> m.getName().equals("Coordinates HUD") || m.getName().equals("Stella Branding HUD")).forEach(m -> m.setEnabled(true));
                        mc.player.networkHandler.sendChatCommand("gamemode spectator");
                        mc.player.networkHandler.sendChatCommand("tp @s " + (ox + 44) + " " + (oy + 30) + " " + (oz + 118));
                    }
                    if (inStage() == 492) { mc.player.setYaw(180f); mc.player.setPitch(55f); }
                    if (inStage() == 560) { mc.inGameHud.getChatHud().clear(false); logSus("trial chamber shot"); }
                    if (inStage() == 564) shot(mc, "sus-trial-chamber");
                    if (inStage() == 566) {
                        // Spawner Finder: beams into the sky + dropdown with mob types + chat messages
                        mc.inGameHud.getChatHud().clear(false);
                        for (String n : new String[]{"Storage ESP", "Block ESP", "Sus Chunk Finder"})
                            ModuleManager.all().stream().filter(m -> m.getName().equals(n)).forEach(m -> m.setEnabled(false));
                        ModuleManager.get(dev.stella.client.modules.combat.SpawnerFinder.class).setEnabled(true);
                        mc.player.networkHandler.sendChatCommand("tp @s " + (ox - 14) + " " + (oy + 9) + " " + oz);
                    }
                    if (inStage() == 570) { mc.player.setYaw(-90f); mc.player.setPitch(-4f); }
                    if (inStage() == 592) {
                        var sf = ModuleManager.get(dev.stella.client.modules.combat.SpawnerFinder.class);
                        StellaClient.LOGGER.info("[smoke] spawners: {}", sf.spawners());
                        int[][] test = {{8, -2}, {10, 5}, {14, -6}};
                        for (int[] o : test) {
                            for (int dy = -2; dy <= 2; dy++) {
                                var bp = new net.minecraft.util.math.BlockPos(spawnX + o[0], oy + dy, spawnZ + o[1]);
                                var st = mc.world.getBlockState(bp);
                                if (st.isOf(net.minecraft.block.Blocks.SPAWNER)) StellaClient.LOGGER.info("[smoke] spawner check {} -> {}", bp,
                                        dev.stella.client.scan.BlockScanner.debug(bp, sf.scanTarget()));
                            }
                        }
                        StellaClient.LOGGER.info("[smoke] scanner queued={} loaded={}", dev.stella.client.scan.BlockScanner.queued(), dev.stella.client.scan.BlockScanner.loadedChunks());
                    }
                    if (inStage() == 594) shot(mc, "spawner-finder");
                    if (inStage() == 596) { setting("ClickGUI", "Theme", "Sunset"); mc.setScreen(new ClickGuiScreen()); }
                    if (inStage() == 610) shot(mc, "theme-sunset");
                    if (inStage() == 612) setting("ClickGUI", "Theme", "Ocean");
                    if (inStage() == 622) shot(mc, "theme-ocean");
                    if (inStage() == 624) setting("ClickGUI", "Theme", "Galaxy");
                    if (inStage() == 634) shot(mc, "theme-galaxy");
                    if (inStage() == 636) { setting("ClickGUI", "Theme", "Stella"); mc.setScreen(null); }
                    if (inStage() == 638) {
                        // New HUDs + Totem Pop Counter: damaged armor, effects, PvP items, then a real totem pop
                        for (Module m : ModuleManager.all())
                            if (m.getCategory() == dev.stella.client.module.Category.HUD && !m.getName().equals("Stella Branding HUD")) m.setEnabled(false);
                        for (String n : new String[]{"Spawner Finder", "Sus Chunk Finder", "Storage ESP", "Block ESP"})
                            ModuleManager.all().stream().filter(m -> m.getName().equals(n)).forEach(m -> m.setEnabled(false));
                        for (Class<? extends Module> c : java.util.List.of(dev.stella.client.modules.hud.ArmorHud.class, dev.stella.client.modules.hud.PotionHud.class,
                                dev.stella.client.modules.hud.ItemCounterHud.class, dev.stella.client.modules.pvp.TotemPopCounter.class))
                            ModuleManager.get(c).setEnabled(true);
                        var h3 = mc.player.networkHandler;
                        h3.sendChatCommand("gamemode survival");
                        h3.sendChatCommand("tp @s " + ox + " " + (oy + 1) + " " + oz);
                        h3.sendChatCommand("item replace entity @s armor.head with minecraft:diamond_helmet[damage=300]");
                        h3.sendChatCommand("item replace entity @s armor.chest with minecraft:netherite_chestplate[damage=100]");
                        h3.sendChatCommand("item replace entity @s armor.legs with minecraft:diamond_leggings");
                        h3.sendChatCommand("item replace entity @s armor.feet with minecraft:diamond_boots[damage=410]");
                        h3.sendChatCommand("item replace entity @s weapon.mainhand with minecraft:netherite_sword");
                        h3.sendChatCommand("item replace entity @s weapon.offhand with minecraft:totem_of_undying");
                        h3.sendChatCommand("give @s minecraft:totem_of_undying 3");
                        h3.sendChatCommand("give @s minecraft:end_crystal 64");
                        h3.sendChatCommand("give @s minecraft:obsidian 32");
                        h3.sendChatCommand("give @s minecraft:ender_pearl 16");
                        h3.sendChatCommand("give @s minecraft:golden_apple 12");
                        h3.sendChatCommand("effect give @s minecraft:strength 90 1");
                        h3.sendChatCommand("effect give @s minecraft:speed 45 0");
                        mc.inGameHud.getChatHud().clear(false);
                    }
                    if (inStage() == 644) mc.player.networkHandler.sendChatCommand("damage @s 100");
                    if (inStage() == 656) StellaClient.LOGGER.info("[smoke] totem pops counted for self: {}",
                            ModuleManager.get(dev.stella.client.modules.pvp.TotemPopCounter.class).pops(mc.player.getUuid()));
                    if (inStage() == 658) shot(mc, "new-huds");
                    if (inStage() == 660) {
                        // Death Coordinates + Toggle Sprint
                        ModuleManager.get(dev.stella.client.modules.misc.ToggleSprint.class).setEnabled(true);
                        ModuleManager.get(dev.stella.client.modules.misc.DeathCoordinates.class).setEnabled(true);
                        mc.player.networkHandler.sendChatCommand("kill @s");
                    }
                    if (inStage() == 668) {
                        boolean dead = mc.player.isDead();
                        String where = dev.stella.client.modules.misc.DeathCoordinates.lastDeath;
                        StellaClient.LOGGER.info("[smoke] death coordinates: dead={} recorded at {}", dead, where);
                        if (where == null) fail("Death Coordinates did not record the death");
                        mc.player.requestRespawn();
                    }
                    if (inStage() == 674) {
                        mc.setScreen(null);
                        StellaClient.LOGGER.info("[smoke] toggle sprint: sprint key held={}", mc.options.sprintKey.isPressed());
                        ModuleManager.get(dev.stella.client.modules.misc.ToggleSprint.class).setEnabled(false);
                        mc.player.networkHandler.sendChatCommand("gamemode creative");
                    }
                    if (inStage() == 678) {
                        // Player Detector: one player hidden behind a wall, one standing in the open
                        for (Module m : ModuleManager.all())
                            if (m.getCategory() == dev.stella.client.module.Category.HUD && !m.getName().equals("Stella Branding HUD")) m.setEnabled(false);
                        for (String n : new String[]{"Player ESP", "Glow Outline", "Anti Vanish", "Totem Pop Counter"})
                            ModuleManager.all().stream().filter(m -> m.getName().equals(n)).forEach(m -> m.setEnabled(false));
                        var pd = ModuleManager.get(dev.stella.client.modules.combat.PlayerDetector.class);
                        setting("Player Detector", "Detect", "Hidden Only");
                        pd.setEnabled(true);
                        mc.inGameHud.getChatHud().clear(false);
                        mc.player.setYaw(-90f);
                        mc.player.setPitch(10f);
                        detectorScene(mc, true);
                    }
                    if (inStage() == 696) logDetector("hidden only");
                    if (inStage() == 698) shot(mc, "player-detector");
                    if (inStage() == 700) setting("Player Detector", "Detect", "All Players");
                    if (inStage() == 707) logDetector("all players");
                    if (inStage() == 709) detectorScene(mc, false);
                    if (inStage() == 712) {
                        // Light Level Overlay + Auto Tool: a pitch-dark stone room, sword in hand, pickaxe in the hotbar
                        int x = mc.player.getBlockX(), y = mc.player.getBlockY(), z = mc.player.getBlockZ();
                        serverCmd(mc, "fill " + (x - 4) + " " + (y - 1) + " " + (z - 4) + " " + (x + 4) + " " + (y + 4) + " " + (z + 4) + " minecraft:stone hollow");
                        serverCmd(mc, "gamemode survival " + mc.player.getGameProfile().name());
                        serverCmd(mc, "clear " + mc.player.getGameProfile().name());
                        serverCmd(mc, "item replace entity " + mc.player.getGameProfile().name() + " hotbar.0 with minecraft:diamond_sword");
                        serverCmd(mc, "item replace entity " + mc.player.getGameProfile().name() + " hotbar.3 with minecraft:diamond_pickaxe");
                        serverCmd(mc, "tp " + mc.player.getGameProfile().name() + " " + (x + 0.5) + " " + y + " " + (z + 0.5) + " -90 40");
                        serverCmd(mc, "setblock " + (x + 2) + " " + (y + 1) + " " + z + " minecraft:stone"); // block to mine, right in front
                        serverCmd(mc, "setblock " + (x + 2) + " " + y + " " + (z + 1) + " minecraft:torch");
                        serverCmd(mc, "setblock " + (x - 2) + " " + (y + 4) + " " + (z - 2) + " minecraft:glowstone");
                        ModuleManager.get(dev.stella.client.modules.combat.PlayerDetector.class).setEnabled(false);
                        setting("Light Level Overlay", "Style", "Both");
                        ModuleManager.get(dev.stella.client.modules.world.LightLevelOverlay.class).setEnabled(true);
                        ModuleManager.get(dev.stella.client.modules.misc.AutoTool.class).setEnabled(true);
                        mc.player.setYaw(-90f);
                        mc.player.setPitch(40f);
                        mc.inGameHud.getChatHud().clear(false);
                    }
                    if (inStage() == 724) shot(mc, "light-overlay");
                    if (inStage() == 726) {
                        mc.player.setPitch(0f);
                        mc.player.getInventory().setSelectedSlot(0);
                    }
                    // CI has no real mouse (Minecraft cancels breaking without a captured cursor), so drive Auto Tool's logic directly
                    if (inStage() >= 727 && inStage() < 733) {
                        var at = ModuleManager.get(dev.stella.client.modules.misc.AutoTool.class);
                        at.update(true);
                        if (mc.player.getInventory().getSelectedSlot() == 3) autoToolPicked = true;
                        StellaClient.LOGGER.info("[smoke] auto tool tick: {} | slot {}", dev.stella.client.modules.misc.AutoTool.lastDecision,
                                mc.player.getInventory().getSelectedSlot());
                    }
                    if (inStage() == 733) ModuleManager.get(dev.stella.client.modules.misc.AutoTool.class).update(false);
                    if (inStage() == 734) {
                        StellaClient.LOGGER.info("[smoke] auto tool picked the pickaxe while mining stone: {}", autoToolPicked);
                        if (!autoToolPicked) fail("Auto Tool did not pick the pickaxe");
                    }
                    if (inStage() == 736) {
                        int sel = mc.player.getInventory().getSelectedSlot();
                        StellaClient.LOGGER.info("[smoke] auto tool after mining: slot {}", sel);
                        if (sel != 0) fail("Auto Tool did not switch back");
                        StellaClient.LOGGER.info("[smoke] light overlay lit blocks: {}", dev.stella.client.modules.world.LightLevelOverlay.lastCount);
                        if (dev.stella.client.modules.world.LightLevelOverlay.lastCount == 0) fail("Light Level Overlay found no lit blocks");
                        serverCmd(mc, "gamemode creative " + mc.player.getGameProfile().name());
                    }
                    if (inStage() == 740) mc.reloadResources(); // shows the startup / reload overlay
                    if (inStage() == 750 && mc.getOverlay() != null) shot(mc, "loading-splash");
                    if (inStage() > 750 && inStage() < 900 && mc.getOverlay() != null) return; // wait for the reload to finish
                    // Animation showcase for the preview video: GUI open, toggles sweeping, settings pop, staggered close
                    if (inStage() == 900) { mc.inGameHud.getChatHud().clear(false); mc.setScreen(new ClickGuiScreen()); }
                    if (inStage() >= 916 && inStage() <= 940 && (inStage() - 916) % 6 == 0) {
                        String[] showcase = {"Fullbright", "Zoom", "Item Physics", "Swing Speed", "Custom Crosshair"};
                        String n = showcase[(inStage() - 916) / 6];
                        ModuleManager.all().stream().filter(m -> m.getName().equals(n)).forEach(Module::toggle);
                    }
                    if (inStage() == 946 && mc.currentScreen instanceof ClickGuiScreen gui) gui.openSettingsFor("Custom Sky");
                    if (inStage() == 950) shot(mc, "animations-gui");
                    if (inStage() == 962 && mc.currentScreen != null) mc.currentScreen.close();
                    // ESP / tracers / Sus Chunk showcase: a hidden stash with a kelp farm at Y -30
                    if (inStage() == 980) {
                        int bx = ox - 40, by = -30, bz = oz + 20;
                        stashX = bx; stashZ = bz;
                        String[] cmds = {
                            "forceload add " + (bx - 16) + " " + (bz - 16) + " " + (bx + 28) + " " + (bz + 26),
                            "fill " + bx + " " + (by - 1) + " " + bz + " " + (bx + 12) + " " + (by + 5) + " " + (bz + 10) + " minecraft:deepslate[axis=x] hollow",
                            // storage wall
                            "setblock " + (bx + 2) + " " + by + " " + (bz + 1) + " minecraft:chest[facing=south]",
                            "setblock " + (bx + 3) + " " + by + " " + (bz + 1) + " minecraft:chest[facing=south]",
                            "setblock " + (bx + 4) + " " + by + " " + (bz + 1) + " minecraft:chest[facing=south]",
                            "setblock " + (bx + 5) + " " + by + " " + (bz + 1) + " minecraft:barrel[facing=up]",
                            "setblock " + (bx + 6) + " " + by + " " + (bz + 1) + " minecraft:purple_shulker_box",
                            "setblock " + (bx + 7) + " " + by + " " + (bz + 1) + " minecraft:magenta_shulker_box",
                            "setblock " + (bx + 8) + " " + by + " " + (bz + 1) + " minecraft:ender_chest[facing=south]",
                            "setblock " + (bx + 2) + " " + (by + 1) + " " + (bz + 1) + " minecraft:chest[facing=south]",
                            "setblock " + (bx + 3) + " " + (by + 1) + " " + (bz + 1) + " minecraft:chest[facing=south]",
                            // dispenser row
                            "fill " + (bx + 2) + " " + by + " " + (bz + 9) + " " + (bx + 5) + " " + by + " " + (bz + 9) + " minecraft:dispenser[facing=north]",
                            // kelp farm: glass column with water + kelp, observer watching it, piston, redstone, hoppers into a chest
                            "fill " + (bx + 8) + " " + by + " " + (bz + 4) + " " + (bx + 10) + " " + (by + 4) + " " + (bz + 6) + " minecraft:glass",
                            "fill " + (bx + 9) + " " + by + " " + (bz + 5) + " " + (bx + 9) + " " + (by + 4) + " " + (bz + 5) + " minecraft:water",
                            "fill " + (bx + 9) + " " + by + " " + (bz + 5) + " " + (bx + 9) + " " + (by + 2) + " " + (bz + 5) + " minecraft:kelp_plant",
                            "setblock " + (bx + 9) + " " + (by + 3) + " " + (bz + 5) + " minecraft:kelp",
                            "setblock " + (bx + 9) + " " + (by + 2) + " " + (bz + 4) + " minecraft:observer[facing=south]",
                            "setblock " + (bx + 9) + " " + (by + 1) + " " + (bz + 4) + " minecraft:sticky_piston[facing=south]",
                            "setblock " + (bx + 9) + " " + (by + 2) + " " + (bz + 3) + " minecraft:redstone_wire",
                            "setblock " + (bx + 9) + " " + (by + 1) + " " + (bz + 3) + " minecraft:repeater[facing=north]",
                            "setblock " + (bx + 10) + " " + by + " " + (bz + 3) + " minecraft:hopper[facing=east]",
                            "setblock " + (bx + 11) + " " + by + " " + (bz + 3) + " minecraft:chest[facing=west]",
                            "setblock " + (bx + 8) + " " + (by + 2) + " " + (bz + 7) + " minecraft:observer[facing=north]",
                            "setblock " + (bx + 1) + " " + (by + 2) + " " + (bz + 5) + " minecraft:lantern[hanging=false]",
                            "setblock " + (bx + 11) + " " + (by + 2) + " " + (bz + 8) + " minecraft:lantern[hanging=false]"};
                        for (String c : cmds) serverCmd(mc, c);
                        // modules: Storage ESP + Block ESP (farm parts) with neon tracers, Sus Chunk Finder; clean HUD
                        for (Module m : ModuleManager.all())
                            if (m.getCategory() == dev.stella.client.module.Category.HUD && !m.getName().equals("Stella Branding HUD")
                                    && !m.getName().equals("Coordinates HUD")) m.setEnabled(false);
                        for (String n : new String[]{"Light Level Overlay", "Auto Tool", "Player Detector", "Spawner Finder", "Glow Outline"})
                            ModuleManager.all().stream().filter(m -> m.getName().equals(n)).forEach(m -> m.setEnabled(false));
                        setting("Block ESP", "Blocks", new java.util.ArrayList<>(java.util.List.of("minecraft:observer", "minecraft:sticky_piston",
                                "minecraft:piston", "minecraft:dispenser", "minecraft:hopper")));
                        setting("Block ESP", "Tracers", true);
                        setting("Storage ESP", "Tracers", true);
                        setting("Block ESP", "Tracer Origin", "Crosshair");
                        setting("Storage ESP", "Tracer Origin", "Crosshair");
                        setting("Sus Chunk Finder", "Only Below Y", true);
                        setting("Sus Chunk Finder", "Flat Y", (double) oy + 1);
                        setting("Sus Chunk Finder", "Range", 192.0);
                        for (String n : new String[]{"Storage ESP", "Block ESP", "Sus Chunk Finder", "Custom Sky"})
                            ModuleManager.all().stream().filter(m -> m.getName().equals(n)).forEach(m -> { m.setEnabled(false); m.setEnabled(true); });
                        serverCmd(mc, "gamemode spectator " + mc.player.getGameProfile().name());
                        // aerial: look down at 62 degrees so the room (centre bz+5, y by+2) sits in the middle of the frame
                        double camY = oy + 18, back = (camY - (by + 2)) / Math.tan(Math.toRadians(62));
                        serverCmd(mc, "tp " + mc.player.getGameProfile().name() + " " + (bx + 6.5) + " " + camY + " " + (bz + 5.5 - back) + " 0 62");
                        mc.inGameHud.getChatHud().clear(false);
                    }
                    if (inStage() == 1006) { mc.player.setYaw(0f); mc.player.setPitch(62f); }
                    if (inStage() == 1012) {
                        var sus = ModuleManager.get(dev.stella.client.modules.combat.SusChunkFinder.class);
                        StellaClient.LOGGER.info("[smoke] stash: sus chunks flagged {}", sus.suspectChunks().size());
                    }
                    if (inStage() == 1013 && holdForTerrain(mc, 900)) return;
                    if (inStage() == 1014) shot(mc, "stash-aerial");
                    if (inStage() == 1016) serverCmd(mc, "tp " + mc.player.getGameProfile().name() + " " + (stashX + 1.5) + " -29 " + (stashZ + 8.5) + " -135 12");
                    if (inStage() == 1022) { mc.player.setYaw(-135f); mc.player.setPitch(12f); }
                    if (inStage() == 1033 && holdForTerrain(mc, 900)) return;
                    if (inStage() == 1034) shot(mc, "stash-inside");
                    // ================= DonutSMP tour: every DonutSMP module (plus Auto Tool) one after another =================
                    int T = inStage(), bx = stashX, bz = stashZ, by = -30;
                    String me = mc.player.getGameProfile().name();
                    if (T == 1040) {
                        for (String c : new String[]{"time set noon", "weather clear", "gamerule sendCommandFeedback false", "gamerule doDaylightCycle false"}) serverCmd(mc, c);
                        setting("Spawner Finder", "Chat Message", false);
                        ModuleManager.get(dev.stella.client.modules.combat.SusChunkFinder.class).setEnabled(true);
                        for (String n : new String[]{"Death Coordinates", "Light Level Overlay"})
                            ModuleManager.all().stream().filter(m -> m.getName().equals(n)).forEach(m -> m.setEnabled(false));
                        caption("Storage ESP + Block ESP", "Stash + kelp farm 30 blocks underground - every container and redstone part, with tracers");
                        only("Storage ESP", "Block ESP");
                    }
                    if (T > 1040 && T < 1070) mc.player.setYaw(-135f + (T - 1040) * 2.5f);
                    if (T == 1070) shot(mc, "tour-1-storage-block-esp");
                    if (T == 1074) {
                        caption("Sus Chunk Finder", "Flags chunks that hide a base underground - you just get a chat message");
                        only();
                        var sus = ModuleManager.get(dev.stella.client.modules.combat.SusChunkFinder.class);
                        sus.resetChatMessages();
                        double tx = bx + 6.5, tz = bz + 5.5, best = Double.MAX_VALUE;
                        for (var c : sus.suspectChunks()) {
                            double cx = c.cx() * 16 + 8, cz = c.cz() * 16 + 8, d = (cx - mc.player.getX()) * (cx - mc.player.getX()) + (cz - mc.player.getZ()) * (cz - mc.player.getZ());
                            if (d < best) { best = d; tx = cx; tz = cz; }
                        }
                        StellaClient.LOGGER.info("[smoke] tour sus camera on {} {} ({} flagged)", tx, tz, sus.suspectChunks().size());
                        aerial(mc, tx, oy, tz, oy + 34, 52f);
                    }
                    if (T == 1080) { mc.player.setYaw(camYaw); mc.player.setPitch(camPitch); }
                    if (T == 1093 && holdForTerrain(mc, 900)) return;
                    if (T == 1100) shot(mc, "tour-2-sus-chunk");
                    if (T == 1104) {
                        caption("Spawner Finder", "Beacon beams on every spawner, coloured by mob - list sorted by distance");
                        serverCmd(mc, "forceload add " + (bx + 12) + " " + (bz - 16) + " " + (bx + 48) + " " + (bz + 30));
                        String[][] sp = {{"22", "-40", "2", "zombie"}, {"30", "-38", "9", "skeleton"}, {"18", "-44", "15", "blaze"},
                                {"34", "-42", "-3", "cave_spider"}, {"26", "-36", "20", "spider"}};
                        for (String[] o : sp) serverCmd(mc, "setblock " + (bx + Integer.parseInt(o[0])) + " " + o[1] + " " + (bz + Integer.parseInt(o[2]))
                                + " minecraft:spawner{SpawnData:{entity:{id:\"minecraft:" + o[3] + "\"}}}");
                    }
                    if (T == 1110) {
                        only("Spawner Finder");
                        aerial(mc, bx + 26.5, oy, bz + 8.5, oy + 14, 22f);
                    }
                    if (T == 1116) { mc.player.setYaw(camYaw); mc.player.setPitch(camPitch); }
                    if (T == 1133 && holdForTerrain(mc, 900)) return;
                    if (T == 1135) StellaClient.LOGGER.info("[smoke] tour: spawner finder sees {}", ModuleManager.get(dev.stella.client.modules.combat.SpawnerFinder.class).spawners().stream()
                            .map(x -> x.type() + "@" + x.pos().toShortString()).collect(java.util.stream.Collectors.joining(", ")));
                    if (T == 1140) shot(mc, "tour-3-spawner-finder");
                    if (T == 1144) {
                        caption("Player Detector + Player ESP", "Blue chunk + list for players the client knows about but you can't see");
                        only("Player Detector", "Player ESP", "Radar HUD");
                        setting("Radar HUD", "Range", 32.0);
                        setting("Radar HUD", "Distance", true);
                        setting("Player ESP", "Tracers", true);
                        serverCmd(mc, "fill " + (bx + 15) + " " + by + " " + (bz + 3) + " " + (bx + 17) + " " + (by + 2) + " " + (bz + 5) + " minecraft:air");
                        serverCmd(mc, "setblock " + (bx + 16) + " " + (by + 2) + " " + (bz + 3) + " minecraft:lantern[hanging=true]");
                        tourPlayers(mc, true, bx + 16, by, bz + 4, bx + 6, by, bz + 8);
                        camYaw = -112f; camPitch = 6f;
                        serverCmd(mc, "tp " + me + " " + (bx + 2.5) + " " + by + " " + (bz + 2.5) + " -112 6");
                    }
                    if (T == 1150) { mc.player.setYaw(camYaw); mc.player.setPitch(camPitch); }
                    if (T == 1163 && holdForTerrain(mc, 600)) return;
                    if (T > 1163 && T < 1172) { mc.player.setYaw(-112f + (T - 1163) * 1.5f); }
                    if (T == 1168) logDetectorTour();
                    if (T == 1172) shot(mc, "tour-4-player-detector");
                    if (T == 1173) {
                        // close-up of the radar for the preview
                        var rd = ModuleManager.get(dev.stella.client.modules.hud.RadarHud.class);
                        StellaClient.LOGGER.info("[smoke] radar hud at {} {} size {}x{}", rd.x(), rd.y(), rd.getWidth(), rd.getHeight());
                    }
                    if (T == 1176) {
                        tourPlayers(mc, false, 0, 0, 0, 0, 0, 0);
                        caption("Staff List + Radar", "A staff member from staff.json joins - alert, and their head on the radar");
                        only("Staff List", "Radar HUD");
                        aerial(mc, bx + 6.5, oy, bz + 5.5, oy + 10, 12f);
                    }
                    if (T == 1180) { mc.player.setYaw(camYaw); mc.player.setPitch(camPitch); }
                    if (T == 1182) staffTest(mc, 1);
                    if (T == 1196) logAntiVanish("tour");
                    if (T == 1200) shot(mc, "tour-5-staff-radar");
                    if (T == 1204) {
                        staffTest(mc, 3);
                        caption("Media Base Finder + Region Map", "Shows media players in your region and which region you're in");
                        try {
                            java.nio.file.Files.writeString(dev.stella.client.data.MediaDatabase.file(), "{\"media\": [\"MediaStreamer\"]}");
                        } catch (Exception ex) { StellaClient.LOGGER.warn("[smoke] media.json", ex); }
                        dev.stella.client.data.MediaDatabase.load();
                        only("Media Base Finder", "Region Map HUD");
                    }
                    if (T == 1210) mediaPlayer(mc, true);
                    if (T == 1229) mc.inGameHud.getChatHud().clear(false);
                    if (T == 1230) shot(mc, "tour-6-media-region");
                    if (T == 1234) {
                        mediaPlayer(mc, false);
                        caption("Auto Tool", "Switches to the best tool for the block you mine, then back");
                        only("Auto Tool");
                        serverCmd(mc, "gamemode survival " + me);
                        serverCmd(mc, "clear " + me);
                        serverCmd(mc, "item replace entity " + me + " hotbar.0 with minecraft:netherite_sword[enchantments={sharpness:5}]");
                        serverCmd(mc, "item replace entity " + me + " hotbar.1 with minecraft:diamond_pickaxe[enchantments={efficiency:5}]");
                        serverCmd(mc, "item replace entity " + me + " hotbar.2 with minecraft:enchanted_book");
                        serverCmd(mc, "item replace entity " + me + " armor.chest with minecraft:netherite_chestplate[enchantments={protection:4}]");
                        ModuleManager.get(dev.stella.client.modules.cosmetics.CustomGlint.class).setEnabled(true);
                        serverCmd(mc, "item replace entity " + me + " hotbar.2 with minecraft:netherite_pickaxe");
                        serverCmd(mc, "item replace entity " + me + " hotbar.4 with minecraft:netherite_axe");
                        serverCmd(mc, "item replace entity " + me + " hotbar.6 with minecraft:netherite_shovel");
                        serverCmd(mc, "setblock " + (bx + 1) + " " + (by + 1) + " " + (bz + 3) + " minecraft:oak_log");
                        serverCmd(mc, "setblock " + (bx + 1) + " " + (by + 1) + " " + (bz + 7) + " minecraft:dirt");
                        serverCmd(mc, "tp " + me + " " + (bx + 3.5) + " " + by + " " + (bz + 5.5) + " 90 10");
                    }
                    if (T == 1240) { mc.player.setYaw(90f); mc.player.setPitch(10f); mc.player.getInventory().setSelectedSlot(0); }
                    if (T == 1243 && holdForTerrain(mc, 600)) return;
                    if (T == 1244) mc.inGameHud.getChatHud().clear(false);
                    // look at deepslate (pickaxe) -> oak log (axe) -> dirt (shovel)
                    if (T >= 1246 && T < 1300) {
                        int seg = (T - 1246) / 18, k = (T - 1246) % 18;
                        float[] yaws = {90f, 90f + 39f, 90f - 39f};
                        float[] pitches = {10f, 14f, 14f};
                        mc.player.setYaw(yaws[seg]); mc.player.setPitch(pitches[seg]);
                        var at = ModuleManager.get(dev.stella.client.modules.misc.AutoTool.class);
                        if (k >= 2 && k < 14) at.update(true); else at.update(false);
                        if (k == 2) mc.player.getInventory().setSelectedSlot(0);
                        if (k == 8) {
                            captionSub = "Mining " + new String[]{"deepslate", "an oak log", "dirt"}[seg] + "  ->  " + mc.player.getMainHandStack().getName().getString();
                            StellaClient.LOGGER.info("[smoke] tour auto tool: {} ({}) slot {}", captionSub, dev.stella.client.modules.misc.AutoTool.lastDecision, mc.player.getInventory().getSelectedSlot());
                        }
                        if (k == 12) shot(mc, "tour-7-auto-tool-" + seg);
                    }
                    if (T == 1302) {
                        serverCmd(mc, "gamemode spectator " + me);
                        caption("Everything together", "ESP, tracers, spawner beams and Sus Chunk Finder over the stash");
                        only("Storage ESP", "Block ESP", "Spawner Finder");
                        aerial(mc, bx + 16.5, oy, bz + 8.5, oy + 30, 40f);
                    }
                    if (T == 1308) { mc.player.setYaw(camYaw); mc.player.setPitch(camPitch); }
                    if (T == 1321 && holdForTerrain(mc, 600)) return;
                    if (T == 1312 || T == 1329) mc.inGameHud.getChatHud().clear(false);
                    if (T == 1330) shot(mc, "tour-8-everything");
                    if (T == 1340) caption = null;
                    // ================= Cosmetics: wings, Chinese hat, jump circles / landing star, knife skins =================
                    if (T == 1344) {
                        only();
                        for (String n : new String[]{"Sus Chunk Finder", "Radar HUD", "Notifications HUD"})
                            ModuleManager.all().stream().filter(m -> m.getName().equals(n)).forEach(m -> m.setEnabled(false));
                        serverCmd(mc, "gamemode creative " + me);
                        serverCmd(mc, "kill @e[type=item]");
                        serverCmd(mc, "fill " + (ox - 6) + " " + (oy - 1) + " " + (oz - 6) + " " + (ox + 6) + " " + (oy - 1) + " " + (oz + 6) + " minecraft:smooth_quartz");
                        serverCmd(mc, "fill " + (ox - 6) + " " + oy + " " + (oz - 6) + " " + (ox + 6) + " " + (oy + 6) + " " + (oz + 6) + " minecraft:air");
                        serverCmd(mc, "tp " + me + " " + (ox + 0.5) + " " + oy + " " + (oz + 0.5) + " 180 15");
                        setting("Wings", "Style", "Angel");
                        for (String n : new String[]{"Wings", "Chinese Hat", "Jump Circles"})
                            ModuleManager.all().stream().filter(m -> m.getName().equals(n)).forEach(m -> m.setEnabled(true));
                        mc.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK);
                        caption("Cosmetics \u2022 Wings + Chinese Hat", "Angel wings and a glowing hat - only you see them (third person)");
                    }
                    if (T == 1350) { mc.player.getAbilities().flying = false; mc.player.sendAbilitiesUpdate(); mc.inGameHud.getChatHud().clear(false); }
                    if (T == 1351) { mc.player.setYaw(180f); mc.player.setPitch(15f); mc.player.bodyYaw = 180f; mc.player.lastBodyYaw = 180f; }
                    if (T == 1353 && holdForTerrain(mc, 400)) return;
                    if (T == 1356) shot(mc, "cosmetics-wings-back");
                    if (T == 1358) { mc.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_FRONT); setting("Wings", "Style", "Demon"); caption("Cosmetics \u2022 Demon Wings", "Three wing styles: Angel, Demon and Crystal"); }
                    if (T == 1364) shot(mc, "cosmetics-demon-front");
                    if (T == 1366) { mc.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK); setting("Wings", "Style", "Crystal"); setting("Chinese Hat", "Color", "Rainbow"); caption("Cosmetics \u2022 Crystal Wings", "Rainbow hat, shimmering crystal wings"); }
                    if (T == 1372) shot(mc, "cosmetics-crystal");
                    // jump circles: a real jump (ring on take-off, Stella star on landing)
                    if (T == 1374) {
                        setting("Wings", "Style", "Angel"); setting("Chinese Hat", "Color", "Theme");
                        caption("Cosmetics \u2022 Jump Circles", "A ring when you jump - the Stella star when you land");
                        mc.player.setPitch(72f);
                    }
                    if (T == 1376) mc.player.jump();
                    if (T > 1376 && T < 1400 && mc.player.isOnGround() && T > 1380 && !jumpLogged) {
                        jumpLogged = true;
                        StellaClient.LOGGER.info("[smoke] jump circles: effects after landing {}", ModuleManager.get(dev.stella.client.modules.cosmetics.JumpCircles.class).activeEffects());
                        if (ModuleManager.get(dev.stella.client.modules.cosmetics.JumpCircles.class).activeEffects() < 1) fail("Jump Circles: no landing star after a jump");
                        landedAt = T;
                    }
                    if (T == 1400 && !jumpLogged) StellaClient.LOGGER.warn("[smoke] jump circles: player never landed (onGround={}, flying={})", mc.player.isOnGround(), mc.player.getAbilities().flying);
                    if (landedAt > 0 && T == landedAt + 3) shot(mc, "cosmetics-landing-star");
                    if (T == 1404) {
                        // a fresh star to catch it fully bloomed
                        ModuleManager.get(dev.stella.client.modules.cosmetics.JumpCircles.class).spawnStar();
                    }
                    if (T == 1407) shot(mc, "cosmetics-landing-star-2");
                    // knife skins (first person) + inspect
                    if (T == 1410) {
                        for (String n : new String[]{"Wings", "Chinese Hat", "Jump Circles"})
                            ModuleManager.all().stream().filter(m -> m.getName().equals(n)).forEach(m -> m.setEnabled(false));
                        mc.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);
                        serverCmd(mc, "item replace entity " + me + " hotbar.0 with minecraft:diamond_sword");
                        mc.player.getInventory().setSelectedSlot(0);
                        mc.player.setPitch(10f);
                        ModuleManager.get(dev.stella.client.modules.cosmetics.KnifeSkins.class).setEnabled(true);
                    }
                    String[][] knives = {{"Karambit", "Nebula"}, {"Butterfly", "Damascus"}, {"Bayonet", "Ember"}, {"Stella Dagger", "Sunset"}, {"Kunai", "Galaxy"}, {"Tanto", "Chrome"}};
                    if (T >= 1418 && T < 1418 + knives.length * 8) {
                        int i = (T - 1418) / 8, k = (T - 1418) % 8;
                        var ks = ModuleManager.get(dev.stella.client.modules.cosmetics.KnifeSkins.class);
                        String slug = "knife-" + knives[i][0].toLowerCase(java.util.Locale.ROOT).replace(' ', '-');
                        if (k == 0) {
                            ks.setKnife(knives[i][0]); ks.setFinish(knives[i][1]);
                            ks.setWear(knives[i][0].equals("Tanto") ? 0.45 : 0.0);
                            dev.stella.client.modules.cosmetics.KnifeSkins.previewT = -1f;
                            caption("Cosmetics \u2022 Knife Skins", knives[i][0] + " | " + knives[i][1] + (knives[i][0].equals("Tanto") ? " (worn)" : "") + "  -  press Y to inspect");
                        }
                        if (k == 2) shot(mc, slug);
                        if (k == 3) dev.stella.client.modules.cosmetics.KnifeSkins.previewT = 0.3f;
                        if (k == 5) shot(mc, slug + "-inspect");
                        if (k == 6) dev.stella.client.modules.cosmetics.KnifeSkins.previewT = 0.66f;
                        if (k == 7) shot(mc, slug + "-trick");
                    }
                    if (T == 1418 + knives.length * 8) {
                        dev.stella.client.modules.cosmetics.KnifeSkins.previewT = -1f;
                        var ks = ModuleManager.get(dev.stella.client.modules.cosmetics.KnifeSkins.class);
                        ks.setKnife("Karambit"); ks.setFinish("Nebula"); ks.setWear(0.0);
                        ks.inspect();
                    }
                    // custom enchantment glint on armor + items (third person front)
                    if (T == 1468) {
                        ModuleManager.get(dev.stella.client.modules.cosmetics.KnifeSkins.class).setEnabled(false);
                        mc.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_FRONT);
                        mc.player.setPitch(5f);
                        caption("Cosmetics \u2022 Custom Glint", "Stella sparkle glint in your theme colour");
                    }
                    if (T == 1474) shot(mc, "glint-stella");
                    if (T == 1476) { setting("Custom Glint", "Style", "Waves"); setting("Custom Glint", "Color", "Rainbow"); caption("Cosmetics \u2022 Custom Glint", "Rainbow waves - pick style, colour, speed and strength"); }
                    if (T == 1482) shot(mc, "glint-rainbow");
                    if (T == 1484) {
                        mc.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);
                        ModuleManager.get(dev.stella.client.modules.cosmetics.KnifeSkins.class).setEnabled(true);
                        setting("Custom Glint", "Style", "Stella"); setting("Custom Glint", "Color", "Theme");
                        caption("Cosmetics \u2022 Enchanted Knife", "Enchanted swords shimmer in your glint colour");
                    }
                    if (T == 1490) shot(mc, "knife-enchanted");
                    if (T == 1494) caption = null;
                    // Anti Cheat Off: allowed in singleplayer (and only on servers listed in Test Environment)
                    if (T == 1498) {
                        caption = null;
                        boolean ok = dev.stella.client.modules.anticheatoff.TestEnvironment.allowed();
                        ModuleManager.get(dev.stella.client.modules.anticheatoff.FastMine.class).setEnabled(true);
                        ModuleManager.get(dev.stella.client.modules.anticheatoff.SpeedMine.class).setEnabled(true);
                        StellaClient.LOGGER.info("[smoke] anti cheat off: allowed in singleplayer = {}", ok);
                        if (!ok) fail("Test Environment should allow singleplayer");
                        mc.setScreen(new ClickGuiScreen());
                    }
                    if (T == 1504 && mc.currentScreen instanceof ClickGuiScreen gui) gui.openSettingsFor("Test Environment");
                    if (T == 1508) {
                        boolean still = ModuleManager.get(dev.stella.client.modules.anticheatoff.SpeedMine.class).isEnabled();
                        StellaClient.LOGGER.info("[smoke] anti cheat off: SpeedMine still enabled after ticks = {}", still);
                        if (!still) fail("SpeedMine switched itself off in singleplayer");
                        shot(mc, "anti-cheat-off-gui");
                    }
                    if (T == 1512) {
                        mc.setScreen(null);
                        ModuleManager.get(dev.stella.client.modules.anticheatoff.FastMine.class).setEnabled(false);
                        ModuleManager.get(dev.stella.client.modules.anticheatoff.SpeedMine.class).setEnabled(false);
                    }
                    // ================= Anti Cheat Off showcase (singleplayer): simulated key presses =================
                    if (T >= 1516 && T < 1830) acoDemo(mc, T, me);
                    // ================= Free Look: orbit around a closed room, walls on / off =================
                    if (T >= 1832 && T < 1912) freeLookDemo(mc, T, me);
                    if (T >= 1914 && T < 2000) newFeaturesDemo(mc, T, me);
                    // ================= Liquid Glass theme =================
                    if (T == 2000) {
                        setting("ClickGUI", "Theme", "Liquid Glass");
                        mc.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);
                        mc.player.setPitch(-8f);
                        on("Radar HUD", "Clock HUD", "CPS Graph HUD", "Money Tracker HUD", "Armor HUD", "Coordinates HUD", "Performance HUD", "Combat Timer HUD", "Notifications HUD");
                        ModuleManager.get(dev.stella.client.modules.hud.CombatTimerHud.class).tag();
                        dev.stella.client.notification.NotificationManager.post("Liquid Glass", "Theme applied to the GUI and every HUD card",
                                dev.stella.client.notification.Notification.Type.SUCCESS, 8000);
                        caption = null;
                    }
                    if (T == 2005) mc.inGameHud.getChatHud().clear(false);
                    if (T == 2006) shot(mc, "glass-hud");
                    if (T == 2008) mc.setScreen(new ClickGuiScreen());
                    if (T == 2016) shot(mc, "glass-gui");
                    if (T == 2018 && mc.currentScreen instanceof ClickGuiScreen gui) gui.openSettingsFor("Knife Skins");
                    if (T == 2024) shot(mc, "glass-settings");
                    if (T == 2026) { mc.setScreen(null); setting("ClickGUI", "Theme", "Stella"); }
                    if (inStage() == 2034) {
                        int errors = StellaClient.errorCount();
                        if (errors > 0) fail(errors + " runtime errors: " + StellaClient.errorSummary());
                        else {
                            StellaClient.LOGGER.info("STELLA_SMOKE_OK");
                            System.out.println("STELLA_SMOKE_OK");
                            mc.scheduleStop();
                        }
                        next();
                    }
                }
                default -> { }
            }
        } catch (Throwable t) {
            StellaClient.LOGGER.error("[smoke] exception", t);
            fail(t.toString());
        }
    }

    private static int held;

    /** Freezes the current stage until the chunk meshes around the camera are built (CI renders at ~5 FPS). */
    private static boolean holdForTerrain(MinecraftClient mc, int maxTicks) {
        if (held < 40 || (!mc.worldRenderer.isTerrainRenderComplete() && held < maxTicks)) { held++; stageStart++; return true; }
        StellaClient.LOGGER.info("[smoke] terrain ready after {} ticks", held);
        held = 0;
        return false;
    }

    private static final java.util.UUID ENEMY_UUID = java.util.UUID.fromString("5e1a5a11-0000-4000-8000-0000000000f1");
    private static final java.util.UUID BUDDY_UUID = java.util.UUID.fromString("5e1a5a11-0000-4000-8000-0000000000f2");

    private static net.minecraft.entity.player.PlayerEntity clientPlayer(MinecraftClient mc, String name) {
        for (var p : mc.world.getPlayers()) if (p.getGameProfile().name().equals(name)) return p;
        return null;
    }

    private static void on(String... names) {
        for (String n : names) ModuleManager.all().stream().filter(m -> m.getName().equals(n)).forEach(m -> m.setEnabled(true));
    }

    /** Target HUD, Combat Timer, Hit Effects, Friends, Armor Break Alerts, cosmetics, visuals, Auction Helper, tools. */
    private static void newFeaturesDemo(MinecraftClient mc, int T, String me) {
        int x = ox + 40, y = oy, z = oz;
        mc.getToastManager().clear();
        var server = mc.getServer();
        if (T == 1914) {
            dev.stella.client.modules.misc.FreeLook.forceHeld = false;
            mc.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);
            for (String c : new String[]{"gamemode creative " + me, "time set noon",
                    "fill " + (x - 8) + " " + (y - 1) + " " + (z - 8) + " " + (x + 8) + " " + (y - 1) + " " + (z + 12) + " minecraft:smooth_stone",
                    "fill " + (x - 8) + " " + y + " " + (z - 8) + " " + (x + 8) + " " + (y + 8) + " " + (z + 12) + " minecraft:air",
                    "tp " + me + " " + (x + 0.5) + " " + y + " " + (z + 0.5) + " 0 8",
                    "item replace entity " + me + " hotbar.0 with minecraft:netherite_sword",
                    "scoreboard objectives add stella dummy \"DonutSMP\"", "scoreboard objectives setdisplay sidebar stella",
                    "scoreboard players set Money:$1.25M stella 3", "scoreboard players set Shards:4,210 stella 2", "scoreboard players set Kills:42 stella 1"})
                serverCmd(mc, c);
            mc.player.getInventory().setSelectedSlot(0);
            if (!dev.stella.client.data.Friends.isFriend("Buddy")) dev.stella.client.data.Friends.toggle("Buddy");
            if (server != null) server.execute(() -> {
                var world = server.getOverworld();
                var pm = server.getPlayerManager();
                Object[][] spec = {{"Enemy", ENEMY_UUID, x + 0.5, z + 4.5}, {"Buddy", BUDDY_UUID, x + 3.5, z + 6.5}};
                for (Object[] o : spec) {
                    var fp = net.fabricmc.fabric.api.entity.FakePlayer.get(world, new com.mojang.authlib.GameProfile((java.util.UUID) o[1], (String) o[0]));
                    fp.refreshPositionAndAngles((double) o[2], y, (double) o[3], 180f, 0f);
                    if (o[0].equals("Enemy")) {
                        var chest = new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND_CHESTPLATE);
                        chest.setDamage((int) (chest.getMaxDamage() * 0.92));
                        fp.equipStack(net.minecraft.entity.EquipmentSlot.CHEST, chest);
                        fp.equipStack(net.minecraft.entity.EquipmentSlot.HEAD, new net.minecraft.item.ItemStack(net.minecraft.item.Items.NETHERITE_HELMET));
                        fp.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND_SWORD));
                        fp.equipStack(net.minecraft.entity.EquipmentSlot.OFFHAND, new net.minecraft.item.ItemStack(net.minecraft.item.Items.TOTEM_OF_UNDYING));
                    }
                    pm.sendToAll(net.minecraft.network.packet.s2c.play.PlayerListS2CPacket.entryFromPlayer(java.util.List.of(fp)));
                    world.onPlayerConnected(fp);
                    scenePlayers.add(fp);
                }
            });
            on("Target HUD", "Combat Timer HUD", "Hit Effects", "Friends", "Armor Break Alerts", "Player ESP", "Radar HUD", "Notifications HUD", "Totem Pop Counter");
            setting("Radar HUD", "Range", 16.0);
            caption("New \u2022 Target HUD + Combat Timer", "Health, armor, pops and who's winning - friends show green");
        }
        if (T == 1920 || T == 1924) {
            var enemy = clientPlayer(mc, "Enemy");
            if (enemy != null) { mc.interactionManager.attackEntity(mc.player, enemy); mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND); }
            StellaClient.LOGGER.info("[smoke] new: attacked enemy = {}", enemy != null);
        }
        if (T == 1930) shot(mc, "new-target-hud");
        // cosmetics
        if (T == 1932) {
            mc.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK);
            setting("Cape", "Style", "Galaxy");
            setting("Accessories", "Horns", false);
            on("Cape", "Accessories", "Trails");
            var tr = ModuleManager.get(dev.stella.client.modules.cosmetics.Trails.class);
            for (int i = 30; i >= 0; i--) tr.push(new net.minecraft.util.math.Vec3d(x + 0.5 + Math.sin(i * 0.25) * 2.5, y + 0.9, z - 5 + i * 0.18));
            caption("New \u2022 Cape + Halo + Star Pet + Trails", "Galaxy cape, glowing halo, a star pet and a rainbow trail");
        }
        if (T == 1936) { mc.player.setYaw(0f); mc.player.setPitch(12f); mc.player.bodyYaw = 0f; mc.player.lastBodyYaw = 0f; }
        if (T == 1940) shot(mc, "new-cape-halo-trail");
        if (T == 1942) {
            var enemy = clientPlayer(mc, "Enemy");
            setting("Kill Effects", "Effect", "Stella Burst");
            on("Kill Effects");
            if (enemy != null) ModuleManager.get(dev.stella.client.modules.cosmetics.KillEffects.class).play(enemy.getEntityPos());
            caption("New \u2022 Kill Effects", "A Stella star burst where a player dies (also Lightning or Confetti)");
        }
        if (T == 1945) shot(mc, "new-kill-effect");
        // visuals + HUDs
        if (T == 1948) {
            for (String n : new String[]{"Cape", "Accessories", "Trails", "Target HUD", "Radar HUD"})
                ModuleManager.all().stream().filter(m -> m.getName().equals(n)).forEach(m -> m.setEnabled(false));
            mc.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);
            serverCmd(mc, "gamemode survival " + me);
            serverCmd(mc, "effect give " + me + " minecraft:resistance 120 4 true");
            serverCmd(mc, "item replace entity " + me + " hotbar.1 with minecraft:ender_pearl 16");
            serverCmd(mc, "setblock " + x + " " + y + " " + (z + 2) + " minecraft:amethyst_block");
            mc.player.getInventory().setSelectedSlot(1);
            setting("Scoreboard", "Scale", 0.8);
            on("Block Outline", "Trajectories", "Saturation Overlay", "Scoreboard", "Clock HUD", "CPS Graph HUD", "Money Tracker HUD");
            var mt = ModuleManager.get(dev.stella.client.modules.hud.MoneyTrackerHud.class);
            long now = System.currentTimeMillis();
            for (int i = 0; i < 24; i++) mt.addSample(now - (24 - i) * 75_000L, 1_000_000 + i * 11_000 + (i % 3) * 4000, 4000 + i * 9);
            caption("New \u2022 Visuals + HUDs", "Block outline, pearl trajectory, saturation, clock, CPS graph, money tracker");
        }
        if (T == 1952) { mc.player.setYaw(0f); mc.player.setPitch(28f); }
        if (T == 1958) {
            StellaClient.LOGGER.info("[smoke] new: money tracker sidebar lines = {}", dev.stella.client.util.ScoreboardUtil.lines());
            shot(mc, "new-visuals-huds");
        }
        // Auction Helper: a chest named like the auction menu with priced listings
        if (T == 1960) {
            int cx = x, cy = y, cz = z + 2;
            serverCmd(mc, "gamemode creative " + me);
            serverCmd(mc, "setblock " + cx + " " + cy + " " + cz + " minecraft:chest{CustomName:\"Auction House\"}");
            String[] items = {"minecraft:diamond[lore=[\"Price: $900\"]] 16", "minecraft:diamond[lore=[\"Price: $640\"]] 8",
                    "minecraft:spawner[lore=[\"Price: $1.5m\"]] 1", "minecraft:elytra[lore=[\"Price: $650k\"]] 1",
                    "minecraft:totem_of_undying[lore=[\"Price: $9k\"]] 1", "minecraft:netherite_ingot[lore=[\"Price: $12.5k\"]] 4",
                    "minecraft:golden_apple[lore=[\"Price: $2,400\"]] 12", "minecraft:totem_of_undying[lore=[\"Price: $7.2k\"]] 1"};
            for (int i = 0; i < items.length; i++) serverCmd(mc, "item replace block " + cx + " " + cy + " " + cz + " container." + (10 + i) + " with " + items[i]);
            on("Auction Helper");
            caption("New \u2022 Auction Helper", "Price per item, cheapest in green, your watchlist in gold");
        }
        if (T == 1964) {
            var pos = new net.minecraft.util.math.BlockPos(x, y, z + 2);
            mc.interactionManager.interactBlock(mc.player, net.minecraft.util.Hand.MAIN_HAND,
                    new net.minecraft.util.hit.BlockHitResult(net.minecraft.util.math.Vec3d.ofCenter(pos), net.minecraft.util.math.Direction.UP, pos, false));
        }
        if (T == 1968) StellaClient.LOGGER.info("[smoke] new: auction screen = {}", mc.currentScreen == null ? "none" : mc.currentScreen.getTitle().getString());
        if (T == 1971) shot(mc, "new-auction-helper");
        if (T == 1973) { if (mc.currentScreen != null) mc.currentScreen.close(); caption = null; }
        // tools: gallery, share code, base saver, spawner export, region number
        if (T == 1975) mc.setScreen(new dev.stella.client.gui.ScreenshotGallery());
        if (T == 1980) shot(mc, "new-screenshot-gallery");
        if (T == 1982) {
            if (mc.currentScreen != null) mc.currentScreen.close();
            try {
                String code = dev.stella.client.modules.client.ClientTools.shareCode();
                StellaClient.LOGGER.info("[smoke] new: share code length {} prefix {}", code.length(), code.substring(0, 8));
            } catch (Exception e) { fail("share code: " + e); }
            var bs = ModuleManager.get(dev.stella.client.modules.combat.BaseSaver.class);
            StellaClient.LOGGER.info("[smoke] new: base saver wrote {}", bs.saveNowForced());
            StellaClient.LOGGER.info("[smoke] new: region here = {}", dev.stella.client.modules.combat.RegionAlerts.number(
                    dev.stella.client.data.RegionConfig.colOf(mc.player.getX()), dev.stella.client.data.RegionConfig.rowOf(mc.player.getZ())));
        }
        if (T == 1990) {
            if (server != null) server.execute(() -> {
                var world = server.getOverworld();
                for (var fp : scenePlayers) world.removePlayer(fp, net.minecraft.entity.Entity.RemovalReason.DISCARDED);
                server.getPlayerManager().sendToAll(new net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket(java.util.List.of(ENEMY_UUID, BUDDY_UUID)));
                scenePlayers.clear();
            });
            serverCmd(mc, "scoreboard objectives remove stella");
        }
    }

    private static void freeLookDemo(MinecraftClient mc, int T, String me) {
        int x = ox, y = oy, z = oz + 20;
        var fl = ModuleManager.get(dev.stella.client.modules.misc.FreeLook.class);
        mc.getToastManager().clear();
        if (T == 1832) {
            serverCmd(mc, "gamemode creative " + me);
            serverCmd(mc, "fill " + (x - 3) + " " + (y - 1) + " " + (z - 3) + " " + (x + 3) + " " + (y + 3) + " " + (z + 3) + " minecraft:stone_bricks hollow");
            serverCmd(mc, "setblock " + x + " " + (y + 2) + " " + (z + 2) + " minecraft:lantern[hanging=false]");
            serverCmd(mc, "tp " + me + " " + (x + 0.5) + " " + y + " " + (z + 0.5) + " 0 0");
            mc.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);
            fl.setEnabled(true);
            setting("Free Look", "Mode", "Third Person");
            setting("Free Look", "Distance", 7.0);
            setting("Free Look", "Go Through Blocks", false);
            caption("Free Look \u2022 Go Through Blocks OFF", "Inside a closed room the camera gets pushed in by the walls");
        }
        if (T == 1838) dev.stella.client.modules.misc.FreeLook.forceHeld = true;
        if (T >= 1840 && T < 1872) fl.setAngles((T - 1840) * 11.25f, 25f);
        if (T == 1856) shot(mc, "freelook-clip-off");
        if (T == 1872) {
            setting("Free Look", "Go Through Blocks", true);
            caption("Free Look \u2022 Go Through Blocks ON", "The camera orbits straight through the walls");
        }
        if (T >= 1874 && T < 1906) fl.setAngles((T - 1874) * 11.25f, 25f);
        if (T == 1890) shot(mc, "freelook-clip-on");
        if (T == 1906) { dev.stella.client.modules.misc.FreeLook.forceHeld = false; caption = null; }
    }

    private static void keys(MinecraftClient mc, boolean fwd, boolean jump, boolean sneak, boolean sprint) {
        mc.options.forwardKey.setPressed(fwd);
        mc.options.jumpKey.setPressed(jump);
        mc.options.sneakKey.setPressed(sneak);
        mc.options.sprintKey.setPressed(sprint);
    }

    private static void aco(String name, boolean on) {
        ModuleManager.all().stream().filter(m -> m.getName().equals(name)).forEach(m -> m.setEnabled(on));
    }

    private static int mineIndex;
    /** Read by MinecraftClientMixin: hold attack during the scripted mining demo (CI has no captured mouse). */
    public static volatile boolean forceAttack;

    /** Scripted demo of the Anti Cheat Off modules for the preview video. */
    private static void acoDemo(MinecraftClient mc, int T, String me) {
        int x = ox, y = oy, z = oz;
        mc.getToastManager().clear(); // no recipe / advancement popups in the video
        if (T % 10 == 0) mc.inGameHud.getChatHud().clear(false);
        if (T == 1516) {
            for (String c : new String[]{"gamerule announceAdvancements false", "gamemode survival " + me, "clear " + me, "effect give " + me + " minecraft:resistance 600 4 true",
                    "fill " + (x - 12) + " " + y + " " + (z - 30) + " " + (x + 12) + " " + (y + 40) + " " + (z + 12) + " minecraft:air",
                    "fill " + (x - 12) + " " + (y - 1) + " " + (z - 30) + " " + (x + 12) + " " + (y - 1) + " " + (z + 12) + " minecraft:smooth_quartz",
                    "tp " + me + " " + (x + 0.5) + " " + y + " " + (z + 6.5) + " 180 10"}) serverCmd(mc, c);
            mc.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK);
            setting("Flight", "Mode", "Velocity"); setting("Flight", "Horizontal Speed", 0.7); setting("Flight", "Vertical Speed", 0.45);
            caption("Anti Cheat Off \u2022 Flight", "Velocity mode - adjustable horizontal and vertical speed");
        }
        if (T == 1520) { mc.player.setYaw(180f); mc.player.setPitch(10f); aco("Flight", true); }
        if (T >= 1522 && T < 1534) keys(mc, false, true, false, false);      // rise
        if (T >= 1534 && T < 1556) keys(mc, true, false, false, false);      // fly forward
        if (T >= 1556 && T < 1562) keys(mc, false, false, true, false);      // descend
        if (T == 1562) { keys(mc, false, false, false, false); aco("Flight", false); }
        // AirJump: climb an invisible staircase
        if (T == 1566) {
            serverCmd(mc, "tp " + me + " " + (x + 0.5) + " " + y + " " + (z + 6.5) + " 180 5");
            setting("AirJump", "Jump Height", 0.5); setting("AirJump", "Max Air Jumps", 0.0);
            aco("AirJump", true);
            caption("Anti Cheat Off \u2022 AirJump", "Jump again in mid-air - adjustable jump height");
        }
        if (T >= 1570 && T < 1604) { mc.player.setYaw(180f); keys(mc, true, (T - 1570) % 4 < 2, false, false); }
        if (T == 1604) { keys(mc, false, false, false, false); aco("AirJump", false); }
        // ElytraFly: launch from height, then controlled flight
        if (T == 1610) {
            serverCmd(mc, "item replace entity " + me + " armor.chest with minecraft:elytra");
            serverCmd(mc, "tp " + me + " " + (x + 0.5) + " " + (y + 25) + " " + (z + 8.5) + " 180 5");
            setting("ElytraFly", "Mode", "Control"); setting("ElytraFly", "Speed", 1.2);
            aco("ElytraFly", true);
            caption("Anti Cheat Off \u2022 ElytraFly", "Fly where you look at a set speed, hover when idle");
        }
        if (T == 1616) keys(mc, false, true, false, false);                  // open the elytra
        if (T == 1618) keys(mc, false, false, false, false);
        if (T == 1620) keys(mc, false, true, false, false);
        if (T == 1622) keys(mc, false, false, false, false);
        if (T >= 1624 && T < 1650) { mc.player.setYaw(180f + (T - 1624) * 3f); mc.player.setPitch(5f); keys(mc, true, false, false, false); }
        if (T == 1626) StellaClient.LOGGER.info("[smoke] aco: gliding = {}", mc.player.isGliding());
        if (T == 1650) { keys(mc, false, false, false, false); aco("ElytraFly", false); serverCmd(mc, "item replace entity " + me + " armor.chest with minecraft:air"); }
        // BoatFly
        if (T == 1656) {
            serverCmd(mc, "tp " + me + " " + (x + 0.5) + " " + y + " " + (z + 6.5) + " 180 10");
            serverCmd(mc, "summon minecraft:oak_boat " + (x + 0.5) + " " + y + " " + (z + 6.5));
            setting("BoatFly", "Speed", 0.8); setting("BoatFly", "Vertical Speed", 0.35);
            caption("Anti Cheat Off \u2022 BoatFly", "Steer a boat through the air - speed, vertical speed, glide");
        }
        if (T == 1660) serverCmd(mc, "ride " + me + " mount @e[type=minecraft:oak_boat,limit=1,sort=nearest]");
        if (T == 1664) { aco("BoatFly", true); mc.player.setYaw(180f); StellaClient.LOGGER.info("[smoke] aco: in boat = {}", mc.player.getVehicle() != null); }
        if (T >= 1666 && T < 1678) keys(mc, false, true, false, false);
        if (T >= 1678 && T < 1700) { mc.player.setYaw(180f + (T - 1678) * 2f); keys(mc, true, false, false, false); }
        if (T == 1700) {
            keys(mc, false, false, false, false); aco("BoatFly", false);
            serverCmd(mc, "ride " + me + " dismount");
            serverCmd(mc, "kill @e[type=minecraft:oak_boat]");
        }
        // WaterSpeed: a pool
        if (T == 1704) {
            serverCmd(mc, "fill " + (x - 4) + " " + (y - 3) + " " + (z - 24) + " " + (x + 4) + " " + (y - 1) + " " + (z + 4) + " minecraft:water");
            serverCmd(mc, "tp " + me + " " + (x + 0.5) + " " + (y - 2) + " " + (z + 3.5) + " 180 15");
            setting("WaterSpeed", "Swim Speed", 0.5);
            caption("Anti Cheat Off \u2022 WaterSpeed", "Adjustable swimming speed");
        }
        if (T == 1708) { aco("WaterSpeed", true); mc.player.setYaw(180f); }
        if (T >= 1710 && T < 1736) keys(mc, true, false, false, false);
        if (T == 1736) { keys(mc, false, false, false, false); aco("WaterSpeed", false); serverCmd(mc, "fill " + (x - 4) + " " + (y - 3) + " " + (z - 24) + " " + (x + 4) + " " + (y - 1) + " " + (z + 4) + " minecraft:smooth_quartz"); }
        // Mining: a wall of stone, SpeedMine + FastMine, then InstantBreak
        if (T == 1740) {
            mc.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);
            serverCmd(mc, "tp " + me + " " + (x + 0.5) + " " + y + " " + (z + 0.5) + " 180 20");
            serverCmd(mc, "fill " + (x - 3) + " " + y + " " + (z - 3) + " " + (x + 3) + " " + (y + 2) + " " + (z - 3) + " minecraft:stone");
            serverCmd(mc, "item replace entity " + me + " hotbar.0 with minecraft:diamond_pickaxe");
            setting("SpeedMine", "Speed", 2.0); setting("FastMine", "Break Delay", 0.0);
            caption("Anti Cheat Off \u2022 SpeedMine + FastMine", "2x mining speed, no delay between blocks");
            mineIndex = 0;
        }
        if (T == 1744) { mc.player.getInventory().setSelectedSlot(0); aco("SpeedMine", true); aco("FastMine", true); }
        if (T == 1786) {
            aco("SpeedMine", false); aco("FastMine", false); aco("InstantBreak", true);
            serverCmd(mc, "gamemode creative " + me); // the server only accepts instant breaks where you could really do it
            serverCmd(mc, "fill " + (x - 3) + " " + y + " " + (z - 3) + " " + (x + 3) + " " + (y + 2) + " " + (z - 3) + " minecraft:stone");
            caption("Anti Cheat Off \u2022 InstantBreak", "Instant breaking where the server allows it (creative here)");
            mineIndex = 0;
        }
        if ((T >= 1746 && T < 1784) || (T >= 1790 && T < 1826)) mineWall(mc, x, y, z);
        if (T == 1784 || T == 1826) {
            forceAttack = false;
            mc.interactionManager.cancelBlockBreaking();
            StellaClient.LOGGER.info("[smoke] aco: wall blocks broken = {}", mineIndex);
        }
        if (T == 1785) {
            int broken = 0;
            for (int i = 0; i < 21; i++) if (mc.world.getBlockState(new net.minecraft.util.math.BlockPos(x - 3 + i % 7, y + 2 - i / 7, z - 3)).isAir()) broken++;
            StellaClient.LOGGER.info("[smoke] aco: survival SpeedMine broke {} of 21", broken);
        }
        if (T == 1828) { aco("InstantBreak", false); caption = null; serverCmd(mc, "gamemode creative " + me); }
    }

    /** Breaks the wall left to right, top to bottom, the way a player holding attack would. */
    private static void mineWall(MinecraftClient mc, int x, int y, int z) {
        for (int guard = 0; guard < 21; guard++) {
            int i = mineIndex;
            if (i >= 21) { forceAttack = false; return; }
            net.minecraft.util.math.BlockPos pos = new net.minecraft.util.math.BlockPos(x - 3 + i % 7, y + 2 - i / 7, z - 3);
            if (mc.world.getBlockState(pos).isAir()) { mineIndex++; continue; }
            var eye = mc.player.getEyePos();
            double dx = pos.getX() + 0.5 - eye.x, dy = pos.getY() + 0.5 - eye.y, dz = pos.getZ() + 0.5 - eye.z;
            mc.player.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
            mc.player.setPitch((float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));
            forceAttack = true; // vanilla does the actual mining of the block under the crosshair
            return;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setting(String module, String setting, Object value) {
        for (Module m : ModuleManager.all()) {
            if (!m.getName().equals(module)) continue;
            for (var st : m.getSettings()) if (st.getName().equals(setting)) ((dev.stella.client.setting.Setting) st).set(value);
        }
    }

    private static void press(Screen screen, String translationKey) {
        Text wanted = Text.translatable(translationKey);
        for (Element e : screen.children()) {
            if (e instanceof ButtonWidget b && b.getMessage().getString().equals(wanted.getString())) { b.onPress(new net.minecraft.client.input.KeyInput(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0)); return; }
        }
        throw new IllegalStateException("button not found: " + translationKey);
    }

    private static void fail(String why) {
        StellaClient.LOGGER.error("STELLA_SMOKE_FAIL {}", why);
        System.out.println("STELLA_SMOKE_FAIL " + why);
        stage = 99;
        MinecraftClient.getInstance().scheduleStop();
    }
}
