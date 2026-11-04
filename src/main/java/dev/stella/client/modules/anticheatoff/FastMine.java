package dev.stella.client.modules.anticheatoff;

import dev.stella.client.setting.NumberSetting;

/** Shorter delay between breaking one block and starting the next (singleplayer / authorized test servers only). */
public class FastMine extends GatedModule {
    private final NumberSetting delay = add(new NumberSetting("Break Delay", "Ticks between blocks (vanilla 5)", 0, 0, 5, 1));

    public FastMine() { super("FastMine", "Remove the delay between mining blocks"); }

    @Override
    protected void tick() {
        var im = MiningUtil.im();
        if (im.stella$getCooldown() > delay.getInt()) im.stella$setCooldown(delay.getInt());
    }
}
