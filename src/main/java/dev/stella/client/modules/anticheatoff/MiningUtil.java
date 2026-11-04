package dev.stella.client.modules.anticheatoff;

import dev.stella.client.mixin.InteractionManagerAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

/** Shared access to the client's block-breaking state. */
final class MiningUtil {
    private MiningUtil() {}

    static InteractionManagerAccessor im() { return (InteractionManagerAccessor) MinecraftClient.getInstance().interactionManager; }

    /** The block being broken right now, or null. */
    static BlockPos breaking() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.interactionManager == null || !mc.interactionManager.isBreakingBlock()) return null;
        return im().stella$getBreakingPos();
    }

    /** Vanilla's per-tick progress for that block with your current tool and effects. */
    static float delta(BlockPos pos) {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.world.getBlockState(pos).calcBlockBreakingDelta(mc.player, mc.world, pos);
    }
}
