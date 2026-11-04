package dev.stella.client.modules.misc;

import dev.stella.client.data.Friends;
import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.notification.Notification;
import dev.stella.client.notification.NotificationManager;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.ColorSetting;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import org.lwjgl.glfw.GLFW;

/** Middle-click a player to add or remove them as a friend. Friends get their own colour everywhere. */
public class FriendsModule extends Module {
    private final BoolSetting middleClick = add(new BoolSetting("Middle Click", "Middle-click a player to add / remove them", true));
    public final ColorSetting color = add(new ColorSetting("Friend Color", "Colour for friends in ESP, Radar and Target HUD", 0xFF4DE38B));
    public final BoolSetting protect = add(new BoolSetting("Protect Friends", "Auto Crystal won't break crystals next to a friend", true));
    private boolean wasDown;

    public FriendsModule() { super("Friends", "Middle-click players to add friends (coloured in ESP, Radar, Target HUD)", Category.MISC, true); }

    @Override protected boolean showTogglePopup() { return false; }

    public static FriendsModule get() { return dev.stella.client.module.ModuleManager.get(FriendsModule.class); }

    /** True when the module is on and this player is on your list. */
    public static boolean is(PlayerEntity p) {
        FriendsModule f = get();
        return f != null && f.isEnabled() && p != null && Friends.isFriend(p.getGameProfile().name());
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        boolean down = mc.currentScreen == null && GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;
        if (down && !wasDown && middleClick.isOn() && mc.crosshairTarget instanceof EntityHitResult eh && eh.getEntity() instanceof PlayerEntity p) {
            String n = p.getGameProfile().name();
            boolean now = Friends.toggle(n);
            NotificationManager.post("Friends", now ? n + " added as a friend" : n + " removed from friends",
                    now ? Notification.Type.SUCCESS : Notification.Type.INFO, 2500);
        }
        wasDown = down;
    }
}
