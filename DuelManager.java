package dev.beluvu.bduels;

import dev.beluvu.bduels.ArenaManager.Arena;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

final class DuelManager {

    enum DState { COUNTDOWN, FIGHT, ENDED }

    static final class Duel {
        final UUID a;
        final UUID b;
        final String an;
        final String bn;
        final Arena arena;
        final String kit;
        DState state = DState.COUNTDOWN;
        final Set<UUID> inside = new HashSet<>();

        Duel(Player a, Player b, Arena arena, String kit) {
            this.a = a.getUniqueId();
            this.b = b.getUniqueId();
            this.an = a.getName();
            this.bn = b.getName();
            this.arena = arena;
            this.kit = kit;
            inside.add(this.a);
            inside.add(this.b);
        }
    }

    private record Invite(UUID from, String kit, long expires) {
    }

    private final Main plugin;
    private final Map<UUID, Invite> invites = new HashMap<>();      // key = invited player
    private final Map<UUID, Duel> byPlayer = new HashMap<>();
    private final Set<String> busyArenas = new HashSet<>();

    DuelManager(Main plugin) {
        this.plugin = plugin;
    }

    // ---------- queries ----------

    boolean inDuel(Player p) {
        return byPlayer.containsKey(p.getUniqueId());
    }

    Duel get(Player p) {
        return byPlayer.get(p.getUniqueId());
    }

    boolean sameDuel(Player p, Player q) {
        Duel d = byPlayer.get(p.getUniqueId());
        return d != null && d == byPlayer.get(q.getUniqueId());
    }

    void clearInvites(UUID id) {
        invites.remove(id);
    }

    // ---------- invites ----------

