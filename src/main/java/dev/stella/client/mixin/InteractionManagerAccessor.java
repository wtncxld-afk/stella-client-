package dev.stella.client.mixin;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Block-breaking progress and cooldown (SpeedMine / InstantBreak / FastMine). */
@Mixin(ClientPlayerInteractionManager.class)
public interface InteractionManagerAccessor {
    @Accessor("currentBreakingProgress") float stella$getProgress();
    @Accessor("currentBreakingProgress") void stella$setProgress(float v);
    @Accessor("blockBreakingCooldown") int stella$getCooldown();
    @Accessor("blockBreakingCooldown") void stella$setCooldown(int v);
    @Accessor("currentBreakingPos") net.minecraft.util.math.BlockPos stella$getBreakingPos();
}
