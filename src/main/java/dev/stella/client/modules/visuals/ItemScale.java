package dev.stella.client.modules.visuals;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.setting.NumberSetting;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Hand;

/** Size (and position) of the items in your hands, plus dropped items on the ground. */
public class ItemScale extends Module {
    private final NumberSetting mainHand = add(new NumberSetting("Main Hand Scale", "Size of the item in your main hand", 0.8, 0.1, 2.5, 0.05).suffix("x"));
    private final NumberSetting offHand = add(new NumberSetting("Off Hand Scale", "Size of the item in your off hand", 0.8, 0.1, 2.5, 0.05).suffix("x"));
    private final NumberSetting offsetX = add(new NumberSetting("Offset X", "Move held items sideways", 0, -1, 1, 0.02));
    private final NumberSetting offsetY = add(new NumberSetting("Offset Y", "Move held items up/down", 0, -1, 1, 0.02));
    private final NumberSetting offsetZ = add(new NumberSetting("Offset Z", "Move held items closer/further", 0, -1, 1, 0.02));
    private final NumberSetting dropped = add(new NumberSetting("Dropped Items", "Size of item entities on the ground", 1, 0.25, 3, 0.05).suffix("x"));

    public ItemScale() { super("Item Scale", "Change the size of held and dropped items", Category.VISUALS); }

    /** Applied just before the held item is rendered (inside vanilla's push/pop). */
    public void applyHeld(MatrixStack matrices, Hand hand) {
        if (!isEnabled()) return;
        float side = hand == Hand.MAIN_HAND ? 1 : -1;
        matrices.translate(offsetX.getFloat() * side, offsetY.getFloat(), offsetZ.getFloat());
        float s = (hand == Hand.MAIN_HAND ? mainHand : offHand).getFloat();
        matrices.scale(s, s, s);
    }

    public float droppedScale() { return isEnabled() ? dropped.getFloat() : 1f; }
}
