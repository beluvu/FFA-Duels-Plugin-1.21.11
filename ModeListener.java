package dev.beluvu.bduels;

import dev.beluvu.bduels.ArenaManager.Arena;
import dev.beluvu.bduels.ArenaManager.FfaArena;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class ModeListener implements Listener {

    private record Hit(UUID attacker, long time) {
    }

    private static final long LAST_HIT_MS = 10_000L;
    private static final Set<SpawnReason> NATURAL_SPAWNS = Set.of(
            SpawnReason.NATURAL, SpawnReason.SPAWNER, SpawnReason.RAID, SpawnReason.PATROL,
            SpawnReason.REINFORCEMENTS, SpawnReason.TRAP, SpawnReason.VILLAGE_INVASION,
            SpawnReason.CHUNK_GEN, SpawnReason.NETHER_PORTAL);

    private final Main plugin;
    private final Map<UUID, UUID> crystalOwner = new HashMap<>();
    private final Map<UUID, Hit> lastHit = new HashMap<>();

    ModeListener(Main plugin) {
        this.plugin = plugin;
    }

    // ---------- helpers ----------

    private Player attackerOf(Entity d) {
        if (d instanceof Player p) return p;
        if (d instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        if (d instanceof TNTPrimed t && t.getSource() instanceof Player p) return p;
        if (d instanceof EnderCrystal c) {
            UUID owner = crystalOwner.get(c.getUniqueId());
            return owner == null ? null : Bukkit.getPlayer(owner);
        }
        return null;
    }

    private void rememberCrystal(EnderCrystal c, Player p) {
        if (crystalOwner.size() > 500) crystalOwner.clear();
        crystalOwner.put(c.getUniqueId(), p.getUniqueId());
    }

    private boolean sameContext(Player a, Player b) {
        return plugin.duels.sameDuel(a, b) || plugin.ffa.sameMode(a, b);
    }

    private Player recentAttacker(Player victim) {
        Hit h = lastHit.get(victim.getUniqueId());
        if (h == null || System.currentTimeMillis() - h.time() > LAST_HIT_MS) return null;
        Player p = Bukkit.getPlayer(h.attacker());
        return (p != null && sameContext(p, victim)) ? p : null;
    }

    private boolean isAdmin(Player p) {
        return p.hasPermission("bduels.admin");
    }

    // ---------- combat ----------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        // remember who hit/placed an end crystal, so crystal kills are credited
        if (e instanceof EntityDamageByEntityEvent be && be.getEntity() instanceof EnderCrystal c) {
            Player a = attackerOf(be.getDamager());
            if (a != null) rememberCrystal(c, a);
            return;
        }
        if (!(e.getEntity() instanceof Player v)) return;

        DuelManager.Duel d = plugin.duels.get(v);
        boolean vIn = d != null || plugin.ffa.in(v);

        Player attacker = null;
        if (e instanceof EntityDamageByEntityEvent be) attacker = attackerOf(be.getDamager());
        boolean byOther = attacker != null && !attacker.equals(v);

        // players inside a mode can only fight players of the same duel/mode, and nobody else
        if (byOther) {
            boolean aIn = plugin.busy(attacker);
            if (vIn != aIn || (vIn && !sameContext(attacker, v))) {
                e.setCancelled(true);
                return;
            }
        }
        if (!vIn) return;

        if (plugin.ffa.isRespawning(v)) {
            e.setCancelled(true);
            return;
        }
        if (d != null && d.state != DuelManager.DState.FIGHT) {   // countdown / duel already over
            e.setCancelled(true);
            return;
        }
        if (byOther) lastHit.put(v.getUniqueId(), new Hit(attacker.getUniqueId(), System.currentTimeMillis()));

        // not lethal -> normal damage
        if (v.getHealth() - e.getFinalDamage() > 0) return;

        // a totem will save them (void / kill ignore totems)
        DamageCause cause = e.getCause();
        boolean hard = cause == DamageCause.VOID || cause == DamageCause.KILL || cause == DamageCause.SUICIDE;
        if (!hard && Util.hasTotem(v)) return;

        // lethal: no death screen, handle it ourselves
        e.setCancelled(true);
        Player killer = byOther ? attacker : recentAttacker(v);
        if (d != null) plugin.duels.onLethal(v);
        else plugin.ffa.onLethal(v, killer);
    }

    @EventHandler
    public void onPlace(EntityPlaceEvent e) {
        if (e.getEntity() instanceof EnderCrystal c && e.getPlayer() != null) {
            rememberCrystal(c, e.getPlayer());
        }
    }

    /** Safety net: if a player somehow really dies inside a mode, don't drop anything and recover. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        boolean duel = plugin.duels.inDuel(p);
        boolean ffa = plugin.ffa.in(p);
        if (!duel && !ffa) return;
        e.setKeepInventory(true);
        e.getDrops().clear();
        e.setKeepLevel(true);
        e.setDroppedExp(0);
        e.deathMessage(null);
        Player killer = p.getKiller();
        Bukkit.getScheduler().runTask(plugin, () -> {
            p.spigot().respawn();
            if (duel) plugin.duels.onLethal(p);
            else plugin.ffa.onLethal(p, killer);
        });
    }

    // ---------- join / quit ----------

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!plugin.states.has(p.getUniqueId())) return;
        // left over from a crash or a disconnect: give their stuff back
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && !plugin.busy(p)) plugin.states.restore(p);
        }, 2L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        plugin.duels.onQuit(p);
        plugin.ffa.onQuit(p);
        lastHit.remove(p.getUniqueId());
    }

    // ---------- movement / items / commands ----------

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        DuelManager.Duel d = plugin.duels.get(e.getPlayer());
        if (d == null || d.state != DuelManager.DState.COUNTDOWN) return;
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null) return;
        if (from.getX() != to.getX() || from.getZ() != to.getZ()) {
            Location fixed = from.clone();
            fixed.setYaw(to.getYaw());     // looking around is fine, walking is not
            fixed.setPitch(to.getPitch());
            e.setTo(fixed);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!plugin.busy(p) || isAdmin(p)) return;
        String msg = e.getMessage();
        String cmd = msg.startsWith("/") ? msg.substring(1) : msg;
        int sp = cmd.indexOf(' ');
        if (sp >= 0) cmd = cmd.substring(0, sp);
        cmd = cmd.toLowerCase(Locale.ROOT);
        for (String allowed : plugin.getConfig().getStringList("allowed-commands")) {
            if (allowed.equalsIgnoreCase(cmd)) return;
        }
        e.setCancelled(true);
        plugin.msg.send(p, "ffa.command-blocked");
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (plugin.busy(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && plugin.busy(p)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && plugin.busy(p)) e.setCancelled(true);
    }

    // ---------- world protection ----------

    @EventHandler(ignoreCancelled = true)
    public void onMobSpawn(CreatureSpawnEvent e) {
        if (!NATURAL_SPAWNS.contains(e.getSpawnReason())) return;
        Location l = e.getLocation();
        Arena a = plugin.arenas.duelAt(l);
        if (a != null && !a.mobSpawn) {
            e.setCancelled(true);
            return;
        }
        FfaArena f = plugin.arenas.ffaAt(l);
        if (f != null && !f.mobSpawn) e.setCancelled(true);
    }

    private boolean protectBlocks() {
        return plugin.getConfig().getBoolean("ffa.protect-blocks", true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        if (protectBlocks() && plugin.arenas.ffaAt(e.getLocation()) != null) e.blockList().clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (protectBlocks() && plugin.arenas.ffaAt(e.getBlock().getLocation()) != null) e.blockList().clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (protectBlocks() && plugin.ffa.in(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        if (protectBlocks() && plugin.ffa.in(e.getPlayer())) e.setCancelled(true);
    }
}
