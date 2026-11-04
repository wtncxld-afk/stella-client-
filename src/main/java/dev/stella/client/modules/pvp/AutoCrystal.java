package dev.stella.client.modules.pvp;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.NumberSetting;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * Crosshair auto crystal: while holding end crystals, looking at obsidian/bedrock places a crystal
 * and looking at a crystal breaks it, at the speeds you set.
 */
public class AutoCrystal extends Module {
    private final NumberSetting placeDelay = add(new NumberSetting("Place Delay", "Ticks between placements", 1, 0, 10, 1));
    private final NumberSetting breakDelay = add(new NumberSetting("Break Delay", "Ticks between hits", 1, 0, 10, 1));
    private final BoolSetting place = add(new BoolSetting("Place", "Place crystals on obsidian / bedrock you look at", true));
    private final BoolSetting breakCrystals = add(new BoolSetting("Break", "Hit crystals you look at", true));
    private final BoolSetting requireUse = add(new BoolSetting("Only While Holding Right Click", "Only work while the use key is held", false));
    private final BoolSetting pauseEating = add(new BoolSetting("Pause While Eating", "Do nothing while you use an item (eating, shields...)", true));
    private int placeCd, breakCd;

    public AutoCrystal() { super("Auto Crystal", "Places and breaks end crystals where you look", Category.COMBAT); }

    @Override
    public void onTick() {
        if (!inGame() || mc.currentScreen != null) return;
        if (placeCd > 0) placeCd--;
        if (breakCd > 0) breakCd--;
        if (!mc.player.getMainHandStack().isOf(Items.END_CRYSTAL)) return;
        if (requireUse.isOn() && !mc.options.useKey.isPressed()) return;
        if (pauseEating.isOn() && mc.player.isUsingItem()) return;
        HitResult t = mc.crosshairTarget;
        if (t instanceof EntityHitResult eh && eh.getEntity() instanceof EndCrystalEntity crystal) {
            var fm = dev.stella.client.modules.misc.FriendsModule.get();
            boolean friendNear = fm != null && fm.isEnabled() && fm.protect.isOn() && mc.world.getPlayers().stream()
                    .anyMatch(p -> p != mc.player && dev.stella.client.modules.misc.FriendsModule.is(p) && p.squaredDistanceTo(crystal) < 36);
            if (breakCrystals.isOn() && breakCd == 0 && !friendNear) {
                mc.interactionManager.attackEntity(mc.player, crystal);
                mc.player.swingHand(Hand.MAIN_HAND);
                breakCd = breakDelay.getInt();
            }
        } else if (t instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK && place.isOn() && placeCd == 0) {
            BlockState s = mc.world.getBlockState(bh.getBlockPos());
            if ((s.isOf(Blocks.OBSIDIAN) || s.isOf(Blocks.BEDROCK)) && mc.world.isAir(bh.getBlockPos().up())) {
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bh);
                mc.player.swingHand(Hand.MAIN_HAND);
                placeCd = placeDelay.getInt();
            }
        }
    }
}
