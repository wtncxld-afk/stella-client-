package dev.stella.client.util;

import dev.stella.client.module.ModuleManager;
import dev.stella.client.modules.client.ClickGuiModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

/** UI sounds, each category controlled from the THEMES / SOUNDS panel. */
public final class Sounds {
    private static long lastHover;
    private Sounds() {}

    private static ClickGuiModule cfg() { return ModuleManager.get(ClickGuiModule.class); }

    private static void play(SoundEvent e, float pitch, float vol) {
        ClickGuiModule c = cfg();
        float v = (float) (vol * (c == null ? 0.6 : c.volume.get()));
        if (v <= 0.001f) return;
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(e, pitch, v));
    }

    /** Module alerts (Block Notifier, detectors...). */
    public static void ping(float pitch) {
        ClickGuiModule c = cfg();
        if (c != null && !c.soundNotify.isOn()) return;
        play(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), pitch, 1f);
    }

    public static void click() {
        ClickGuiModule c = cfg();
        if (c != null && !c.soundClick.isOn()) return;
        play(SoundEvents.UI_BUTTON_CLICK.value(), 1.6f, 0.4f);
    }

    private static boolean stellaPack() {
        ClickGuiModule c = cfg();
        return c == null || c.soundPack.is("Stella");
    }

    private static SoundEvent custom(String name) {
        return SoundEvent.of(net.minecraft.util.Identifier.of(dev.stella.client.StellaClient.MOD_ID, name));
    }

    public static void toggle(boolean on) {
        ClickGuiModule c = cfg();
        if (c != null && !c.soundClick.isOn()) return;
        if (stellaPack()) play(custom(on ? "toggle_on" : "toggle_off"), 1f, 0.8f);
        else play(SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), on ? 1.6f : 1.1f, 0.7f);
    }

    /** Main menu / pause menu button click. */
    public static void menuClick() {
        ClickGuiModule c = cfg();
        if (c != null && !c.soundClick.isOn()) return;
        if (stellaPack()) play(custom("menu_click"), 1f, 0.8f);
        else play(SoundEvents.UI_BUTTON_CLICK.value(), 1f, 0.5f);
    }

    public static void hover() {
        ClickGuiModule c = cfg();
        long now = System.currentTimeMillis();
        if (c == null || !c.soundHover.isOn() || now - lastHover < 60) return;
        lastHover = now;
        play(SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), 2f, 0.15f);
    }

    public static void open(boolean opening) {
        ClickGuiModule c = cfg();
        if (c != null && !c.soundOpen.isOn()) return;
        if (stellaPack()) play(custom(opening ? "gui_open" : "gui_close"), 1f, 1f);
        else play(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, opening ? 1.4f : 1.0f, 1.2f);
    }
}
