package dev.stella.client.modules.hud;

import dev.stella.client.hud.HudElement;
import dev.stella.client.module.ModuleManager;
import dev.stella.client.modules.combat.PlayerDetector;
import dev.stella.client.setting.ColorSetting;
import dev.stella.client.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

public class PlayerDetectorHud extends HudElement {
    private final NumberSetting maxRows = add(new NumberSetting("Max Rows", "", 8, 1, 30, 1));
    private final dev.stella.client.hud.CardStyle card = card("Modern");
    private final ColorSetting headerColor = add(new ColorSetting("Header Color", "", 0xFFA855F7));
    private final ColorSetting textColor = add(new ColorSetting("Text Color", "", 0xFFF3F0F7));

    public PlayerDetectorHud() { super("Player Detector HUD", "Names and distances from Player Detector", -160, -4, false); }

    @Override
    public void render(DrawContext ctx, float tickDelta, boolean editor) {
        PlayerDetector pd = ModuleManager.get(PlayerDetector.class);
        List<PlayerDetector.Seen> list = pd.current();
        String head = "PLAYERS " + list.size() + (pd.isEnabled() ? "" : " (detector off)");
        int w = dev.stella.client.render.StellaFont.width(head);
        int n = Math.min(list.size(), maxRows.getInt());
        for (int i = 0; i < n; i++) w = Math.max(w, dev.stella.client.render.StellaFont.width(list.get(i).name() + "  " + (int) list.get(i).distance() + "m"));
        int h = 9 + n * 10;
        card.draw(ctx, this, w, h);
        int px = card.padLeft(), py = card.pad();
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate((float) (px), (float) (py));
        dev.stella.client.render.StellaFont.draw(ctx, head, 0, 0, col(card.accent(headerColor.argb())), true);
        for (int i = 0; i < n; i++) {
            PlayerDetector.Seen s = list.get(i);
            dev.stella.client.render.StellaFont.draw(ctx, s.name(), 0, 11 + i * 10, col(textColor.argb()), true);
            String d = (int) s.distance() + "m";
            dev.stella.client.render.StellaFont.draw(ctx, d, w - dev.stella.client.render.StellaFont.width(d), 11 + i * 10, col(0xFF8E879A), true);
        }
        ctx.getMatrices().popMatrix();
        setSize(w + px + card.pad(), h + py * 2);
    }
}
