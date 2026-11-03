package dev.stella.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Bridges 1.21.11's input objects (Click / KeyInput / CharInput) to simple
 * coordinate / key-code hooks used by all Stella screens.
 */
public abstract class StellaScreen extends Screen {
    private KeyInput lastKey;

    protected StellaScreen(Text title) { super(title); }

    protected static double mouseX() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.mouse.getScaledX(mc.getWindow());
    }

    protected static double mouseY() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.mouse.getScaledY(mc.getWindow());
    }

    protected boolean onMouseClicked(double mx, double my, int button) { return false; }
    protected boolean onMouseReleased(double mx, double my, int button) { return false; }
    protected boolean onMouseDragged(double mx, double my, int button, double dx, double dy) { return false; }
    protected boolean onCharTyped(char c, int mods) { return false; }

    /** Default: vanilla handling (Esc closes the screen, etc.). */
    protected boolean onKeyPressed(int key, int scan, int mods) {
        return lastKey != null && super.keyPressed(lastKey);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        return onMouseClicked(mouseX(), mouseY(), click.button()) || super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(Click click) {
        return onMouseReleased(mouseX(), mouseY(), click.button()) || super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        return onMouseDragged(mouseX(), mouseY(), click.button(), dx, dy) || super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        lastKey = input;
        int mods = (input.hasShift() ? GLFW.GLFW_MOD_SHIFT : 0) | (input.hasCtrl() ? GLFW.GLFW_MOD_CONTROL : 0)
                | (input.hasAlt() ? GLFW.GLFW_MOD_ALT : 0);
        return onKeyPressed(input.getKeycode(), 0, mods);
    }

    @Override
    public boolean charTyped(CharInput input) {
        boolean any = false;
        for (char c : input.asString().toCharArray()) any |= onCharTyped(c, 0);
        return any;
    }
}
