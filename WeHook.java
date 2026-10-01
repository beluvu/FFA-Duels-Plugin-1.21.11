package dev.beluvu.bduels;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** The only class that touches WorldEdit, so the plugin still loads without it. */
final class WeHook {
    private WeHook() {
    }

    static boolean available() {
        return Bukkit.getPluginManager().isPluginEnabled("WorldEdit")
                || Bukkit.getPluginManager().isPluginEnabled("FastAsyncWorldEdit");
    }

    /** The player's current //pos1 //pos2 selection, or null if none. */
    static Bounds selection(Player p) {
        try {
            var session = WorldEdit.getInstance().getSessionManager().get(BukkitAdapter.adapt(p));
            Region r = session.getSelection(BukkitAdapter.adapt(p.getWorld()));
            BlockVector3 min = r.getMinimumPoint();
            BlockVector3 max = r.getMaximumPoint();
            return new Bounds(p.getWorld().getName(),
                    min.getBlockX(), min.getBlockY(), min.getBlockZ(),
                    max.getBlockX(), max.getBlockY(), max.getBlockZ());
        } catch (Exception e) {
            return null;
        }
    }
}
