package dev.stella.client.util;

import net.minecraft.client.MinecraftClient;

public final class Dimensions {
    private Dimensions() {}

    public static String current() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return "unknown";
        return mc.world.getRegistryKey().getValue().toString();
    }

    public static String pretty(String id) {
        return switch (id) {
            case "minecraft:overworld" -> "Overworld";
            case "minecraft:the_nether" -> "Nether";
            case "minecraft:the_end" -> "End";
            default -> id;
        };
    }
}
