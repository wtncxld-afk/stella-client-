package dev.stella.client.mixin;

import dev.stella.client.StellaClient;
import dev.stella.client.gui.menu.StellaMainMenu;
import dev.stella.client.gui.menu.StellaPauseScreen;
import dev.stella.client.module.ModuleManager;
import dev.stella.client.modules.client.MenusModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Swaps the vanilla title / pause screens for the Stella versions when the Menus module wants it. */
@Mixin(MinecraftClient.class)
public abstract class MenuRedirectMixin {
    @Shadow public abstract void setScreen(Screen screen);

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void stella$menus(Screen screen, CallbackInfo ci) {
        if (!StellaClient.isReady() || screen == null) return;
        MenusModule m = ModuleManager.get(MenusModule.class);
        if (m == null || !m.isEnabled()) return;
        if (screen.getClass() == TitleScreen.class && m.mainMenu.isOn() && !StellaMainMenu.bypass
                && !Boolean.getBoolean("stella.smokeTest.vanillaTitle")) {
            ci.cancel();
            setScreen(new StellaMainMenu());
        } else if (screen.getClass() == GameMenuScreen.class && m.pauseMenu.isOn() && ((GameMenuScreen) screen).shouldShowMenu()) {
            ci.cancel();
            setScreen(new StellaPauseScreen());
        }
    }
}
