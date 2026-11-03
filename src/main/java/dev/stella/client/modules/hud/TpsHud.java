package dev.stella.client.modules.hud;

import dev.stella.client.util.track.TpsTracker;

public class TpsHud extends TextHud {
    public TpsHud() {
        super("TPS HUD", "Estimated server TPS from world-time packets (shows '...' until enough samples exist)", -4, 46, false);
    }

    @Override protected String label() { return "TPS"; }

    @Override
    protected String value() {
        if (mc.isInSingleplayer() && mc.getServer() != null) {
            double ms = mc.getServer().getAverageTickTime();
            return String.format("%.1f", Math.min(20.0, 1000.0 / Math.max(50.0, ms)));
        }
        double tps = TpsTracker.tps();
        if (tps < 0) return "...";
        double since = TpsTracker.secondsSinceUpdate();
        return String.format("%.1f", tps) + (since > 3 ? " (stalled " + (int) since + "s)" : " ~");
    }

    @Override
    protected int valueColorFor() {
        double tps = TpsTracker.tps();
        if (tps < 0) return valueColor.argb();
        return tps > 18 ? 0xFF7CF29A : tps > 14 ? 0xFFF5D76E : 0xFFF2777A;
    }
}
