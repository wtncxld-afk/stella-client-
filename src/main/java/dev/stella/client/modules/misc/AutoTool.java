package dev.stella.client.modules.misc;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.NumberSetting;
import dev.stella.client.util.InvUtil;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * While you hold left-click on a block, selects the hotbar item that mines it fastest (a correct tool
 * first), then returns to your previous slot when you stop mining.
 */
public class AutoTool extends Module {
    private final BoolSetting switchBack = add(new BoolSetting("Switch Back", "Return to your previous slot when you stop mining", true));
    private final BoolSetting protect = add(new BoolSetting("Protect Low Durability", "Never pick a tool that is about to break", true));
    private final NumberSetting minUses = add(new NumberSetting("Minimum Uses Left", "Uses a tool must have left to be picked", 10, 1, 100, 1)).visibleWhen(protect::isOn);
    private final BoolSetting creative = add(new BoolSetting("In Creative", "Also switch in creative mode", false));
    private int previous = -1;

    public AutoTool() { super("Auto Tool", "Picks the best tool in your hotbar for the block you're mining", Category.MISC); }

    @Override
    public void onTick() {
        if (!inGame() || mc.currentScreen != null) return;
        boolean mining = mc.options.attackKey.isPressed() && mc.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK;
        if (!mining || (mc.player.isCreative() && !creative.isOn())) { restore(); return; }
        BlockState state = mc.world.getBlockState(((BlockHitResult) mc.crosshairTarget).getBlockPos());
        if (state.isAir()) return;
        int best = -1;
        float bestScore = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack st = mc.player.getInventory().getStack(i);
            if (protect.isOn() && st.isDamageable() && st.getMaxDamage() - st.getDamage() < minUses.getInt()) continue;
            float speed = st.getMiningSpeedMultiplier(state);
            // a tool that can actually harvest the block (drops it) always beats one that can't
            float score = speed + (st.isSuitableFor(state) ? 1000 : 0);
            if (score > bestScore) { bestScore = score; best = i; }
        }
        // only switch when it is actually faster than what you hold
        ItemStack held = mc.player.getMainHandStack();
        float heldScore = held.getMiningSpeedMultiplier(state) + (held.isSuitableFor(state) ? 1000 : 0);
        if (best < 0 || best == InvUtil.selected() || bestScore <= heldScore || bestScore <= 1f) return;
        if (previous < 0) previous = InvUtil.selected();
        InvUtil.select(best);
    }

    private void restore() {
        if (previous >= 0 && switchBack.isOn()) InvUtil.select(previous);
        previous = -1;
    }

    @Override protected void onDisable() { restore(); }
}
