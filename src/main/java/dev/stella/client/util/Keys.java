package dev.stella.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/** Raw key polling (works regardless of key bindings). */
public final class Keys {
    private Keys() {}

    public static boolean down(int key) {
        if (key == GLFW.GLFW_KEY_UNKNOWN) return false;
        return InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow(), key);
    }

    public static boolean ctrl() { return down(GLFW.GLFW_KEY_LEFT_CONTROL) || down(GLFW.GLFW_KEY_RIGHT_CONTROL); }
}
