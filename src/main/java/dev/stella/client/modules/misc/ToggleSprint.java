package dev.stella.client.modules.misc;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;

/** Keeps the sprint key held for you, exactly as if you held it yourself (vanilla rules still apply). */
public class ToggleSprint extends Module {
    public ToggleSprint() { super("Toggle Sprint", "Always sprint while moving forward", Category.MISC); }

    @Override
    public void onTick() {
        if (!inGame() || mc.currentScreen != null) return;
        mc.options.sprintKey.setPressed(true);
    }

    @Override
    protected void onDisable() {
        if (mc.options != null) mc.options.sprintKey.setPressed(false);
    }
}
