package dev.stella.client.modules.visuals;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.setting.BoolSetting;
import net.minecraft.util.Identifier;

public class NoPumpkinOverlay extends Module {
    private final BoolSetting pumpkin = add(new BoolSetting("Pumpkin", "Hide the carved pumpkin overlay", true));
    private final BoolSetting powderSnow = add(new BoolSetting("Powder Snow", "Hide the powder snow frost overlay", false));
    public final BoolSetting vignette = add(new BoolSetting("Vignette", "Hide the dark screen-edge vignette", false));
    public final BoolSetting spyglass = add(new BoolSetting("Spyglass", "Hide the spyglass scope overlay", false));
    public final BoolSetting portal = add(new BoolSetting("Portal", "Hide the purple nether-portal swirl", false));
    public final BoolSetting nausea = add(new BoolSetting("Nausea", "Hide the nausea distortion overlay", false));

    public NoPumpkinOverlay() { super("No Pumpkin Overlay", "Removes the pumpkin head overlay (and optionally others)", Category.VISUALS); }

    public boolean shouldHide(Identifier texture) {
        if (!isEnabled()) return false;
        String path = texture.getPath();
        if (pumpkin.isOn() && path.contains("pumpkinblur")) return true;
        return powderSnow.isOn() && path.contains("powder_snow_outline");
    }

    public boolean hide(BoolSetting which) { return isEnabled() && which.isOn(); }
}
