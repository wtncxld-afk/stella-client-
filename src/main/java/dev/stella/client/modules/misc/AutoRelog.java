package dev.stella.client.modules.misc;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.notification.Notification;
import dev.stella.client.notification.NotificationManager;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.KeySetting;
import dev.stella.client.setting.NumberSetting;
import dev.stella.client.setting.TextSetting;
import dev.stella.client.util.ChatUtil;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.util.InputUtil;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Digs straight down to a target Y-level, disconnects, then reconnects to the
 * configured server. Uses the normal login flow only — it never bypasses
 * authentication, queues, bans or any server access control.
 * <p>
 * Safety: stops on lava/water/other fluids next to the next block, bedrock or
 * unbreakable blocks, large drops, void proximity, low health or damage. Check
 * the server's rules before using automation; many servers forbid it.
 */
public class AutoRelog extends Module {
    public enum State { IDLE, DIGGING, WAITING_DISCONNECT, WAITING_RECONNECT, CONNECTING, DONE, STOPPED }

    private final NumberSetting targetY = add(new NumberSetting("Target Y", "Y-level to dig down to", -58, -64, 320, 1));
    private final BoolSetting start = add(new BoolSetting("Start", "Toggle on to begin (module must be enabled)", false)).onChange(this::onStartToggled);
    private final NumberSetting digDelay = add(new NumberSetting("Dig Delay", "Ticks to wait between blocks", 4, 0, 40, 1).suffix("t"));
    private final NumberSetting reconnectDelay = add(new NumberSetting("Reconnect Delay", "Seconds before reconnecting", 5, 1, 120, 1).suffix("s"));
    private final NumberSetting retryLimit = add(new NumberSetting("Retry Limit", "Reconnect attempts before giving up", 3, 0, 20, 1));
    private final TextSetting server = add(new TextSetting("Server", "Address to reconnect to (blank = current server)", "", 64));
    private final BoolSetting notifications = add(new BoolSetting("Status Notifications", "Show progress notifications", true));
    private final KeySetting emergencyStop = add(new KeySetting("Emergency Stop", "Instantly stops digging/reconnecting", GLFW.GLFW_KEY_END));
    private final BoolSetting stopOnDamage = add(new BoolSetting("Stop On Damage", "Abort if you take damage while digging", true));
    private final NumberSetting minHealth = add(new NumberSetting("Min Health", "Abort below this health", 10, 1, 20, 1));
    private final NumberSetting maxDrop = add(new NumberSetting("Max Drop", "Abort if digging would drop you more than N blocks", 3, 1, 10, 1));

    private State state = State.IDLE;
    private int delay;
    private int retries;
    private long waitUntil;
    private String address = "";
    private float lastHealth = -1;
    private boolean stopKeyDown;
    private String status = "Idle";

    public AutoRelog() {
        super("Auto Relog", "Dig down to a Y-level, disconnect, then reconnect (with safety checks)", Category.MISC);
    }

    public State state() { return state; }
    public String status() { return status; }

    @Override
    protected void onEnable() {
        state = State.IDLE;
        status = "Armed - toggle Start";
        if (start.isOn()) start.set(false);
    }

    @Override protected void onDisable() { stop("Module disabled", false); }

    private void onStartToggled() {
        if (!start.isOn()) {
            if (state == State.DIGGING || state == State.WAITING_RECONNECT || state == State.CONNECTING) stop("Stopped by user", true);
            return;
        }
        if (!isEnabled()) { start.set(false); return; }
        if (!inGame()) { status("Join a world first", Notification.Type.WARNING); start.set(false); return; }
        if (mc.isInSingleplayer() || mc.getCurrentServerEntry() == null) {
            if (server.get().isBlank()) { status("Set a server address (singleplayer cannot reconnect)", Notification.Type.WARNING); start.set(false); return; }
        }
        if (mc.player.getY() < targetY.get() + 1) {
            status("Target Y is above you - only downward digging is supported", Notification.Type.WARNING);
            start.set(false);
            return;
        }
        address = server.get().isBlank() ? mc.getCurrentServerEntry().address : server.get().trim();
        retries = 0;
        lastHealth = mc.player.getHealth();
        state = State.DIGGING;
        status("Digging to Y " + targetY.getInt(), Notification.Type.INFO);
    }

