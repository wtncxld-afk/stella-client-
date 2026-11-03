package dev.stella.client.modules.visuals;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.NumberSetting;

public class CustomFov extends Module {
    public final NumberSetting fov = add(new NumberSetting("FOV", "Field of view in degrees", 90, 30, 150, 1).suffix("°"));
    public final BoolSetting dynamic = add(new BoolSetting("Dynamic FOV", "Keep sprint / speed FOV changes", true));
    private final BoolSetting reset = add(new BoolSetting("Reset", "Toggle to restore 90°", false)).onChange(this::doReset);

    public CustomFov() { super("Custom FOV", "Field of view beyond the vanilla slider", Category.VISUALS); }

    private void doReset() {
        if (reset.isOn()) { fov.reset(); dynamic.reset(); reset.set(false); }
    }

    /** @param original vanilla result (already includes dynamic modifiers) */
    public float apply(float original) {
        float base = mc.options.getFov().getValue();
        float ratio = base > 0 ? original / base : 1f;
        return fov.getFloat() * (dynamic.isOn() ? ratio : 1f);
    }
}
