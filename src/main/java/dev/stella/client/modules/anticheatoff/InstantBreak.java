package dev.stella.client.modules.anticheatoff;

import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.NumberSetting;
import net.minecraft.util.math.BlockPos;

/**
 * Completes block breaking immediately. The server still decides: vanilla servers only accept it when
 * you could really break the block that fast (creative, or a tool/effects strong enough), so this is for
 * local testing where permissions allow it.
 */
public class InstantBreak extends GatedModule {
    private final NumberSetting maxHardness = add(new NumberSetting("Max Hardness", "Only blocks this hard or softer (stone 1.5, obsidian 50)", 3.0, 0.0, 50.0, 0.5));
    private final BoolSetting needTool = add(new BoolSetting("Require Right Tool", "Only when your held tool can harvest the block", true));

    public InstantBreak() { super("InstantBreak", "Break blocks instantly where permissions allow it"); }

    @Override
    protected void tick() {
        BlockPos pos = MiningUtil.breaking();
        if (pos == null) return;
        var state = mc.world.getBlockState(pos);
        float hardness = state.getHardness(mc.world, pos);
        if (hardness < 0 || hardness > maxHardness.get()) return;
        if (needTool.isOn() && state.isToolRequired() && !mc.player.getMainHandStack().isSuitableFor(state)) return;
        MiningUtil.im().stella$setProgress(1.0f);
    }
}
