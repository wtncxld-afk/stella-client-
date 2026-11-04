package dev.stella.client.modules.anticheatoff;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.module.ModuleManager;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.TextSetting;
import net.minecraft.client.MinecraftClient;

import java.util.Locale;

/**
 * Where the Anti Cheat Off modules are allowed to run: your own singleplayer worlds, plus servers you
 * list here yourself (your own test server, a server whose owner allows it). Everywhere else they
 * switch themselves off. There is deliberately no anti-cheat bypass in any of these modules.
 */
public class TestEnvironment extends Module {
    public final BoolSetting singleplayer = add(new BoolSetting("Singleplayer", "Allow in your own singleplayer worlds", true));
    public final TextSetting servers = add(new TextSetting("Authorized Servers",
            "Comma-separated server addresses where you have permission to use these (e.g. localhost, test.myserver.net)", "localhost", 300));

    public TestEnvironment() { super("Test Environment", "Where Anti Cheat Off modules may run (singleplayer + your authorized servers)", Category.ANTI_CHEAT_OFF, true); }

    @Override protected boolean showTogglePopup() { return false; }

    /** True when the current world is singleplayer (if allowed) or an authorized server. */
    public static boolean allowed() {
        MinecraftClient mc = MinecraftClient.getInstance();
        TestEnvironment env = ModuleManager.get(TestEnvironment.class);
        if (env == null || mc.world == null) return false;
        if (mc.isInSingleplayer()) return env.singleplayer.isOn();
        var entry = mc.getCurrentServerEntry();
        if (entry == null) return false;
        String host = host(entry.address);
        for (String s : env.servers.get().split(",")) {
            String a = host(s);
            if (!a.isEmpty() && (host.equals(a) || host.endsWith("." + a))) return true;
        }
        return false;
    }

    private static String host(String address) {
        String h = address.trim().toLowerCase(Locale.ROOT);
        int colon = h.lastIndexOf(':');
        if (colon > 0 && h.indexOf(':') == colon) h = h.substring(0, colon);
        return h.endsWith(".") ? h.substring(0, h.length() - 1) : h;
    }

    public static String where() {
        MinecraftClient mc = MinecraftClient.getInstance();
        var e = mc.getCurrentServerEntry();
        return mc.isInSingleplayer() ? "singleplayer" : e == null ? "this world" : e.address;
    }
}