    private void status(String s, Notification.Type type) {
        status = s;
        if (notifications.isOn()) NotificationManager.post("Auto Relog", s, type);
    }

    private void stop(String reason, boolean notify) {
        if (mc.interactionManager != null) mc.interactionManager.cancelBlockBreaking();
        boolean wasActive = state != State.IDLE && state != State.DONE && state != State.STOPPED;
        state = State.STOPPED;
        status = reason;
        if (start.isOn()) start.set(false);
        if (notify && wasActive) {
            NotificationManager.post("Auto Relog stopped", reason, Notification.Type.ALERT, 6000);
            ChatUtil.info("Auto Relog stopped: " + reason);
        }
    }

    @Override
    public void onTick() {
        boolean down = emergencyStop.isBound() && InputUtil.isKeyPressed(mc.getWindow().getHandle(), emergencyStop.key());
        if (down && !stopKeyDown) stop("Emergency stop", true);
        stopKeyDown = down;

        switch (state) {
            case DIGGING -> tickDigging();
            case WAITING_DISCONNECT -> {
                if (mc.world == null) {
                    state = State.WAITING_RECONNECT;
                    waitUntil = System.currentTimeMillis() + reconnectDelay.getInt() * 1000L;
                    status = "Reconnecting in " + reconnectDelay.getInt() + "s";
                }
            }
            case WAITING_RECONNECT -> {
                long left = waitUntil - System.currentTimeMillis();
                status = "Reconnecting in " + Math.max(0, left / 1000 + 1) + "s (attempt " + (retries + 1) + ")";
                if (left <= 0) connect();
            }
            case CONNECTING -> {
                if (mc.world != null && mc.player != null) {
                    state = State.DONE;
                    if (start.isOn()) start.set(false);
                    status("Reconnected to " + address, Notification.Type.SUCCESS);
                } else if (mc.currentScreen instanceof DisconnectedScreen || mc.currentScreen instanceof TitleScreen) {
                    if (retries >= retryLimit.getInt()) {
                        stop("Reconnect failed after " + retries + " retr" + (retries == 1 ? "y" : "ies"), true);
                    } else {
                        retries++;
                        state = State.WAITING_RECONNECT;
                        waitUntil = System.currentTimeMillis() + reconnectDelay.getInt() * 1000L;
                    }
                }
            }
            default -> { }
        }
    }

    private void connect() {
        if (!ServerAddress.isValid(address)) { stop("Invalid server address: " + address, true); return; }
        state = State.CONNECTING;
        status = "Connecting to " + address;
        ServerInfo info = new ServerInfo("Stella Auto Relog", address, ServerInfo.ServerType.OTHER);
        ConnectScreen.connect(new TitleScreen(false), mc, ServerAddress.parse(address), info, false, null);
    }

