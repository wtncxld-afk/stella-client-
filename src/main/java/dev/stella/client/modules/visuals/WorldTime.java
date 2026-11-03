package dev.stella.client.modules.visuals;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.setting.ModeSetting;
import dev.stella.client.setting.NumberSetting;

/** Client-side only: changes how the sky looks, not the server's time. */
public class WorldTime extends Module {
    private final ModeSetting mode = add(new ModeSetting("Mode", "Fixed time or a faster/slower cycle", "Fixed", "Fixed", "Cycle"));
    private final NumberSetting time = add(new NumberSetting("Time", "0 = sunrise, 6000 = noon, 18000 = midnight", 6000, 0, 23999, 100)).visibleWhen(() -> mode.is("Fixed"));
    private final NumberSetting speed = add(new NumberSetting("Cycle Speed", "Multiplier of the day cycle", 10, 0.1, 100, 0.1)).visibleWhen(() -> mode.is("Cycle"));
    private long start = System.currentTimeMillis();

    public WorldTime() { super("World Time", "Set the client-side time of day", Category.VISUALS); }

    @Override protected void onEnable() { start = System.currentTimeMillis(); }

    public long apply(long original) {
        if (mode.is("Fixed")) return (original / 24000L) * 24000L + time.getInt();
        double ticks = (System.currentTimeMillis() - start) / 50.0 * speed.get();
        return (long) (time.getInt() + ticks) % 24000L;
    }
}
