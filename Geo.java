package dev.beluvu.bduels;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

/** Cuboid area (block coordinates, inclusive). */
record Bounds(String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    boolean contains(Location l) {
        return l != null && l.getWorld() != null && l.getWorld().getName().equals(world)
                && l.getBlockX() >= minX && l.getBlockX() <= maxX
                && l.getBlockY() >= minY && l.getBlockY() <= maxY
                && l.getBlockZ() >= minZ && l.getBlockZ() <= maxZ;
    }

    void save(ConfigurationSection s) {
        s.set("world", world);
        s.set("minX", minX);
        s.set("minY", minY);
        s.set("minZ", minZ);
        s.set("maxX", maxX);
        s.set("maxY", maxY);
        s.set("maxZ", maxZ);
    }

    static Bounds load(ConfigurationSection s) {
        if (s == null || s.getString("world") == null) return null;
        return new Bounds(s.getString("world"), s.getInt("minX"), s.getInt("minY"), s.getInt("minZ"),
                s.getInt("maxX"), s.getInt("maxY"), s.getInt("maxZ"));
    }
}

/** A spawn point without a world (the world comes from the arena bounds). */
record Spot(double x, double y, double z, float yaw, float pitch) {

    static Spot of(Location l) {
        return new Spot(l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    }

    Location at(String worldName) {
        World w = Bukkit.getWorld(worldName);
        if (w == null) return null;
        return new Location(w, x, y, z, yaw, pitch);
    }

    void save(ConfigurationSection s) {
        s.set("x", x);
        s.set("y", y);
        s.set("z", z);
        s.set("yaw", (double) yaw);
        s.set("pitch", (double) pitch);
    }

    static Spot load(ConfigurationSection s) {
        if (s == null || !s.contains("x")) return null;
        return new Spot(s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                (float) s.getDouble("yaw"), (float) s.getDouble("pitch"));
    }
}
