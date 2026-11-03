package dev.stella.client.modules.hud;

import dev.stella.client.setting.BoolSetting;

public class FpsHud extends TextHud {
    private final BoolSetting colorByValue = add(new BoolSetting("Color By Value", "Green / yellow / red depending on FPS", false));
    private final BoolSetting lowFlag = add(new BoolSetting("Show Lowest", "Also show the lowest FPS of the last 10 seconds", false));
    private final int[] history = new int[10];
    private int idx;
    private long lastSample;

    public FpsHud() { super("FPS HUD", "Shows the current frames per second", -4, 26, true); }

    @Override protected String label() { return "FPS"; }

    @Override
    protected String value() {
        int fps = mc.getCurrentFps();
        long now = System.currentTimeMillis();
        if (now - lastSample >= 1000) { history[idx++ % history.length] = fps; lastSample = now; }
        if (!lowFlag.isOn()) return String.valueOf(fps);
        int low = Integer.MAX_VALUE;
        for (int h : history) if (h > 0) low = Math.min(low, h);
        return fps + (low == Integer.MAX_VALUE ? "" : "  (low " + low + ")");
    }

    @Override
    protected int valueColorFor() {
        if (!colorByValue.isOn()) return valueColor.argb();
        int fps = mc.getCurrentFps();
        return fps >= 60 ? 0xFF7CF29A : fps >= 30 ? 0xFFF5D76E : 0xFFF2777A;
    }
}
