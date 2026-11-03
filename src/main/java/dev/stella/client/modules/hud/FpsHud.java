package dev.stella.client.modules.hud;

public class FpsHud extends TextHud {
    public FpsHud() { super("FPS HUD", "Shows the current frames per second", -4, 26, true); }
    @Override protected String label() { return "FPS"; }
    @Override protected String value() { return String.valueOf(mc.getCurrentFps()); }
}
