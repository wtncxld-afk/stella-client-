package dev.stella.client.mixin;

import dev.stella.client.util.OptionOverrides;
import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SimpleOption.class)
public abstract class SimpleOptionMixin<T> {
    @Inject(method = "getValue", at = @At("HEAD"), cancellable = true)
    private void stella$override(CallbackInfoReturnable<T> cir) {
        Object v = OptionOverrides.get((SimpleOption<?>) (Object) this);
        if (v != null) {
            @SuppressWarnings("unchecked") T t = (T) v;
            cir.setReturnValue(t);
        }
    }
}
