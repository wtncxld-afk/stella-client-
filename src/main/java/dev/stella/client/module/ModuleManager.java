package dev.stella.client.module;

import dev.stella.client.modules.client.ClickGuiModule;
import dev.stella.client.modules.client.HudEditorModule;
import dev.stella.client.modules.combat.PlayerDetector;
import dev.stella.client.modules.combat.PlayerESP;
import dev.stella.client.modules.combat.AntiVanish;
import dev.stella.client.modules.combat.BlockESP;
import dev.stella.client.modules.combat.StaffList;
import dev.stella.client.modules.combat.StorageESP;
import dev.stella.client.modules.combat.SusChunkFinder;
import dev.stella.client.modules.hud.*;
import dev.stella.client.modules.misc.AutoRelog;
import dev.stella.client.modules.combat.StaffList;
import dev.stella.client.modules.combat.AntiVanish;
import dev.stella.client.modules.combat.BlockESP;
import dev.stella.client.modules.combat.StorageESP;
import dev.stella.client.modules.combat.SusChunkFinder;
import dev.stella.client.modules.settings.AnimationsModule;
import dev.stella.client.modules.settings.ConfigModule;
import dev.stella.client.modules.visuals.*;
import dev.stella.client.modules.world.AmethystDetector;
import dev.stella.client.modules.world.BlockNotifier;
import dev.stella.client.render.Render3D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.InputUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ModuleManager {
    private static final List<Module> MODULES = new ArrayList<>();
    private static final Map<Class<?>, Module> BY_CLASS = new HashMap<>();
    private static final Map<Module, Boolean> KEY_STATE = new HashMap<>();

    private ModuleManager() {}

    public static void init() {
        // DonutSMP
        register(new PlayerESP());
        register(new PlayerDetector());
        register(new StorageESP());
        register(new BlockESP());
        register(new SusChunkFinder());
        register(new dev.stella.client.modules.combat.SpawnerFinder());
        // Combat
        register(new dev.stella.client.modules.pvp.AutoTotem());
        register(new dev.stella.client.modules.pvp.AutoInvTotem());
        register(new dev.stella.client.modules.pvp.HoverTotem());
        register(new dev.stella.client.modules.pvp.AutoCrystal());
        register(new dev.stella.client.modules.pvp.AutoAnchor());
        register(new dev.stella.client.modules.pvp.DoubleAnchor());
        register(new dev.stella.client.modules.pvp.TotemPopCounter());
        register(new dev.stella.client.modules.misc.ToggleSprint());
        register(new dev.stella.client.modules.misc.DeathCoordinates());
        register(new dev.stella.client.modules.misc.DurabilityWarning());
        register(new dev.stella.client.modules.misc.AutoTool());
        register(new dev.stella.client.modules.world.LightLevelOverlay());
        register(new dev.stella.client.modules.hud.ArmorHud());
        register(new dev.stella.client.modules.hud.RadarHud());
        register(new dev.stella.client.modules.cosmetics.Wings());
        register(new dev.stella.client.modules.cosmetics.ChineseHat());
        register(new dev.stella.client.modules.cosmetics.JumpCircles());
        register(new dev.stella.client.modules.cosmetics.KnifeSkins());
        register(new dev.stella.client.modules.cosmetics.CustomGlint());
        register(new dev.stella.client.modules.misc.FriendsModule());
        register(new dev.stella.client.modules.misc.Macros());
        register(new dev.stella.client.modules.hud.TargetHud());
        register(new dev.stella.client.modules.hud.CombatTimerHud());
        register(new dev.stella.client.modules.pvp.HitEffects());
        register(new dev.stella.client.modules.pvp.ArmorBreakAlerts());
        register(new dev.stella.client.modules.combat.AuctionHelper());
        register(new dev.stella.client.modules.combat.RegionAlerts());
        register(new dev.stella.client.modules.combat.BaseSaver());
        register(new dev.stella.client.modules.hud.MoneyTrackerHud());
        register(new dev.stella.client.modules.anticheatoff.TestEnvironment());
        register(new dev.stella.client.modules.anticheatoff.Flight());
        register(new dev.stella.client.modules.anticheatoff.BoatFly());
        register(new dev.stella.client.modules.anticheatoff.AirJump());
        register(new dev.stella.client.modules.anticheatoff.WaterSpeed());
        register(new dev.stella.client.modules.anticheatoff.ElytraFly());
        register(new dev.stella.client.modules.anticheatoff.SpeedMine());
        register(new dev.stella.client.modules.anticheatoff.InstantBreak());
        register(new dev.stella.client.modules.anticheatoff.FastMine());
        register(new dev.stella.client.modules.hud.PotionHud());
        register(new dev.stella.client.modules.hud.ItemCounterHud());
        register(new StaffList());
        // Anti Vanish removed until further notice (source kept in modules/combat/AntiVanish.java)
        // register(new AntiVanish());
        register(new dev.stella.client.modules.combat.MediaBaseFinder());
        // Misc
        register(new AutoRelog());
        register(new dev.stella.client.modules.misc.Freecam());
        register(new dev.stella.client.modules.misc.FreeLook());
        // Render
        // World
        register(new BlockNotifier());
        register(new AmethystDetector());
        register(new dev.stella.client.modules.world.WeatherNotifier());
        // Visuals
        register(new CustomFov());
        register(new CustomCrosshair());
        register(new CustomSky());
        register(new Fullbright());
        register(new Ambience());
        register(new MotionBlur());
        register(new FogCustomizer());
        register(new WeatherCustomizer());
        register(new HurtCamera());
        register(new NoPumpkinOverlay());
        register(new Zoom());
        register(new WorldTime());
        register(new ItemPhysics());
        register(new EnchantmentGlint());
        register(new DamageTint());
        register(new GlowOutline());
        register(new SwingSpeed());
        register(new ItemScale());
        // Client
        register(new ClickGuiModule());
        register(new HudEditorModule());
        register(new dev.stella.client.modules.client.MenusModule());
        // HUD — every element is its own module
        register(new BrandingHud());
        register(new KeystrokesHud());
        register(new FpsHud());
        register(new TpsHud());
        register(new CoordinatesHud());
        register(new PingHud());
        register(new RegionMapHud());
        register(new NotificationsHud());
        register(new StaffListHud());
        register(new BlockNotifierHud());
        register(new MusicHud());
        // Settings
        register(new ConfigModule());
        register(new AnimationsModule());
        register(new dev.stella.client.modules.settings.AutoUpdaterModule());

        for (Module m : MODULES) m.finishSetup();
    }

    private static void register(Module m) {
        MODULES.add(m);
        BY_CLASS.put(m.getClass(), m);
    }

    public static List<Module> all() { return Collections.unmodifiableList(MODULES); }

    public static List<Module> byCategory(Category c) {
        List<Module> out = new ArrayList<>();
        for (Module m : MODULES) if (m.getCategory() == c) out.add(m);
        return out;
    }

    @SuppressWarnings("unchecked")
    public static <T extends Module> T get(Class<T> type) { return (T) BY_CLASS.get(type); }

    public static void afterConfigLoad() {
        ClickGuiModule gui = get(ClickGuiModule.class);
        if (gui != null) gui.applyTheme();
    }

    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        handleKeybinds(mc);
        for (Module m : MODULES) {
            if (!m.isEnabled()) continue;
            try { m.onTick(); } catch (Throwable t) { dev.stella.client.StellaClient.reportError("tick " + m.getName(), t); }
        }
    }

    private static void handleKeybinds(MinecraftClient mc) {
        long handle = mc.getWindow().getHandle();
        boolean allowed = mc.currentScreen == null;
        for (Module m : MODULES) {
            if (!m.keybind.isBound()) continue;
            boolean down = dev.stella.client.util.Keys.down(m.keybind.key());
            boolean was = KEY_STATE.getOrDefault(m, false);
            KEY_STATE.put(m, down);
            if (down && !was && allowed) {
                if (m instanceof ClickGuiModule g) g.open();
                else if (m instanceof HudEditorModule h) h.setEnabled(true);
                else if (m.keybindToggles()) m.toggle();
            }
        }
    }

    public static void render3D(Render3D r) {
        for (Module m : MODULES) {
            if (!m.isEnabled()) continue;
            try { m.onRender3D(r); } catch (Throwable t) { dev.stella.client.StellaClient.reportError("render3D " + m.getName(), t); }
        }
        r.flush();
    }

    public static void renderOverlay(DrawContext ctx, float tickDelta) {
        for (Module m : MODULES) {
            if (!m.isEnabled()) continue;
            try { m.onRenderOverlay(ctx, tickDelta); } catch (Throwable t) { dev.stella.client.StellaClient.reportError("overlay " + m.getName(), t); }
        }
    }
}
