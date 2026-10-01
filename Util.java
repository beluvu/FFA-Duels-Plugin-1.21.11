package dev.beluvu.bduels;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

import java.util.ArrayList;

final class Util {
    private Util() {
    }

    static double maxHealth(Player p) {
        AttributeInstance a = p.getAttribute(Attribute.MAX_HEALTH);
        return a == null ? 20.0 : a.getValue();
    }

    /** Full clean slate: empty inventory, full health/food, no effects, survival. */
    static void reset(Player p) {
        p.getInventory().clear();
        p.setGameMode(GameMode.SURVIVAL);
        for (PotionEffect e : new ArrayList<>(p.getActivePotionEffects())) {
            p.removePotionEffect(e.getType());
        }
        p.setHealth(maxHealth(p));
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setLevel(0);
        p.setExp(0f);
        p.setFireTicks(0);
        p.setFallDistance(0f);
        p.setVelocity(new Vector(0, 0, 0));
    }

    static boolean hasTotem(Player p) {
        return p.getInventory().getItemInMainHand().getType() == Material.TOTEM_OF_UNDYING
                || p.getInventory().getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING;
    }

    static ItemStack[] cloneAll(ItemStack[] src) {
        ItemStack[] out = new ItemStack[src.length];
        for (int i = 0; i < src.length; i++) {
            out[i] = src[i] == null ? null : src[i].clone();
        }
        return out;
    }
}
