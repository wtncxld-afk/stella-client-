package dev.stella.client.mixin;

import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.screen.world.WorldCreator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Used only by the CI smoke test to configure its throwaway test world. */
@Mixin(CreateWorldScreen.class)
public interface CreateWorldScreenAccessor {
    @Accessor("worldCreator")
    WorldCreator getWorldCreator();
}
