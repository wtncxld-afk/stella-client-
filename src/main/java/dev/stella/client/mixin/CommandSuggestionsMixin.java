package dev.stella.client.mixin;

import dev.stella.client.StellaClient;
import dev.stella.client.module.ModuleManager;
import dev.stella.client.modules.combat.AntiVanish;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.CommandSuggestionsS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/** Feeds command-suggestion responses to Anti Vanish's opt-in tab-complete check. */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class CommandSuggestionsMixin {
    @Inject(method = "onCommandSuggestions", at = @At("TAIL"))
    private void stella$suggestions(CommandSuggestionsS2CPacket packet, CallbackInfo ci) {
        if (!StellaClient.isReady()) return;
        List<String> names = new ArrayList<>();
        packet.getSuggestions().getList().forEach(s -> names.add(s.getText()));
        AntiVanish av = ModuleManager.get(AntiVanish.class);
        if (av != null) av.onSuggestions(packet.id(), names);
    }
}
