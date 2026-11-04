package dev.stella.client;

import dev.stella.client.config.ConfigManager;
import dev.stella.client.data.RegionConfig;
import dev.stella.client.data.StaffDatabase;
import dev.stella.client.hud.HudManager;
import dev.stella.client.module.ModuleManager;
import dev.stella.client.modules.visuals.CustomSky;
import dev.stella.client.render.Projection;
import dev.stella.client.render.Render3D;
import dev.stella.client.scan.BlockScanner;
import dev.stella.client.util.track.TpsTracker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class StellaClient implements ClientModInitializer {
    public static final String MOD_ID = "stella";
    public static final Logger LOGGER = LoggerFactory.getLogger("Stella Client");
    public static String VERSION = "1.0.0";
    private static boolean ready;

    @Override
    public void onInitializeClient() {
        VERSION = FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse(VERSION);
        LOGGER.info("Stella Client {} initialising", VERSION);

        ModuleManager.init();
        BlockScanner.init();

        // Config needs GameOptions and the window, so load once the client has started.
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            StaffDatabase.load();
            dev.stella.client.data.MediaDatabase.load();
            RegionConfig.load();
            ConfigManager.init();
            ready = true;
            LOGGER.info("Stella Client ready - profile '{}'", ConfigManager.activeProfile());
            ModuleManager.get(dev.stella.client.modules.settings.AutoUpdaterModule.class).startup();
            if (Boolean.getBoolean("stella.smokeTest")) StellaSmokeTest.start();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            if (ready) ConfigManager.save();
            dev.stella.client.update.AutoUpdater.installOnShutdown();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!ready) return;
            ModuleManager.tick();
            BlockScanner.tick();
            ConfigManager.tick();
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> TpsTracker.reset());

        WorldRenderEvents.END_EXTRACTION.register(ctx -> {
            if (!ready) return;
            frameDelta = ctx.tickCounter().getTickProgress(true);
            Projection.capture(new org.joml.Matrix4f(ctx.viewMatrix()), new org.joml.Matrix4f(ctx.cullProjectionMatrix()),
                    dev.stella.client.util.CameraPos.of(ctx.camera()));
        });

        WorldRenderEvents.BEFORE_ENTITIES.register(ctx -> {
            if (!ready) return;
            CustomSky sky = ModuleManager.get(CustomSky.class);
            if (sky.active()) {
                try { sky.render(ctx.matrices(), immediate(ctx.consumers()), frameDelta); }
                catch (Throwable t) { reportError("Custom Sky", t); }
            }
        });

        WorldRenderEvents.END_MAIN.register(ctx -> {
            if (!ready) return;
            MatrixStack matrices = ctx.matrices() != null ? ctx.matrices() : new MatrixStack();
            Render3D r = new Render3D(matrices, immediate(ctx.consumers()), ctx.worldState().cameraRenderState.pos, frameDelta);
            ModuleManager.render3D(r);
            var mb = ModuleManager.get(dev.stella.client.modules.visuals.MotionBlur.class);
            if (mb.isEnabled()) {
                try { mb.apply(immediate(ctx.consumers())); } catch (Throwable t) { reportError("Motion Blur", t); }
            }
        });

        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            if (!ready) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            float td = tickCounter.getTickProgress(true);
            if (mc.world != null && !mc.options.hudHidden) ModuleManager.renderOverlay(drawContext, td);
            HudManager.render(drawContext, td);
        });
    }

    private static float frameDelta = 1f;

    private static net.minecraft.client.render.VertexConsumerProvider.Immediate immediate(net.minecraft.client.render.VertexConsumerProvider p) {
        if (p instanceof net.minecraft.client.render.VertexConsumerProvider.Immediate im) return im;
        return MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
    }

    public static boolean isReady() { return ready; }

    private static final java.util.Map<String, Integer> ERRORS = new java.util.LinkedHashMap<>();

    /** Logs a module failure once per location (subsequent repeats are counted only). */
    public static void reportError(String where, Throwable t) {
        int n = ERRORS.merge(where, 1, Integer::sum);
        if (n == 1) LOGGER.warn("Error in {}", where, t);
    }

    public static int errorCount() { return ERRORS.size(); }
    public static String errorSummary() { return String.join(", ", ERRORS.keySet()); }
}
