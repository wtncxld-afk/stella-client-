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
    private static int stage;
    private static int stageStart;

    private StellaSmokeTest() {}

    static void start() {
        StellaClient.LOGGER.info("[smoke] starting");
        ClientTickEvents.END_CLIENT_TICK.register(StellaSmokeTest::onTick);
    }

    private static void next() { stage++; stageStart = tick; StellaClient.LOGGER.info("[smoke] stage {}", stage); }
    private static int inStage() { return tick - stageStart; }

    private static void shot(MinecraftClient mc, String name) {
        ScreenshotRecorder.saveScreenshot(mc.runDirectory, "stella-" + name + ".png", mc.getFramebuffer(),
                msg -> StellaClient.LOGGER.info("[smoke] screenshot {}: {}", name, msg.getString()));
    }

    private static void onTick(MinecraftClient mc) {
        tick++;
        try {
            switch (stage) {
                case 0 -> {
                    if (tick < 40) return;
                    MixinEnvironment.getCurrentEnvironment().audit();
                    StellaClient.LOGGER.info("[smoke] mixin audit passed");
                    mc.setScreen(new ClickGuiScreen());
                    next();
                }
                case 1 -> {
                    if (inStage() == 30 && mc.currentScreen instanceof ClickGuiScreen gui) gui.openSettingsFor("Custom Crosshair");
                    if (inStage() == 50) shot(mc, "clickgui");
                    if (inStage() == 60) { mc.setScreen(new HudEditorScreen(null)); next(); }
                }
                case 2 -> {
                    if (inStage() == 30) shot(mc, "hudeditor");
                    if (inStage() == 40) {
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
                    if (mc.world != null && mc.player != null && mc.currentScreen == null) {
                        for (Module m : ModuleManager.all()) {
                            if (m instanceof AutoRelog || m instanceof HudEditorModule || m instanceof Zoom) continue;
                            m.setEnabled(true);
                        }
                        var h = mc.player.networkHandler;
                        h.sendChatCommand("time set 18000");
                        h.sendChatCommand("gamerule doDaylightCycle false");
                        h.sendChatCommand("fill ~3 ~-1 ~-4 ~12 ~-1 ~4 minecraft:deepslate[axis=x]");
                        h.sendChatCommand("setblock ~5 ~ ~-2 minecraft:chest");
                        h.sendChatCommand("setblock ~5 ~ ~ minecraft:barrel");
                        h.sendChatCommand("setblock ~5 ~ ~2 minecraft:shulker_box");
                        h.sendChatCommand("setblock ~8 ~ ~-2 minecraft:spawner");
                        h.sendChatCommand("setblock ~8 ~ ~1 minecraft:amethyst_block");
                        h.sendChatCommand("setblock ~8 ~1 ~1 minecraft:amethyst_cluster");
                        h.sendChatCommand("summon minecraft:item ~4 ~ ~1 {Item:{id:\"minecraft:diamond_sword\",count:1}}");
                        mc.player.setYaw(-90f);
                        mc.player.setPitch(18f);
                        next();
                    } else if (inStage() > 6000) {
                        fail("world did not load");
                    }
                }
                case 5 -> {
                    if (inStage() == 5) { mc.player.setYaw(-90f); mc.player.setPitch(18f); }
                    if (inStage() == 100) shot(mc, "world-cosmic-night");
                    if (inStage() == 110) { mc.player.setPitch(-35f); }
                    if (inStage() == 130) shot(mc, "sky-cosmic");
                    if (inStage() == 135) ModuleManager.all().stream().filter(m -> m.getName().equals("Custom Sky")).findFirst()
                            .ifPresent(m -> m.getSettings().stream().filter(s -> s.getName().equals("Preset")).findFirst()
                                    .ifPresent(s -> ((dev.stella.client.setting.ModeSetting) s).set("Arctic Aurora")));
                    if (inStage() == 160) shot(mc, "sky-arctic-aurora");
                    if (inStage() == 165) { mc.player.setPitch(18f); mc.setScreen(new ClickGuiScreen()); }
                    if (inStage() == 185 && mc.currentScreen instanceof ClickGuiScreen gui) { gui.openSettingsFor("Player ESP"); gui.openSettingsFor("Custom Sky"); }
                    if (inStage() == 210) shot(mc, "world-gui");
                    if (inStage() == 215) mc.setScreen(new HudEditorScreen(null));
                    if (inStage() == 235) shot(mc, "world-hudeditor");
                    if (inStage() == 240) {
                        mc.setScreen(null);
                        for (Module m : ModuleManager.all()) if (m.isEnabled()) m.setEnabled(false);
                        for (Module m : ModuleManager.all()) if (m.isDefaultEnabled()) m.setEnabled(true);
                    }
                    if (inStage() == 290) {
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

    private static void press(Screen screen, String translationKey) {
        Text wanted = Text.translatable(translationKey);
        for (Element e : screen.children()) {
            if (e instanceof ButtonWidget b && b.getMessage().getString().equals(wanted.getString())) { b.onPress(); return; }
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
