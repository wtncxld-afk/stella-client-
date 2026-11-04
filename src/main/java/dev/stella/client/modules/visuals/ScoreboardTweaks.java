package dev.stella.client.modules.visuals;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;

/** Hide, move or resize the scoreboard sidebar. */
public class ScoreboardTweaks extends Module {
    private final BoolSetting hide = add(new BoolSetting("Hide", "Hide the sidebar completely", false));
    private final NumberSetting scale = add(new NumberSetting("Scale", "Sidebar size", 0.85, 0.4, 1.5, 0.05).suffix("x"));
    private final NumberSetting offsetX = add(new NumberSetting("Offset X", "Move left (-) or right (+)", 0, -400, 50, 1));
    private final NumberSetting offsetY = add(new NumberSetting("Offset Y", "Move up (-) or down (+)", 0, -300, 300, 1));

    public ScoreboardTweaks() { super("Scoreboard", "Hide, move or resize the scoreboard sidebar", Category.VISUALS); }

    public boolean hidden() { return hide.isOn(); }

    /** Scales around the sidebar's right-centre anchor (where vanilla draws it), then offsets. */
    public void push(DrawContext ctx) {
        float s = scale.getFloat();
        float ax = ctx.getScaledWindowWidth(), ay = ctx.getScaledWindowHeight() / 2f;
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(ax + offsetX.getFloat(), ay + offsetY.getFloat());
        m.scale(s, s);
        m.translate(-ax, -ay);
    }

    public void pop(DrawContext ctx) { ctx.getMatrices().popMatrix(); }
}
