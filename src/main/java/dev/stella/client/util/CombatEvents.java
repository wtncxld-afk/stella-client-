package dev.stella.client.util;

import net.minecraft.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Fired when you attack an entity (from the interaction manager mixin). */
public final class CombatEvents {
    private CombatEvents() {}

    private static final List<Consumer<Entity>> ATTACK = new ArrayList<>();
    public static Entity lastTarget;
    public static long lastAttackAt;

    public static void onAttack(Consumer<Entity> l) { ATTACK.add(l); }

    public static void fireAttack(Entity target) {
        lastTarget = target;
        lastAttackAt = System.currentTimeMillis();
        for (Consumer<Entity> l : ATTACK) {
            try { l.accept(target); } catch (Throwable t) { dev.stella.client.StellaClient.reportError("attack listener", t); }
        }
    }
}
