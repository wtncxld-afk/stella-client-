package dev.stella.client.modules.visuals;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.setting.BoolSetting;

/** Dropped items lie flat on the ground instead of bobbing and spinning. */
public class ItemPhysics extends Module {
    public final BoolSetting randomRotation = add(new BoolSetting("Random Rotation", "Give each item a random resting angle", true));
    public final BoolSetting spinInAir = add(new BoolSetting("Spin In Air", "Tumble while falling", true));

    public ItemPhysics() { super("Item Physics", "Dropped items lie flat on the ground", Category.VISUALS); }
}