    void invite(Player from, Player to, String kitName) {
        String kit = KitManager.norm(kitName);
        if (!plugin.kits.exists(kit)) {
            plugin.msg.send(from, "kit-not-found", "kit", kit);
            return;
        }
        if (to == null) {
            plugin.msg.send(from, "player-offline");
            return;
        }
        if (to.equals(from)) {
            plugin.msg.send(from, "duel.self");
            return;
        }
        if (plugin.busy(from)) {
            plugin.msg.send(from, "duel.you-busy");
            return;
        }
        if (plugin.busy(to)) {
            plugin.msg.send(from, "duel.target-busy");
            return;
        }

        long timeout = plugin.getConfig().getLong("duel.invite-timeout", 30);
        invites.put(to.getUniqueId(), new Invite(from.getUniqueId(), kit, System.currentTimeMillis() + timeout * 1000L));
        plugin.msg.send(from, "duel.sent", "player", to.getName());

        Component accept = Component.text("[✔ Принять]", NamedTextColor.GREEN, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/duels accept " + from.getName()))
                .hoverEvent(HoverEvent.showText(Component.text("Принять дуэль")));
        Component decline = Component.text("[✘ Отклонить]", NamedTextColor.RED, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/duels decline " + from.getName()))
                .hoverEvent(HoverEvent.showText(Component.text("Отклонить дуэль")));

        to.sendMessage(plugin.msg.c("duel.invite", "player", from.getName()));
        to.sendMessage(plugin.kits.describe(kit));
        to.sendMessage(accept.append(Component.text("  ")).append(decline));
    }

    private Invite takeInvite(Player to, String fromName) {
        Invite inv = invites.get(to.getUniqueId());
        Player from = Bukkit.getPlayerExact(fromName);
        if (inv == null || from == null || !inv.from().equals(from.getUniqueId())
                || System.currentTimeMillis() > inv.expires()) {
            invites.remove(to.getUniqueId());
            return null;
        }
        invites.remove(to.getUniqueId());
        return inv;
    }

    void accept(Player to, String fromName) {
        Invite inv = takeInvite(to, fromName);
        if (inv == null) {
            plugin.msg.send(to, "duel.no-invite");
            return;
        }
        Player from = Bukkit.getPlayer(inv.from());
        if (from == null) {
            plugin.msg.send(to, "duel.no-invite");
            return;
        }
        if (plugin.busy(from) || plugin.busy(to)) {
            plugin.msg.send(to, "duel.target-busy");
            return;
        }
        if (!plugin.kits.exists(inv.kit())) {
            plugin.msg.send(to, "kit-not-found", "kit", inv.kit());
            return;
        }
        start(from, to, inv.kit());
    }

    void decline(Player to, String fromName) {
        Invite inv = takeInvite(to, fromName);
        if (inv == null) {
            plugin.msg.send(to, "duel.no-invite");
            return;
        }
        plugin.msg.send(to, "duel.decline-you");
        plugin.msg.send(Bukkit.getPlayer(inv.from()), "duel.declined", "player", to.getName());
    }

    // ---------- duel lifecycle ----------

    private void start(Player a, Player b, String kit) {
        List<Arena> free = new ArrayList<>();
        for (Arena ar : plugin.arenas.duelArenas.values()) {
            if (ar.ready() && !busyArenas.contains(ar.name) && Bukkit.getWorld(ar.bounds.world()) != null) {
                free.add(ar);
            }
        }
        if (free.isEmpty()) {
            plugin.msg.send(a, "duel.no-arena");
            plugin.msg.send(b, "duel.no-arena");
            return;
        }
        Arena arena = free.get(ThreadLocalRandom.current().nextInt(free.size()));
        Location l1 = arena.spawn1.at(arena.bounds.world());
        Location l2 = arena.spawn2.at(arena.bounds.world());
        if (l1 == null || l2 == null) {
            plugin.msg.send(a, "duel.no-arena");
            plugin.msg.send(b, "duel.no-arena");
            return;
        }

        busyArenas.add(arena.name);
        Duel d = new Duel(a, b, arena, kit);
        byPlayer.put(d.a, d);
        byPlayer.put(d.b, d);

        for (Player p : List.of(a, b)) {
            plugin.states.enter(p);      // remember location, inventory, health...
            Util.reset(p);
        }
        a.teleport(l1);
        b.teleport(l2);
        plugin.kits.give(a, kit);
        plugin.kits.give(b, kit);

        runCountdown(d);
    }

    private void runCountdown(Duel d) {
        List<String> steps = plugin.getConfig().getStringList("duel.countdown");
        String go = plugin.getConfig().getString("duel.go", "&aGO!");
        int[] idx = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, (BukkitTask t) -> {
            if (d.state != DState.COUNTDOWN) {
                t.cancel();
                return;
            }
            Player pa = Bukkit.getPlayer(d.a);
            Player pb = Bukkit.getPlayer(d.b);
            if (idx[0] < steps.size()) {
                Component text = Msg.LS.deserialize(steps.get(idx[0]));
                idx[0]++;
                for (Player p : new Player[]{pa, pb}) {
                    if (p == null) continue;
                    p.showTitle(Title.title(text, Component.empty(),
                            Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100))));
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                }
            } else {
                d.state = DState.FIGHT;
                Component text = Msg.LS.deserialize(go);
                for (Player p : new Player[]{pa, pb}) {
                    if (p == null) continue;
                    p.showTitle(Title.title(text, Component.empty(),
                            Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(200))));
                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                }
                t.cancel();
            }
        }, 0L, 20L);
    }

    /** Called when a duel participant would take lethal damage during the fight. */
    void onLethal(Player victim) {
        Duel d = get(victim);
        if (d == null || d.state != DState.FIGHT) return;
        UUID loser = victim.getUniqueId();
        UUID winner = d.a.equals(loser) ? d.b : d.a;
        end(d, winner, loser, false);
    }

    void onQuit(Player p) {
        invites.remove(p.getUniqueId());
        Duel d = get(p);
        if (d == null) return;
        if (d.state == DState.ENDED) {
            release(d, p.getUniqueId());
            return;
        }
        UUID other = d.a.equals(p.getUniqueId()) ? d.b : d.a;
        end(d, other, p.getUniqueId(), true);
    }

    private void end(Duel d, UUID winner, UUID loser, boolean quit) {
        d.state = DState.ENDED;
        String wn = d.a.equals(winner) ? d.an : d.bn;
        String ln = d.a.equals(loser) ? d.an : d.bn;
        String key = quit ? "duel.quit-win" : "duel.result";
        Player w = Bukkit.getPlayer(winner);
        Player l = Bukkit.getPlayer(loser);
        plugin.msg.send(w, key, "winner", wn, "loser", ln);
        plugin.msg.send(l, key, "winner", wn, "loser", ln);

        release(d, loser);   // loser goes home right away

        if (w == null) {
            release(d, winner);
            return;
        }
        w.playSound(w.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        long delay = plugin.getConfig().getLong("duel.end-delay", 3) * 20L;
        Bukkit.getScheduler().runTaskLater(plugin, () -> release(d, winner), delay);
    }

    /** Send one participant home (idempotent). Frees the arena once everyone is out. */
    private void release(Duel d, UUID id) {
        if (!d.inside.remove(id)) return;
        byPlayer.remove(id, d);
        Player p = Bukkit.getPlayer(id);
        if (p != null) plugin.states.restore(p);   // if offline, the saved file restores them on join
        if (d.inside.isEmpty()) busyArenas.remove(d.arena.name);
    }

    /** Server shutting down / reloading: put everyone back immediately. */
    void abortAll() {
        for (Duel d : new HashSet<>(byPlayer.values())) {
            d.state = DState.ENDED;
            for (UUID id : new ArrayList<>(d.inside)) {
                release(d, id);
            }
        }
        invites.clear();
    }
}
