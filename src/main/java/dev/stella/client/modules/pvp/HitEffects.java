package dev.stella.client.modules.pvp;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.modules.cosmetics.Cosmetics;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.ColorSetting;
import dev.stella.client.setting.ModeSetting;
import dev.stella.client.setting.NumberSetting;
import dev.stella.client.util.CombatEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;

/** A coloured burst on every hit and a bigger star burst on crits (particles only you see). */
public class HitEffects extends Module {
    private final ModeSetting colorMode = add(new ModeSetting("Color", "Theme accent, rainbow or a custom colour", "Theme", "Theme", "Rainbow", "Custom"));
    private final ColorSetting custom = add(new ColorSetting("Custom Color", "", 0xFFFF4D6D)).visibleWhen(() -> colorMode.is("Custom"));
    private final NumberSetting amount = add(new NumberSetting("Particles", "Particles per hit", 12, 2, 40, 1));
    private final BoolSetting crits = add(new BoolSetting("Crit Burst", "Bigger burst and sparkles on critical hits", true));
    private final BoolSetting onlyPlayers = add(new BoolSetting("Only Players", "Only when you hit players", false));

    public HitEffects() {
        super("Hit Effects", "Coloured particle burst on hits, star burst on crits", Category.COMBAT);
        CombatEvents.onAttack(this::hit);
    }

    private boolean isCrit() {
        var p = mc.player;
        return p.fallDistance > 0 && !p.isOnGround() && !p.isClimbing() && !p.isTouchingWater() && !p.hasVehicle()
                && p.getAttackCooldownProgress(0.5f) > 0.9f;
    }

    private void hit(Entity e) {
        if (!isEnabled() || mc.player == null || !(e instanceof LivingEntity)) return;
        if (onlyPlayers.isOn() && !(e instanceof net.minecraft.entity.player.PlayerEntity)) return;
        boolean crit = crits.isOn() && isCrit();
        int n = amount.getInt() * (crit ? 2 : 1);
        double cx = e.getX(), cy = e.getY() + e.getHeight() * 0.6, cz = e.getZ();
        for (int i = 0; i < n; i++) {
            int c = Cosmetics.color(colorMode.get(), custom.get(), i / (double) n) & 0xFFFFFF;
            double a = Math.random() * Math.PI * 2, b = Math.random() * Math.PI - Math.PI / 2, sp = (crit ? 0.35 : 0.2) * (0.5 + Math.random());
            mc.particleManager.addParticle(new DustParticleEffect(c, crit ? 1.4f : 1.0f), cx, cy, cz,
                    Math.cos(a) * Math.cos(b) * sp, Math.sin(b) * sp + 0.05, Math.sin(a) * Math.cos(b) * sp);
        }
        if (crit) for (int i = 0; i < 8; i++)
            mc.particleManager.addParticle(ParticleTypes.END_ROD, cx, cy, cz, (Math.random() - 0.5) * 0.3, Math.random() * 0.25, (Math.random() - 0.5) * 0.3);
    }
}
