package dev.stella.client.modules.pvp;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.ModeSetting;
import dev.stella.client.setting.NumberSetting;
import dev.stella.client.util.InvUtil;
import net.minecraft.block.BlockState;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * Look at a respawn anchor: an empty one is charged with glowstone from your hotbar, a charged one is
 * detonated with your totem (or an empty / other slot), then your previous slot is restored.
 */
public class AutoAnchor extends Module {
    private final NumberSetting delay = add(new NumberSetting("Delay", "Ticks between charge and explode", 2, 0, 10, 1));
    private final ModeSetting explodeWith = add(new ModeSetting("Explode With", "Hotbar item used to detonate (never glowstone)", "Totem", AnchorUtil.EXPLODE_WITH));
    private final BoolSetting requireHolding = add(new BoolSetting("Only Holding Anchor/Glowstone", "Only act while you hold an anchor or glowstone", true));
    private final BoolSetting chargeOnly = add(new BoolSetting("Charge Only", "Only charge, never detonate", false));
    private final BoolSetting switchBack = add(new BoolSetting("Switch Back", "Return to your previous hotbar slot afterwards", true));
    private int cooldown, previous = -1;

    public AutoAnchor() { super("Auto Anchor", "Charges and detonates the respawn anchor you look at", Category.COMBAT); }

    @Override
    public void onTick() {
        if (!inGame() || mc.currentScreen != null) return;
        if (cooldown > 0) { cooldown--; return; }
        if (!(mc.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) { restore(); return; }
        BlockState state = mc.world.getBlockState(hit.getBlockPos());
        if (!AnchorUtil.isAnchor(state)) { restore(); return; }
        if (requireHolding.isOn() && previous < 0) {
            var held = mc.player.getMainHandStack();
            if (!held.isOf(Items.RESPAWN_ANCHOR) && !held.isOf(Items.GLOWSTONE)) return;
        }
        if (previous < 0) previous = InvUtil.selected();
        if (AnchorUtil.charges(state) == 0) {
            int glow = InvUtil.hotbar(Items.GLOWSTONE);
            if (glow < 0) return;
            InvUtil.select(glow);
            AnchorUtil.use(hit);
            cooldown = delay.getInt();
        } else if (!chargeOnly.isOn() && AnchorUtil.canExplodeHere()) {
            int slot = AnchorUtil.explodeSlot(explodeWith.get());
            if (slot < 0) return;
            InvUtil.select(slot);
            AnchorUtil.use(hit);
            cooldown = delay.getInt();
        }
    }

    private void restore() {
        if (previous >= 0 && switchBack.isOn()) InvUtil.select(previous);
        previous = -1;
    }

    @Override protected void onDisable() { restore(); }
}
