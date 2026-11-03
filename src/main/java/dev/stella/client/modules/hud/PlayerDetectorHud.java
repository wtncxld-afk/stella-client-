package dev.stella.client.modules.hud;

import dev.stella.client.hud.HudElement;
import dev.stella.client.module.ModuleManager;
import dev.stella.client.modules.combat.PlayerDetector;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.ColorSetting;
import dev.stella.client.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

public class PlayerDetectorHud extends HudElement {
    private final NumberSetting maxRows = add(new NumberSetting("Max Rows", "", 8, 1, 30, 1));
    private final BoolSetting background = add(new BoolSetting("Background", "", true));
    private final ColorSetting headerColor = add(new ColorSetting("Header Color", "", 0xFFA855F7));
    private final ColorSetting textColor = add(new ColorSetting("Text Color", "", 0xFFF3F0F7));

    public PlayerDetectorHud() { super("Player Detector HUD", "Names and distances from Player Detector", 4, 300, false); }

    @Override
    public void render(DrawContext ctx, float tickDelta, boolean editor) {
        PlayerDetector pd = ModuleManager.get(PlayerDetector.class);
        List<PlayerDetector.Seen> list = pd.current();
        String head = "Players " + list.size() + (pd.isEnabled() ? "" : " (detector off)");
        int w = mc.textRenderer.getWidth(head);
        int n = Math.min(list.size(), maxRows.getInt());
        for (int i = 0; i < n; i++) w = Math.max(w, mc.textRenderer.getWidth(list.get(i).name() + "  " + (int) list.get(i).distance() + "m"));
        int h = 11 + n * 10;
        if (background.isOn()) ctx.fill(-3, -3, w + 3, h + 1, col(0xA00D0B12));
        ctx.drawText(mc.textRenderer, head, 0, 0, col(headerColor.argb()), true);
        for (int i = 0; i < n; i++) {
            PlayerDetector.Seen s = list.get(i);
            ctx.drawText(mc.textRenderer, s.name(), 0, 11 + i * 10, col(textColor.argb()), true);
            String d = (int) s.distance() + "m";
            ctx.drawText(mc.textRenderer, d, w - mc.textRenderer.getWidth(d), 11 + i * 10, col(0xFF8E879A), true);
        }
        setSize(w, h);
    }
}
