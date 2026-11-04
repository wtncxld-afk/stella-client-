package dev.stella.client.modules.anticheatoff;

import dev.stella.client.setting.NumberSetting;
import net.minecraft.util.math.BlockPos;

/** Faster block breaking (singleplayer / authorized test servers only). */
public class SpeedMine extends GatedModule {
    private final NumberSetting multiplier = add(new NumberSetting("Speed", "Mining speed multiplier", 1.5, 1.0, 4.0, 0.05).suffix("x"));
    private final NumberSetting finishAt = add(new NumberSetting("Finish At", "Break the block once progress reaches this (1.0 = normal)", 1.0, 0.5, 1.0, 0.05));

    public SpeedMine() { super("SpeedMine", "Mine blocks faster (adjustable multiplier)"); }

    @Override
    protected void tick() {
        BlockPos pos = MiningUtil.breaking();
        if (pos == null) return;
        var im = MiningUtil.im();
        float p = im.stella$getProgress() + MiningUtil.delta(pos) * (float) (multiplier.get() - 1.0);
        if (p >= finishAt.get()) p = 1.0f;
        im.stella$setProgress(Math.min(1.0f, p));
    }
}
