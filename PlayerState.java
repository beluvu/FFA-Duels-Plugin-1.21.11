package dev.beluvu.bduels;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Everything we need to put a player back exactly as they were before a duel/FFA. */
final class PlayerState {
    Location loc;
    ItemStack[] inv;
    double health;
    int food;
    float saturation;
    int level;
    float exp;
    GameMode gameMode;
    List<PotionEffect> effects = new ArrayList<>();

    static PlayerState capture(Player p) {
        PlayerState s = new PlayerState();
        s.loc = p.getLocation().clone();
        s.inv = Util.cloneAll(p.getInventory().getContents());
        s.health = p.getHealth();
        s.food = p.getFoodLevel();
        s.saturation = p.getSaturation();
        s.level = p.getLevel();
        s.exp = p.getExp();
        s.gameMode = p.getGameMode();
        s.effects = new ArrayList<>(p.getActivePotionEffects());
        return s;
    }

    void apply(Player p) {
        p.getInventory().clear();
        p.getInventory().setContents(inv);
        for (PotionEffect e : new ArrayList<>(p.getActivePotionEffects())) {
            p.removePotionEffect(e.getType());
        }
        p.addPotionEffects(effects);
        p.setGameMode(gameMode);
        p.setHealth(Math.max(0.5, Math.min(health, Util.maxHealth(p))));
        p.setFoodLevel(food);
        p.setSaturation(saturation);
        p.setLevel(level);
        p.setExp(exp);
        p.setFireTicks(0);
        p.setFallDistance(0f);
        Location target = loc;
        if (target == null || target.getWorld() == null) {
            target = Bukkit.getWorlds().get(0).getSpawnLocation();
        }
        p.teleport(target);
    }

    void save(File f) throws IOException {
        YamlConfiguration y = new YamlConfiguration();
        if (loc != null && loc.getWorld() != null) {
            y.set("world", loc.getWorld().getName());
            y.set("x", loc.getX());
            y.set("y", loc.getY());
            y.set("z", loc.getZ());
            y.set("yaw", (double) loc.getYaw());
            y.set("pitch", (double) loc.getPitch());
        }
        y.set("inv", Arrays.asList(inv));
        y.set("health", health);
        y.set("food", food);
        y.set("saturation", (double) saturation);
        y.set("level", level);
        y.set("exp", (double) exp);
        y.set("gamemode", gameMode.name());
        y.set("effects", effects);
        y.save(f);
    }

    static PlayerState load(File f) {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        PlayerState s = new PlayerState();
        World w = y.getString("world") == null ? null : Bukkit.getWorld(y.getString("world"));
        if (w != null) {
            s.loc = new Location(w, y.getDouble("x"), y.getDouble("y"), y.getDouble("z"),
                    (float) y.getDouble("yaw"), (float) y.getDouble("pitch"));
        }
        s.inv = new ItemStack[41];
        List<?> list = y.getList("inv");
        if (list != null) {
            for (int i = 0; i < list.size() && i < s.inv.length; i++) {
                if (list.get(i) instanceof ItemStack is) s.inv[i] = is;
            }
        }
        s.health = y.getDouble("health", 20.0);
        s.food = y.getInt("food", 20);
        s.saturation = (float) y.getDouble("saturation", 5.0);
        s.level = y.getInt("level");
        s.exp = (float) y.getDouble("exp");
        try {
            s.gameMode = GameMode.valueOf(y.getString("gamemode", "SURVIVAL"));
        } catch (IllegalArgumentException ex) {
            s.gameMode = GameMode.SURVIVAL;
        }
        List<?> eff = y.getList("effects");
        if (eff != null) {
            for (Object o : eff) {
                if (o instanceof PotionEffect pe) s.effects.add(pe);
            }
        }
        return s;
    }
}
