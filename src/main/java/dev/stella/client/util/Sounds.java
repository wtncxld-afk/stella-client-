package dev.stella.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;

public final class Sounds {
    private Sounds() {}

    public static void ping(float pitch) {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), pitch, 0.6f));
    }

    public static void click() {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), 1.6f, 0.25f));
    }
}
