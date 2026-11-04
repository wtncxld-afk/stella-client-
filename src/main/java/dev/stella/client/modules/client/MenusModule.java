package dev.stella.client.modules.client;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.NumberSetting;
import dev.stella.client.setting.TextSetting;

/** Stella main menu and pause menu (vanilla menus come back when disabled). */
public class MenusModule extends Module {
    public final BoolSetting mainMenu = add(new BoolSetting("Custom Main Menu", "Replace the title screen with the Stella menu", true));
    public final BoolSetting pauseMenu = add(new BoolSetting("Custom Pause Menu", "Replace the Esc menu with the Stella pause menu", true));
    public final BoolSetting quickJoin = add(new BoolSetting("Quick Join Button", "Show a one-click server button on the main menu", true));
    public final TextSetting quickJoinName = add(new TextSetting("Quick Join Name", "Button label", "DonutSMP", 24)).visibleWhen(quickJoin::isOn);
    public final TextSetting quickJoinAddress = add(new TextSetting("Quick Join Address", "Server address for the button", "donutsmp.net", 64)).visibleWhen(quickJoin::isOn);
    public final NumberSetting stars = add(new NumberSetting("Background Stars", "Number of animated stars", 220, 0, 800, 10));
    public final BoolSetting loadingScreens = add(new BoolSetting("Stella Loading Screens", "Animated Stella startup, connecting and world-loading screens", true));
    public final BoolSetting pauseStars = add(new BoolSetting("Pause Menu Stars", "Animated stars over the blurred game in the pause menu", true));

    public final BoolSetting petals = add(new BoolSetting("Petals", "Sakura petals drifting across the menus and loading screens", true));
    public final BoolSetting lightning = add(new BoolSetting("Lightning", "Small crackling lightning bolts around the logo", true));
    public final BoolSetting sparkles = add(new BoolSetting("Sparkles", "Twinkling sparkles and click bursts", true));
    public final NumberSetting particleAmount = add(new NumberSetting("Particle Amount", "How many menu particles", 1.0, 0.0, 2.0, 0.1));
    public final BoolSetting parallax = add(new BoolSetting("Mouse Parallax", "Background layers follow the mouse on the main menu", true));

    public MenusModule() { super("Menus", "Stella-styled main menu and pause menu", Category.CLIENT, true); }

    @Override protected boolean showTogglePopup() { return true; }
}
