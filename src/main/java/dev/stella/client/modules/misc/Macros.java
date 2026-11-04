package dev.stella.client.modules.misc;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.setting.KeySetting;
import dev.stella.client.setting.TextSetting;
import dev.stella.client.util.Keys;
import org.lwjgl.glfw.GLFW;

/** Bind keys to commands or chat messages (e.g. /home, /ah, /spawn). */
public class Macros extends Module {
    private static final int COUNT = 6;
    private final KeySetting[] keys = new KeySetting[COUNT];
    private final TextSetting[] texts = new TextSetting[COUNT];
    private final boolean[] was = new boolean[COUNT];

    public Macros() {
        super("Macros", "Keys that run a command or send a message", Category.MISC);
        String[] defaults = {"/home", "/ah", "/spawn", "/rtp", "", ""};
        for (int i = 0; i < COUNT; i++) {
            keys[i] = add(new KeySetting("Key " + (i + 1), "Key for macro " + (i + 1), GLFW.GLFW_KEY_UNKNOWN));
            texts[i] = add(new TextSetting("Macro " + (i + 1), "Command (start with /) or chat message", defaults[i], 256));
        }
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        for (int i = 0; i < COUNT; i++) {
            boolean down = mc.currentScreen == null && keys[i].isBound() && Keys.down(keys[i].key());
            if (down && !was[i]) run(texts[i].get());
            was[i] = down;
        }
    }

    private void run(String s) {
        s = s.trim();
        if (s.isEmpty()) return;
        if (s.startsWith("/")) mc.player.networkHandler.sendChatCommand(s.substring(1));
        else mc.player.networkHandler.sendChatMessage(s);
    }
}
