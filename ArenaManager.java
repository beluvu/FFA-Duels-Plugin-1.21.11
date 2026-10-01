package dev.beluvu.bduels;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ArenaManager {

    static final class Arena {
        final String name;
        Bounds bounds;
        Spot spawn1;
        Spot spawn2;
        boolean mobSpawn;

        Arena(String name) {
            this.name = name;
        }

        boolean ready() {
            return bounds != null && spawn1 != null && spawn2 != null;
        }
    }

    static final class FfaArena {
        final String mode;
        Bounds bounds;
        final List<Spot> spawns = new ArrayList<>();
        String kit;
        boolean mobSpawn;

        FfaArena(String mode) {
            this.mode = mode;
        }

        boolean ready() {
            return bounds != null && !spawns.isEmpty() && kit != null;
        }
    }

    private final Main plugin;
    private final File file;
    final Map<String, Arena> duelArenas = new LinkedHashMap<>();
    final Map<String, FfaArena> ffaArenas = new LinkedHashMap<>();

    ArenaManager(Main plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "arenas.yml");
        load();
    }

    void load() {
        duelArenas.clear();
        ffaArenas.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection as = y.getConfigurationSection("arenas");
        if (as != null) {
            for (String n : as.getKeys(false)) {
                ConfigurationSection s = as.getConfigurationSection(n);
                if (s == null) continue;
                Arena a = new Arena(n);
                a.bounds = Bounds.load(s.getConfigurationSection("bounds"));
                a.spawn1 = Spot.load(s.getConfigurationSection("spawn1"));
                a.spawn2 = Spot.load(s.getConfigurationSection("spawn2"));
                a.mobSpawn = s.getBoolean("mobspawn", false);
                duelArenas.put(n, a);
            }
        }

        ConfigurationSection fs = y.getConfigurationSection("ffa");
        if (fs != null) {
            for (String m : fs.getKeys(false)) {
                ConfigurationSection s = fs.getConfigurationSection(m);
                if (s == null) continue;
                FfaArena f = new FfaArena(m);
                f.bounds = Bounds.load(s.getConfigurationSection("bounds"));
                f.kit = s.getString("kit");
                f.mobSpawn = s.getBoolean("mobspawn", false);
                ConfigurationSection sp = s.getConfigurationSection("spawns");
                if (sp != null) {
                    for (String k : sp.getKeys(false)) {
                        Spot spot = Spot.load(sp.getConfigurationSection(k));
                        if (spot != null) f.spawns.add(spot);
                    }
                }
                ffaArenas.put(m, f);
            }
        }
    }

    void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Arena a : duelArenas.values()) {
            ConfigurationSection s = y.createSection("arenas." + a.name);
            if (a.bounds != null) a.bounds.save(s.createSection("bounds"));
            if (a.spawn1 != null) a.spawn1.save(s.createSection("spawn1"));
            if (a.spawn2 != null) a.spawn2.save(s.createSection("spawn2"));
            s.set("mobspawn", a.mobSpawn);
        }
        for (FfaArena f : ffaArenas.values()) {
            ConfigurationSection s = y.createSection("ffa." + f.mode);
            if (f.bounds != null) f.bounds.save(s.createSection("bounds"));
            if (f.kit != null) s.set("kit", f.kit);
            s.set("mobspawn", f.mobSpawn);
            ConfigurationSection sp = s.createSection("spawns");
            for (int i = 0; i < f.spawns.size(); i++) {
                f.spawns.get(i).save(sp.createSection(String.valueOf(i)));
            }
        }
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save arenas.yml: " + e.getMessage());
        }
    }

    Arena duelAt(Location l) {
        for (Arena a : duelArenas.values()) {
            if (a.bounds != null && a.bounds.contains(l)) return a;
        }
        return null;
    }

    FfaArena ffaAt(Location l) {
        for (FfaArena f : ffaArenas.values()) {
            if (f.bounds != null && f.bounds.contains(l)) return f;
        }
        return null;
    }
}
