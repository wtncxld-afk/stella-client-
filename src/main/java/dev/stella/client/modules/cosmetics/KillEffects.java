package dev.stella.client.modules.cosmetics;

import dev.stella.client.module.Category;
import dev.stella.client.module.Module;
import dev.stella.client.render.Render3D;
import dev.stella.client.setting.BoolSetting;
import dev.stella.client.setting.ModeSetting;
import dev.stella.client.util.ColorUtil;
import dev.stella.client.util.CombatEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/** Lightning, a Stella star burst or confetti where a player dies (only on your screen). */
public class KillEffects extends Module {
    private final ModeSetting effect = add(new ModeSetting("Effect", "What appears where they die", "Stella Burst", "Stella Burst", "Lightning", "Confetti"));
    private final BoolSetting onlyMine = add(new BoolSetting("Only Your Kills", "Only for players you hit in the last 5 seconds", true));

    private record Burst(Vec3d pos, double start) {}
    private final List<Burst> bursts = new ArrayList<>();

    public KillEffects() { super("Kill Effects", "Lightning, Stella star burst or confetti where a player dies", Category.COSMETICS, false); }

    /** Called from the entity status mixin when an entity dies (status 3). */
    public void onDeath(Entity e) {
        if (!isEnabled() || mc.world == null || !(e instanceof PlayerEntity) || e == mc.player) return;
        if (onlyMine.isOn() && !(CombatEvents.lastTarget == e && System.currentTimeMillis() - CombatEvents.lastAttackAt < 5000)) return;
        play(e.getEntityPos());
    }

    /** Plays the chosen effect at a position (also used by the preview). */
    public void play(Vec3d p) {
        switch (effect.get()) {
            case "Lightning" -> {
                LightningEntity bolt = new LightningEntity(EntityType.LIGHTNING_BOLT, mc.world);
                bolt.refreshPositionAfterTeleport(p.x, p.y, p.z);
                bolt.setCosmetic(true);
                mc.world.addEntity(bolt);
            }
            case "Confetti" -> {
                for (int i = 0; i < 90; i++) {
                    int c = ColorUtil.fromHsb((float) Math.random(), 0.8f, 1f, 255) & 0xFFFFFF;
                    mc.particleManager.addParticle(new DustParticleEffect(c, 1.3f), p.x, p.y + 1.2, p.z,
                            (Math.random() - 0.5) * 0.6, Math.random() * 0.6 + 0.2, (Math.random() - 0.5) * 0.6);
                }
            }
            default -> {
                bursts.add(new Burst(p, Cosmetics.now()));
                for (int i = 0; i < 40; i++) {
                    int c = Cosmetics.color("Theme", 0, i / 40.0) & 0xFFFFFF;
                    double a = Math.random() * Math.PI * 2;
                    mc.particleManager.addParticle(new DustParticleEffect(c, 1.2f), p.x, p.y + 1, p.z, Math.cos(a) * 0.4, Math.random() * 0.3, Math.sin(a) * 0.4);
                }
            }
        }
    }

    @Override
    public void onRender3D(Render3D r) {
        double now = Cosmetics.now();
        bursts.removeIf(b -> now - b.start > 1.6);
        Vec3d cam = r.cameraPos();
        for (Burst b : bursts) {
            double t = (now - b.start) / 1.6;
            double grow = 1 - Math.pow(1 - Math.min(1, t * 2.2), 3);
            double fade = t < 0.5 ? 1 : 1 - (t - 0.5) / 0.5;
            Vec3d c = b.pos.add(0, 1.0 + t * 0.8, 0);
            Vec3d toCam = cam.subtract(c).normalize();
            Vec3d right = toCam.crossProduct(new Vec3d(0, 1, 0)).normalize(), up = right.crossProduct(toCam).normalize();
            double outer = 1.4 * grow, inner = outer * 0.42, spin = t * 2;
            for (int i = 0; i < 10; i++) {
                double a0 = spin + i * Math.PI / 5 + Math.PI / 2, a1 = spin + (i + 1) * Math.PI / 5 + Math.PI / 2;
                double r0 = i % 2 == 0 ? outer : inner, r1 = i % 2 == 0 ? inner : outer;
                Vec3d q0 = c.add(right.multiply(Math.cos(a0) * r0)).add(up.multiply(Math.sin(a0) * r0));
                Vec3d q1 = c.add(right.multiply(Math.cos(a1) * r1)).add(up.multiply(Math.sin(a1) * r1));
                int col = ColorUtil.withAlpha(Cosmetics.color("Theme", 0, i / 10.0), (int) (0xE0 * fade));
                r.triWorld(c, q0, q1, ColorUtil.withAlpha(0xFFFFFFFF, (int) (0xF0 * fade)), col, col, true);
            }
        }
    }
}
