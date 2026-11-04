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
final class StellaSmokeTest {
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

    private static final String[] TOUR = {"Storage ESP", "Block ESP", "Sus Chunk Finder", "Spawner Finder", "Player ESP", "Player Detector",
            "Staff List", "Anti Vanish", "Media Base Finder", "Auto Tool", "Region Map HUD", "Staff List HUD", "Notifications HUD"};

    /** Turns on exactly these tour modules (the rest of TOUR off). */
    private static void only(String... on) {
        java.util.Set<String> want = java.util.Set.of(on);
        for (Module m : ModuleManager.all())
            for (String n : TOUR) if (m.getName().equals(n)) m.setEnabled(want.contains(n) || n.equals("Notifications HUD"));
    }

    private static float camYaw, camPitch;

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
                        av.setEnabled(false);
                        av.setEnabled(true);
                        av.log().clear();
                        ModuleManager.get(dev.stella.client.modules.combat.MediaBaseFinder.class).setEnabled(true);
                        mc.inGameHud.getChatHud().clear(false);
                        // Case 1: a verified staff member switches to spectator mode near us (real server-side player)
                        staffTest(mc, 1);
                    }
                    if (inStage() == 366) logAntiVanish("case 1: staff in spectator mode");
                    if (inStage() == 368) shot(mc, "anti-vanish");
                    if (inStage() == 370) staffTest(mc, 2); // Case 2: vanish plugin hides them from tab but still sends their body
                    if (inStage() == 380) staffTest(mc, 4);
                    if (inStage() == 400) logAntiVanish("case 2: hidden from tab list, entity still sent");
                    if (inStage() == 401) staffTest(mc, 3); // clean up
                    if (inStage() == 402) staffTest(mc, 5); // Case 3: unlisted player does /gmsp
                    if (inStage() == 409) { logAntiVanish("case 3: player not in staff.json does /gmsp"); staffTest(mc, 6); }
                    if (inStage() == 372) mc.setScreen(new ClickGuiScreen());
                    if (inStage() == 385 && mc.currentScreen instanceof ClickGuiScreen gui) gui.openSettingsFor("Anti Vanish");
                    if (inStage() == 405) shot(mc, "anti-vanish-gui");
                    if (inStage() == 410) {
                        mc.setScreen(null);
                        ModuleManager.get(dev.stella.client.modules.visuals.GlowOutline.class).setEnabled(true);
                        ModuleManager.get(dev.stella.client.modules.visuals.ItemScale.class).setEnabled(true);
                        ModuleManager.get(dev.stella.client.modules.visuals.SwingSpeed.class).setEnabled(true);
                        ModuleManager.get(dev.stella.client.modules.combat.AntiVanish.class).setEnabled(false);
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
                        boolean waypoint = dev.stella.client.data.Waypoints.all().stream().anyMatch(w -> w.name().equals("Death"));
                        StellaClient.LOGGER.info("[smoke] death coordinates: dead={} death waypoint={}", dead, waypoint);
                        if (!waypoint) fail("Death Coordinates did not record the death");
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
                        for (String n : new String[]{"Death Coordinates", "Light Level Overlay"})
                            ModuleManager.all().stream().filter(m -> m.getName().equals(n)).forEach(m -> m.setEnabled(false));
                        caption("Storage ESP + Block ESP", "Stash + kelp farm 30 blocks underground - every container and redstone part, with tracers");
                        only("Storage ESP", "Block ESP");
                    }
                    if (T > 1040 && T < 1070) mc.player.setYaw(-135f + (T - 1040) * 2.5f);
                    if (T == 1070) shot(mc, "tour-1-storage-block-esp");
                    if (T == 1074) {
                        caption("Sus Chunk Finder", "Flags chunks that hide a base underground - you just get a chat message");
                        only("Sus Chunk Finder");
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
                        aerial(mc, bx + 26.5, oy, bz + 8.5, oy + 22, 28f);
                    }
                    if (T == 1116) { mc.player.setYaw(camYaw); mc.player.setPitch(camPitch); }
                    if (T == 1133 && holdForTerrain(mc, 900)) return;
                    if (T == 1135) StellaClient.LOGGER.info("[smoke] tour: spawner finder sees {}", ModuleManager.get(dev.stella.client.modules.combat.SpawnerFinder.class).spawners().stream()
                            .map(x -> x.type() + "@" + x.pos().toShortString()).collect(java.util.stream.Collectors.joining(", ")));
                    if (T == 1140) shot(mc, "tour-3-spawner-finder");
                    if (T == 1144) {
                        caption("Player Detector + Player ESP", "Blue chunk + list for players the client knows about but you can't see");
                        only("Player Detector", "Player ESP");
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
                    if (T == 1176) {
                        tourPlayers(mc, false, 0, 0, 0, 0, 0, 0);
                        caption("Staff List + Anti Vanish", "A staff member joins and goes /gmsp - Anti Vanish catches it");
                        only("Staff List", "Anti Vanish", "Staff List HUD");
                        aerial(mc, bx + 6.5, oy, bz + 5.5, oy + 10, 12f);
                    }
                    if (T == 1180) { mc.player.setYaw(camYaw); mc.player.setPitch(camPitch); }
                    if (T == 1182) staffTest(mc, 1);
                    if (T == 1196) logAntiVanish("tour");
                    if (T == 1200) shot(mc, "tour-5-staff-anti-vanish");
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
                    if (T == 1230) shot(mc, "tour-6-media-region");
                    if (T == 1234) {
                        mediaPlayer(mc, false);
                        caption("Auto Tool", "Switches to the best tool for the block you mine, then back");
                        only("Auto Tool");
                        serverCmd(mc, "gamemode survival " + me);
                        serverCmd(mc, "clear " + me);
                        serverCmd(mc, "item replace entity " + me + " hotbar.0 with minecraft:diamond_sword");
                        serverCmd(mc, "item replace entity " + me + " hotbar.2 with minecraft:netherite_pickaxe");
                        serverCmd(mc, "item replace entity " + me + " hotbar.4 with minecraft:netherite_axe");
                        serverCmd(mc, "item replace entity " + me + " hotbar.6 with minecraft:netherite_shovel");
                        serverCmd(mc, "setblock " + (bx + 1) + " " + (by + 1) + " " + (bz + 3) + " minecraft:oak_log");
                        serverCmd(mc, "setblock " + (bx + 1) + " " + (by + 1) + " " + (bz + 7) + " minecraft:dirt");
                        serverCmd(mc, "tp " + me + " " + (bx + 3.5) + " " + by + " " + (bz + 5.5) + " 90 10");
                    }
                    if (T == 1240) { mc.player.setYaw(90f); mc.player.setPitch(10f); mc.player.getInventory().setSelectedSlot(0); }
                    if (T == 1243 && holdForTerrain(mc, 600)) return;
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
                        if (k == 10) shot(mc, "tour-7-auto-tool-" + seg);
                    }
                    if (T == 1302) {
                        serverCmd(mc, "gamemode spectator " + me);
                        caption("Everything together", "ESP, tracers, spawner beams and Sus Chunk Finder over the stash");
                        only("Storage ESP", "Block ESP", "Spawner Finder", "Sus Chunk Finder");
                        aerial(mc, bx + 16.5, oy, bz + 8.5, oy + 30, 40f);
                    }
                    if (T == 1308) { mc.player.setYaw(camYaw); mc.player.setPitch(camPitch); }
                    if (T == 1321 && holdForTerrain(mc, 600)) return;
                    if (T == 1330) shot(mc, "tour-8-everything");
                    if (T == 1340) caption = null;
                    if (inStage() == 1350) {
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
