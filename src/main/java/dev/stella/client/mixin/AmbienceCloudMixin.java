package dev.stella.client.mixin;

import dev.stella.client.StellaClient;
import dev.stella.client.module.ModuleManager;
import dev.stella.client.modules.visuals.Ambience;
import net.minecraft.client.render.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** 1.21.11: the cloud colour is passed straight into CloudRenderer#renderClouds. */
@Mixin(CloudRenderer.class)
public abstract class AmbienceCloudMixin {
    @ModifyVariable(method = "renderClouds", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int stella$cloudColor(int color) {
        if (!StellaClient.isReady()) return color;
        Ambience a = ModuleManager.get(Ambience.class);
        return a.isEnabled() ? a.cloudColor(color) : color;
    }
}
