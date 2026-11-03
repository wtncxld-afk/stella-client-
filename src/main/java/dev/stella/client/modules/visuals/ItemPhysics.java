package dev.stella.client.modules.visuals;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.setting.BoolSetting;

/** Dropped items lie flat on the ground instead of bobbing and spinning. */
public class ItemPhysics extends Module {
    public final BoolSetting randomRotation = add(new BoolSetting("Random Rotation", "Give each item a random resting angle", true));
    public final dev.stella.client.setting.NumberSetting scale = add(new dev.stella.client.setting.NumberSetting("Item Scale", "Size of dropped items", 1, 0.5, 2.5, 0.05));
    public final dev.stella.client.setting.NumberSetting stackSpread = add(new dev.stella.client.setting.NumberSetting("Stack Spread", "How far apart stacked copies are drawn", 1, 0, 3, 0.1));

    public ItemPhysics() { super("Item Physics", "Dropped items lie flat on the ground", Category.VISUALS); }
}
