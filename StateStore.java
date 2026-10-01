package dev.beluvu.bduels;

import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Saves a player's pre-mode state in memory AND on disk, so inventories are never lost
 * even if the server crashes while someone is in a duel/FFA.
 */
final class StateStore {
    private final Main plugin;
    private final File dir;
    private final Map<UUID, PlayerState> mem = new HashMap<>();

    StateStore(Main plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "states");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
    }

    private File file(UUID id) {
        return new File(dir, id + ".yml");
    }

    boolean has(UUID id) {
        return mem.containsKey(id) || file(id).exists();
    }

    /** Capture and persist the current state. Does nothing if a state is already stored. */
    void enter(Player p) {
        if (has(p.getUniqueId())) return;
        PlayerState s = PlayerState.capture(p);
        mem.put(p.getUniqueId(), s);
        try {
            s.save(file(p.getUniqueId()));
        } catch (IOException e) {
            plugin.getLogger().warning("Could not persist state for " + p.getName() + ": " + e.getMessage());
        }
    }

    /** Put the player back as they were and forget the stored state. */
    void restore(Player p) {
        UUID id = p.getUniqueId();
        PlayerState s = mem.remove(id);
        File f = file(id);
        if (s == null && f.exists()) s = PlayerState.load(f);
        if (s == null) return;
        s.apply(p);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
