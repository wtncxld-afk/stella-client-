package dev.stella.client.modules.pvp;

import dev.stella.client.util.InvUtil;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Shared respawn-anchor helpers for Auto Anchor and Double Anchor. */
final class AnchorUtil {
    private AnchorUtil() {}

    static final String[] EXPLODE_WITH = {"Totem", "Empty Slot", "Any Other Item"};

    static boolean isAnchor(BlockState s) { return s.isOf(Blocks.RESPAWN_ANCHOR); }

    static int charges(BlockState s) { return isAnchor(s) ? s.get(RespawnAnchorBlock.CHARGES) : 0; }

    /** Anchors only explode outside the Nether (in the Nether they set your spawn instead). */
    static boolean canExplodeHere() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.world != null && mc.world.getRegistryKey() != World.NETHER;
    }

    /** Hotbar slot used to detonate a charged anchor (must not hold glowstone, which would charge it again). */
    static int explodeSlot(String mode) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int s = -1;
        if (mode.equals("Totem")) s = InvUtil.hotbar(Items.TOTEM_OF_UNDYING);
        if (s < 0 && !mode.equals("Any Other Item")) {
            for (int i = 0; i < 9; i++) if (mc.player.getInventory().getStack(i).isEmpty()) { s = i; break; }
        }
        if (s < 0) {
            for (int i = 0; i < 9; i++) {
                ItemStack st = mc.player.getInventory().getStack(i);
                if (!st.isOf(Items.GLOWSTONE) && !st.isOf(Items.RESPAWN_ANCHOR)) { s = i; break; }
            }
        }
        return s;
    }

    static BlockHitResult hitOn(BlockPos pos) {
        return new BlockHitResult(Vec3d.ofCenter(pos).add(0, 0.5, 0), Direction.UP, pos, false);
    }

    static void use(BlockHitResult hit) {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);
    }
}
