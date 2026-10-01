package dev.beluvu.bduels;

import dev.beluvu.bduels.ArenaManager.FfaArena;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

final class FfaManager {
    private final Main plugin;
    private final Map<UUID, String> modeOf = new HashMap<>();
    private final Set<UUID> respawning = new HashSet<>();

    FfaManager(Main plugin) {
        this.plugin = plugin;
    }

    boolean in(Player p) {
        return modeOf.containsKey(p.getUniqueId());
    }

    boolean sameMode(Player a, Player b) {
        String m = modeOf.get(a.getUniqueId());
        return m != null && m.equals(modeOf.get(b.getUniqueId()));
    }

    boolean isRespawning(Player p) {
        return respawning.contains(p.getUniqueId());
    }

    String modeOf(Player p) {
        return modeOf.get(p.getUniqueId());
    }

    private List<Player> playersIn(String mode) {
        List<Player> out = new ArrayList<>();
        for (Map.Entry<UUID, String> e : modeOf.entrySet()) {
            if (!e.getValue().equals(mode)) continue;
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) out.add(p);
        }
        return out;
    }

    private Location randomSpawn(FfaArena a) {
        var spot = a.spawns.get(ThreadLocalRandom.current().nextInt(a.spawns.size()));
        return spot.at(a.bounds.world());
    }

    void join(Player p, String modeName) {
        String mode = modeName.toLowerCase(Locale.ROOT);
        FfaArena a = plugin.arenas.ffaArenas.get(mode);
        if (a == null) {
            plugin.msg.send(p, "ffa.unknown");
            return;
        }
        if (!a.ready() || !plugin.kits.exists(a.kit)) {
            plugin.msg.send(p, "ffa.unavailable");
            return;
        }
        if (plugin.busy(p)) {
            plugin.msg.send(p, "ffa.busy");
            return;
        }
        Location spawn = randomSpawn(a);
        if (spawn == null) {
            plugin.msg.send(p, "ffa.unavailable");
            return;
        }

        plugin.states.enter(p);
        modeOf.put(p.getUniqueId(), mode);
        Util.reset(p);
        p.teleport(spawn);
        plugin.kits.give(p, a.kit);
        plugin.msg.send(p, "ffa.joined", "mode", mode);
    }

    void leave(Player p) {
        if (modeOf.remove(p.getUniqueId()) == null) {
            plugin.msg.send(p, "ffa.not-in");
            return;
        }
        respawning.remove(p.getUniqueId());
        plugin.states.restore(p);
        plugin.msg.send(p, "ffa.left");
    }

    void onQuit(Player p) {
        if (modeOf.remove(p.getUniqueId()) == null) return;
        respawning.remove(p.getUniqueId());
        plugin.states.restore(p);
    }

    /** Player would have died: respawn them instantly with a fresh kit, heal the killer. */
    void onLethal(Player victim, Player killer) {
        UUID id = victim.getUniqueId();
        String mode = modeOf.get(id);
        if (mode == null) return;
        FfaArena a = plugin.arenas.ffaArenas.get(mode);
        if (a == null || !a.ready() || !respawning.add(id)) return;

        boolean validKiller = killer != null && !killer.equals(victim) && sameMode(killer, victim);
        Component line = validKiller
                ? plugin.msg.c("ffa.kill", "killer", killer.getName(), "victim", victim.getName())
                : plugin.msg.c("ffa.death", "victim", victim.getName());
        for (Player p : playersIn(mode)) {
            p.sendMessage(line);
        }

        if (validKiller && plugin.getConfig().getBoolean("ffa.heal-killer", true)) {
            killer.setHealth(Util.maxHealth(killer));
            killer.playSound(killer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        }

        Location spawn = randomSpawn(a);
        Util.reset(victim);
        if (spawn != null) victim.teleport(spawn);
        plugin.kits.give(victim, a.kit);

        // damage from the same instant (e.g. several explosion hits) is ignored for one tick
        Bukkit.getScheduler().runTask(plugin, () -> respawning.remove(id));
    }

    void leaveAll() {
        for (UUID id : new ArrayList<>(modeOf.keySet())) {
            modeOf.remove(id);
            Player p = Bukkit.getPlayer(id);
            if (p != null) plugin.states.restore(p);
        }
        respawning.clear();
    }
}
