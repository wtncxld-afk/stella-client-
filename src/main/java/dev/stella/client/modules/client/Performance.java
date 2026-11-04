package dev.stella.client.modules.client;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.scan.BlockScanner;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.NumberSetting;
import dev.stella.client.util.OptionOverrides;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.particle.ParticlesMode;

/**
 * Performance tweaks that never touch your options.txt: lower FPS when the game is in the background,
 * an FPS boost preset (fewer particles, no clouds, no entity shadows, shorter entity distance) and the
 * per-tick budget for Stella's block scanner (ESP / Spawner Finder / Sus Chunk Finder).
 */
public class Performance extends Module {
    private final BoolSetting background = add(new BoolSetting("Background FPS Limit", "Limit FPS while Minecraft isn't the focused window", true));
    private final NumberSetting backgroundFps = add(new NumberSetting("Background FPS", "", 30, 5, 120, 5)).visibleWhen(background::isOn);
    private final BoolSetting boost = add(new BoolSetting("FPS Boost Preset", "Fewer particles, no clouds, no entity shadows, 75% entity distance", false)).onChange(this::apply);
    private final NumberSetting scanBudget = add(new NumberSetting("Scanner Budget", "Max milliseconds per tick for ESP / Spawner / Sus Chunk scanning (lower = smoother, slower to find)", 3.0, 0.5, 8.0, 0.5).suffix("ms"))
            .onChange(() -> BlockScanner.setBudgetMillis(this.scanBudgetValue()));

    public Performance() { super("Performance", "Background FPS limit, FPS boost preset and scanner budget", Category.CLIENT, true); }

    @Override protected boolean showTogglePopup() { return false; }

    private double scanBudgetValue() { return scanBudget.get(); }

    @Override protected void onEnable() { apply(); BlockScanner.setBudgetMillis(scanBudget.get()); }

    @Override
    protected void onDisable() {
        OptionOverrides.clear(mc.options.getMaxFps());
        clearBoost();
        BlockScanner.setBudgetMillis(3.0);
    }

    /** Your own max FPS from Video Settings, refreshed once a second without our override. */
    private int vanillaFps = 120;

    private void readVanillaFps() {
        OptionOverrides.clear(mc.options.getMaxFps());
        try { vanillaFps = mc.options.getMaxFps().getValue(); } catch (Exception ignored) { }
        setFpsOverride();
    }

    private void setFpsOverride() {
        OptionOverrides.set(mc.options.getMaxFps(), () -> background.isOn() && !mc.isWindowFocused() ? Math.min(backgroundFps.getInt(), vanillaFps) : vanillaFps);
    }

    private void apply() {
        if (!isEnabled()) return;
        readVanillaFps();
        if (boost.isOn()) {
            OptionOverrides.set(mc.options.getParticles(), () -> ParticlesMode.values()[1]);         // decreased
            OptionOverrides.set(mc.options.getCloudRenderMode(), () -> CloudRenderMode.values()[0]); // off
            OptionOverrides.set(mc.options.getEntityShadows(), () -> false);
            OptionOverrides.set(mc.options.getEntityDistanceScaling(), () -> 0.75);
        } else clearBoost();
    }

    private void clearBoost() {
        OptionOverrides.clear(mc.options.getParticles());
        OptionOverrides.clear(mc.options.getCloudRenderMode());
        OptionOverrides.clear(mc.options.getEntityShadows());
        OptionOverrides.clear(mc.options.getEntityDistanceScaling());
    }

    private int ticks;

    @Override
    public void onTick() {
        if (++ticks % 20 == 0) readVanillaFps();
    }
}
