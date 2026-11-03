package dev.stella.client.modules.visuals;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.NumberSetting;
import dev.stella.client.util.Animation;
import dev.stella.client.util.OptionOverrides;
import net.minecraft.world.World;

/** Raises the effective gamma without changing your saved brightness option. */
public class Fullbright extends Module {
    private final NumberSetting brightness = add(new NumberSetting("Brightness", "Effective gamma (vanilla max is 1.0)", 15, 1, 15, 0.5));
    private final BoolSetting fade = add(new BoolSetting("Smooth Fade", "Fade the brightness in instead of switching instantly", true));
    private final BoolSetting overworldOnly = add(new BoolSetting("Overworld Only", "Leave the Nether and End at normal brightness", false));
    private final Animation anim = new Animation(0, 6);
    private double vanillaGamma = 0.5;

    public Fullbright() { super("Fullbright", "See in the dark", Category.VISUALS); }

    @Override
    protected void onEnable() {
        anim.snap(0);
        vanillaGamma = mc.options.getGamma().getValue();
        OptionOverrides.set(mc.options.getGamma(), this::current);
    }

    @Override
    public void onTick() {
        // re-read the player's own gamma (it may change in Video Settings)
        OptionOverrides.clear(mc.options.getGamma());
        vanillaGamma = mc.options.getGamma().getValue();
        OptionOverrides.set(mc.options.getGamma(), this::current);
    }

    private Double current() {
        boolean active = !overworldOnly.isOn() || mc.world == null || mc.world.getRegistryKey() == World.OVERWORLD;
        double target = active ? 1 : 0;
        double t = fade.isOn() ? anim.target(target).get() : target;
        return vanillaGamma + (brightness.get() - vanillaGamma) * t;
    }

    @Override protected void onDisable() { OptionOverrides.clear(mc.options.getGamma()); }
}