    private void tickDigging() {
        if (!inGame()) { stop("Left the world while digging", true); return; }
        float hp = mc.player.getHealth();
        if (hp < minHealth.get()) { stop("Health below " + minHealth.getInt(), true); return; }
        if (stopOnDamage.isOn() && lastHealth >= 0 && hp < lastHealth - 0.01f) { stop("Took damage", true); return; }
        lastHealth = hp;
        if (mc.player.isInLava() || mc.player.isOnFire()) { stop("In lava / on fire", true); return; }
        if (mc.player.isSubmergedInWater()) { stop("Submerged in water", true); return; }

        if (mc.player.getY() <= targetY.get() + 0.01) {
            mc.interactionManager.cancelBlockBreaking();
            state = State.WAITING_DISCONNECT;
            status("Reached Y " + MathHelper.floor(mc.player.getY()) + " - disconnecting", Notification.Type.SUCCESS);
            if (mc.world != null) mc.world.disconnect();
            mc.disconnect();
            return;
        }
        if (!mc.player.isOnGround()) return; // falling into the hole we just dug
        if (delay > 0) { delay--; return; }

        List<BlockPos> targets = blocksUnderFeet();
        for (BlockPos p : targets) {
            String unsafe = unsafeReason(p);
            if (unsafe != null) { stop(unsafe, true); return; }
        }
        for (BlockPos p : targets) {
            BlockState s = mc.world.getBlockState(p);
            if (s.isAir() || s.getCollisionShape(mc.world, p).isEmpty()) continue;
            selectBestTool(s);
            mc.player.setPitch(90f);
            if (mc.interactionManager.updateBlockBreakingProgress(p, Direction.UP)) mc.player.swingHand(Hand.MAIN_HAND);
            if (mc.world.getBlockState(p).isAir()) delay = digDelay.getInt();
            return;
        }
    }

    /** Blocks under the player's hitbox footprint (1 to 4 blocks). */
    private List<BlockPos> blocksUnderFeet() {
        Box b = mc.player.getBoundingBox();
        int y = MathHelper.floor(b.minY - 0.01);
        List<BlockPos> out = new ArrayList<>();
        for (int x = MathHelper.floor(b.minX + 0.001); x <= MathHelper.floor(b.maxX - 0.001); x++)
            for (int z = MathHelper.floor(b.minZ + 0.001); z <= MathHelper.floor(b.maxZ - 0.001); z++)
                out.add(new BlockPos(x, y, z));
        return out;
    }

    private String unsafeReason(BlockPos p) {
        int bottom = mc.world.getBottomY();
        if (p.getY() <= bottom + 1) return "Too close to the void / world bottom";
        BlockState s = mc.world.getBlockState(p);
        if (s.isOf(Blocks.BEDROCK)) return "Bedrock below";
        if (s.getHardness(mc.world, p) < 0) return "Unbreakable block below";
        if (!s.getFluidState().isEmpty()) return "Fluid at dig position";
        for (Direction d : Direction.values()) {
            if (d == Direction.UP) continue;
            FluidState f = mc.world.getFluidState(p.offset(d));
            if (!f.isEmpty()) return "Fluid (" + f.getBlockState().getBlock().getName().getString() + ") next to dig position";
        }
        // measure the drop after this block is removed
        int air = 0;
        for (int i = 1; i <= maxDrop.getInt() + 1; i++) {
            BlockPos q = p.down(i);
            if (q.getY() <= bottom) return "Void below";
            BlockState qs = mc.world.getBlockState(q);
            if (!qs.getFluidState().isEmpty()) return "Fluid below dig position";
            if (qs.isOf(Blocks.LAVA) || qs.isOf(Blocks.MAGMA_BLOCK) || qs.isOf(Blocks.FIRE) || qs.isOf(Blocks.POWDER_SNOW)) return "Hazard below (" + qs.getBlock().getName().getString() + ")";
            if (qs.getCollisionShape(mc.world, q).isEmpty()) air++;
            else break;
        }
        if (air > maxDrop.getInt()) return "Drop of more than " + maxDrop.getInt() + " blocks below";
        return null;
    }

    private void selectBestTool(BlockState s) {
        int best = mc.player.getInventory().selectedSlot;
        float bestSpeed = mc.player.getInventory().getStack(best).getMiningSpeedMultiplier(s);
        for (int i = 0; i < 9; i++) {
            ItemStack st = mc.player.getInventory().getStack(i);
            float sp = st.getMiningSpeedMultiplier(s);
            if (sp > bestSpeed) { bestSpeed = sp; best = i; }
        }
        mc.player.getInventory().selectedSlot = best;
    }
}
