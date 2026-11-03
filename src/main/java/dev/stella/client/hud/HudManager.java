package dev.stella.client.hud;

import dev.stella.client.StellaClient;
import dev.stella.client.module.Module;
import dev.stella.client.module.ModuleManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

public final class HudManager {
    private HudManager() {}

    public static List<HudElement> elements() {
        List<HudElement> out = new ArrayList<>();
        for (Module m : ModuleManager.all()) if (m instanceof HudElement h) out.add(h);
        return out;
    }

    public static void render(DrawContext ctx, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden) return;
        if (mc.currentScreen instanceof HudEditorScreen) return; // editor draws its own previews
        for (HudElement h : elements()) {
            if (!h.isEnabled()) continue;
            renderElement(ctx, h, tickDelta, false);
        }
    }

    public static void renderElement(DrawContext ctx, HudElement h, float tickDelta, boolean editor) {
        clamp(h);
        ctx.getMatrices().push();
        if (!h.isSized()) ctx.getMatrices().translate(-10000, -10000, 0); // measure off-screen first
        ctx.getMatrices().translate(h.x(), h.y(), 0);
        float s = h.scale.getFloat();
        ctx.getMatrices().scale(s, s, 1);
        try {
            h.render(ctx, tickDelta, editor);
        } catch (Throwable t) {
            StellaClient.reportError("hud " + h.getName(), t);
        }
        ctx.getMatrices().pop();
    }

    /** Keeps elements on-screen after a resize or GUI-scale change. */
    public static void clamp(HudElement h) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        // negative default coordinates anchor to the right / bottom edge on first layout
        if (!h.isSized()) return; // anchor once the real size is known
        if (h.x() < 0) h.posX.set((double) (sw + h.x() - h.scaledWidth()));
        if (h.y() < 0) h.posY.set((double) (sh + h.y() - h.scaledHeight()));
        float w = h.scaledWidth(), hh = h.scaledHeight();
        float x = Math.max(0, Math.min(sw - Math.min(w, sw), h.x()));
        float y = Math.max(0, Math.min(sh - Math.min(hh, sh), h.y()));
        if (x != h.x() || y != h.y()) {
            h.posX.set((double) x);
            h.posY.set((double) y);
        }
    }

    /** Forwards chat-screen clicks to interactive HUD elements. */
    public static boolean click(double mx, double my, int button) {
        for (HudElement h : elements()) {
            if (!h.isEnabled()) continue;
            if (mx >= h.x() && my >= h.y() && mx <= h.x() + h.scaledWidth() && my <= h.y() + h.scaledHeight()) {
                double s = h.scale.get();
                if (h.onClick((mx - h.x()) / s, (my - h.y()) / s, button)) return true;
            }
        }
        return false;
    }

    public static boolean scroll(double mx, double my, double amount) {
        for (HudElement h : elements()) {
            if (!h.isEnabled()) continue;
            if (mx >= h.x() && my >= h.y() && mx <= h.x() + h.scaledWidth() && my <= h.y() + h.scaledHeight()) {
                double s = h.scale.get();
                if (h.onScroll((mx - h.x()) / s, (my - h.y()) / s, amount)) return true;
            }
        }
        return false;
    }
}
