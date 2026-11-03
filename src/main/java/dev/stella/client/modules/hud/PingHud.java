package dev.stella.client.modules.hud;

import net.minecraft.client.network.PlayerListEntry;

public class PingHud extends TextHud {
    public PingHud() { super("Ping HUD", "Your latency as reported by the server's player list", 4, 52, true); }
    @Override protected String label() { return "Ping"; }

    @Override
    protected String value() {
        if (mc.getNetworkHandler() == null || mc.player == null) return "-";
        if (mc.isInSingleplayer()) return "0 ms";
        PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        return e == null ? "?" : e.getLatency() + " ms";
    }

    @Override
    protected int valueColorFor() {
        if (mc.getNetworkHandler() == null || mc.player == null) return valueColor.argb();
        PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        if (e == null) return valueColor.argb();
        int p = e.getLatency();
        return p < 80 ? 0xFF7CF29A : p < 160 ? 0xFFF5D76E : 0xFFF2777A;
    }
}
