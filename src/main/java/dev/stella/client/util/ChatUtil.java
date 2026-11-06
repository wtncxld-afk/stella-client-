package dev.stella.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class ChatUtil {
    private ChatUtil() {}

    public static MutableText prefix() {
        return Text.literal(dev.stella.client.Brand.ISRAELI ? "✡ " : "✦ ").formatted(dev.stella.client.Brand.ISRAELI ? Formatting.BLUE : Formatting.LIGHT_PURPLE)
                .append(Text.literal(dev.stella.client.Brand.NAME).formatted(dev.stella.client.Brand.ISRAELI ? Formatting.AQUA : Formatting.DARK_PURPLE, Formatting.BOLD))
                .append(Text.literal(" » ").formatted(Formatting.DARK_GRAY));
    }

    public static void info(String msg) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        mc.player.sendMessage(prefix().append(Text.literal(msg).formatted(Formatting.GRAY)), false);
    }

    public static void send(Text text) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        mc.player.sendMessage(prefix().append(text), false);
    }
}
