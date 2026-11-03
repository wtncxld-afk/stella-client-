package dev.stella.client.mixin;

import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Raw weather gradients (unaffected by the Weather Customizer's visual override). */
@Mixin(World.class)
public interface WorldAccessor {
    @Accessor("rainGradient") float stella$rainGradient();
    @Accessor("thunderGradient") float stella$thunderGradient();
}
