package dev.stella.client.modules.pvp;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.setting.ModeSetting;
import dev.stella.client.setting.NumberSetting;
import dev.stella.client.util.ChatUtil;
import dev.stella.client.util.InvUtil;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

/**
 * Double Anchor macro: press its keybind while looking at a block. It places an anchor, charges and
 * detonates it, then places, charges and detonates a second one in the same spot, and switches back.
 */
public class DoubleAnchor extends Module {
    private final NumberSetting delay = add(new NumberSetting("Delay", "Ticks between each step", 1, 0, 10, 1));
    private final ModeSetting explodeWith = add(new ModeSetting("Explode With", "Hotbar item used to detonate (never glowstone)", "Totem", AnchorUtil.EXPLODE_WITH));
    private final NumberSetting count = add(new NumberSetting("Anchors", "How many anchors to blow up in a row", 2, 1, 4, 1));

    private BlockHitResult target;
    private BlockPos anchorPos;
    private int step, cooldown, previous;

    public DoubleAnchor() { super("Double Anchor", "Keybind macro: place, charge and detonate two anchors in a row", Category.COMBAT); }

    @Override
    protected void onEnable() {
        if (!inGame() || !(mc.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) { abort("look at a block first"); return; }
        if (InvUtil.hotbar(Items.RESPAWN_ANCHOR) < 0 || InvUtil.hotbar(Items.GLOWSTONE) < 0) { abort("needs respawn anchors and glowstone in your hotbar"); return; }
        if (!AnchorUtil.canExplodeHere()) { abort("anchors do not explode in the Nether"); return; }
        target = hit;
        anchorPos = hit.getBlockPos().offset(hit.getSide());
        previous = InvUtil.selected();
        step = 0;
        cooldown = 0;
    }

    private void abort(String why) {
        if (why != null) ChatUtil.info("Double Anchor: " + why);
        target = null;
        setEnabled(false);
    }

    @Override
    public void onTick() {
        if (target == null || !inGame()) return;
        if (cooldown-- > 0) return;
        int phase = step % 3;
        switch (phase) {
            case 0 -> { // place
                int s = InvUtil.hotbar(Items.RESPAWN_ANCHOR);
                if (s < 0) { finish(); return; }
                InvUtil.select(s);
                AnchorUtil.use(target);
            }
            case 1 -> { // charge
                int s = InvUtil.hotbar(Items.GLOWSTONE);
                if (s < 0 || !AnchorUtil.isAnchor(mc.world.getBlockState(anchorPos))) { finish(); return; }
                InvUtil.select(s);
                AnchorUtil.use(AnchorUtil.hitOn(anchorPos));
            }
            default -> { // detonate
                int s = AnchorUtil.explodeSlot(explodeWith.get());
                if (s < 0 || AnchorUtil.charges(mc.world.getBlockState(anchorPos)) == 0) { finish(); return; }
                InvUtil.select(s);
                AnchorUtil.use(AnchorUtil.hitOn(anchorPos));
            }
        }
        step++;
        cooldown = delay.getInt();
        if (step >= count.getInt() * 3) finish();
    }

    private void finish() {
        InvUtil.select(previous);
        target = null;
        setEnabled(false);
    }

    @Override protected boolean showTogglePopup() { return false; }
}
