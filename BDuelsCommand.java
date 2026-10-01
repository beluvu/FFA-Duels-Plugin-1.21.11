package dev.beluvu.bduels;

import dev.beluvu.bduels.ArenaManager.Arena;
import dev.beluvu.bduels.ArenaManager.FfaArena;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class BDuelsCommand implements TabExecutor {
    private final Main plugin;

    BDuelsCommand(Main plugin) {
        this.plugin = plugin;
    }

    private void help(CommandSender s) {
        plugin.msg.text(s, "&e/bduels create kit <имя>&7 - кит из твоего инвентаря");
        plugin.msg.text(s, "&e/bduels create arena <имя>&7 - дуэльная арена из выделения WorldEdit");
        plugin.msg.text(s, "&e/bduels create ffa <режим>&7 - FFA арена из выделения (beast, nether...)");
        plugin.msg.text(s, "&e/bduels arena <имя> spawn <1|2>&7 - точки появления дуэли");
        plugin.msg.text(s, "&e/bduels arena <имя> mobspawn <on|off>");
        plugin.msg.text(s, "&e/bduels ffa <режим> addspawn|clearspawns");
        plugin.msg.text(s, "&e/bduels ffa <режим> kit <кит>");
        plugin.msg.text(s, "&e/bduels ffa <режим> mobspawn <on|off>");
        plugin.msg.text(s, "&e/bduels delete <kit|arena|ffa> <имя>");
        plugin.msg.text(s, "&e/bduels list&7, &e/bduels reload");
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!s.hasPermission("bduels.admin")) {
            plugin.msg.send(s, "no-permission");
            return true;
        }
        if (a.length == 0) {
            help(s);
            return true;
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "create" -> create(s, a);
            case "arena" -> arena(s, a);
            case "ffa" -> ffa(s, a);
            case "delete" -> delete(s, a);
            case "list" -> list(s);
            case "reload" -> {
                plugin.reloadAll();
                plugin.msg.text(s, "&aКонфиг, киты и арены перезагружены.");
            }
            default -> help(s);
        }
        return true;
    }

    // ---------- create ----------

    private void create(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) {
            plugin.msg.send(s, "players-only");
            return;
        }
        if (a.length < 3) {
            help(s);
            return;
        }
        String type = a[1].toLowerCase(Locale.ROOT);
        String name = a[2];
        if (!KitManager.validName(name)) {
            plugin.msg.text(p, "&cИмя: только латиница, цифры, _ и -.");
            return;
        }
        String key = KitManager.norm(name);

        switch (type) {
            case "kit" -> {
                plugin.kits.createFrom(p, key);
                plugin.msg.text(p, "&aКит &e" + key + "&a сохранён из твоего инвентаря.");
            }
            case "arena" -> {
                Bounds b = selection(p);
                if (b == null) return;
                Arena ar = plugin.arenas.duelArenas.computeIfAbsent(key, Arena::new);
                ar.bounds = b;
                plugin.arenas.save();
                plugin.msg.text(p, "&aАрена &e" + key + "&a создана. Теперь поставь точки: &f/bduels arena " + key + " spawn 1&a и &f2&a.");
            }
            case "ffa" -> {
                Bounds b = selection(p);
                if (b == null) return;
                FfaArena f = plugin.arenas.ffaArenas.computeIfAbsent(key, FfaArena::new);
                f.bounds = b;
                plugin.arenas.save();
                plugin.msg.text(p, "&aFFA &e" + key + "&a создан. Дальше: &f/bduels ffa " + key + " addspawn&a и &f/bduels ffa " + key + " kit <кит>");
            }
            default -> help(s);
        }
    }

    private Bounds selection(Player p) {
        if (!WeHook.available()) {
            plugin.msg.text(p, "&cНужен WorldEdit (или FAWE) на сервере.");
            return null;
        }
        Bounds b = WeHook.selection(p);
        if (b == null) {
            plugin.msg.text(p, "&cСначала выдели область WorldEdit (//pos1 и //pos2).");
        }
        return b;
    }

    // ---------- duel arena settings ----------

    private void arena(CommandSender s, String[] a) {
        if (a.length < 4) {
            help(s);
            return;
        }
        Arena ar = plugin.arenas.duelArenas.get(KitManager.norm(a[1]));
        if (ar == null) {
            plugin.msg.text(s, "&cТакой арены нет.");
            return;
        }
        switch (a[2].toLowerCase(Locale.ROOT)) {
            case "spawn" -> {
                if (!(s instanceof Player p)) {
                    plugin.msg.send(s, "players-only");
                    return;
                }
                if (a[3].equals("1")) ar.spawn1 = Spot.of(p.getLocation());
                else if (a[3].equals("2")) ar.spawn2 = Spot.of(p.getLocation());
                else {
                    plugin.msg.text(s, "&cУкажи 1 или 2.");
                    return;
                }
                plugin.arenas.save();
                plugin.msg.text(s, "&aТочка &e" + a[3] + "&a арены &e" + ar.name + "&a сохранена.");
            }
            case "mobspawn" -> {
                ar.mobSpawn = a[3].equalsIgnoreCase("on");
                plugin.arenas.save();
                plugin.msg.text(s, "&aМобы на арене &e" + ar.name + "&a: " + (ar.mobSpawn ? "включены" : "выключены"));
            }
            default -> help(s);
        }
    }

    // ---------- ffa settings ----------

    private void ffa(CommandSender s, String[] a) {
        if (a.length < 3) {
            help(s);
            return;
        }
        FfaArena f = plugin.arenas.ffaArenas.get(KitManager.norm(a[1]));
        if (f == null) {
            plugin.msg.text(s, "&cТакого FFA режима нет. Создай: &f/bduels create ffa <режим>");
            return;
        }
        switch (a[2].toLowerCase(Locale.ROOT)) {
            case "addspawn" -> {
                if (!(s instanceof Player p)) {
                    plugin.msg.send(s, "players-only");
                    return;
                }
                f.spawns.add(Spot.of(p.getLocation()));
                plugin.arenas.save();
                plugin.msg.text(s, "&aСпавн #" + f.spawns.size() + " добавлен в &e" + f.mode + "&a.");
            }
            case "clearspawns" -> {
                f.spawns.clear();
                plugin.arenas.save();
                plugin.msg.text(s, "&aСпавны &e" + f.mode + "&a очищены.");
            }
            case "kit" -> {
                if (a.length < 4) {
                    help(s);
                    return;
                }
                if (!plugin.kits.exists(a[3])) {
                    plugin.msg.send(s, "kit-not-found", "kit", a[3]);
                    return;
                }
                f.kit = KitManager.norm(a[3]);
                plugin.arenas.save();
                plugin.msg.text(s, "&aКит режима &e" + f.mode + "&a: &e" + f.kit);
            }
            case "mobspawn" -> {
                if (a.length < 4) {
                    help(s);
                    return;
                }
                f.mobSpawn = a[3].equalsIgnoreCase("on");
                plugin.arenas.save();
                plugin.msg.text(s, "&aМобы в &e" + f.mode + "&a: " + (f.mobSpawn ? "включены" : "выключены"));
            }
            default -> help(s);
        }
    }

    // ---------- delete / list ----------

    private void delete(CommandSender s, String[] a) {
        if (a.length < 3) {
            help(s);
            return;
        }
        String key = KitManager.norm(a[2]);
        boolean ok = switch (a[1].toLowerCase(Locale.ROOT)) {
            case "kit" -> plugin.kits.delete(key);
            case "arena" -> plugin.arenas.duelArenas.remove(key) != null;
            case "ffa" -> plugin.arenas.ffaArenas.remove(key) != null;
            default -> false;
        };
        if (ok) {
            plugin.arenas.save();
            plugin.msg.text(s, "&aУдалено: &e" + key);
        } else {
            plugin.msg.text(s, "&cНе найдено.");
        }
    }

    private void list(CommandSender s) {
        plugin.msg.text(s, "&7Киты: &f" + String.join(", ", plugin.kits.names()));
        StringBuilder sb = new StringBuilder();
        for (Arena ar : plugin.arenas.duelArenas.values()) {
            sb.append(ar.ready() ? "&a" : "&c").append(ar.name).append("&7, ");
        }
        plugin.msg.text(s, "&7Дуэль арены (&aготова&7/&cне готова&7): " + sb);
        sb.setLength(0);
        for (FfaArena f : plugin.arenas.ffaArenas.values()) {
            sb.append(f.ready() ? "&a" : "&c").append(f.mode).append("&7, ");
        }
        plugin.msg.text(s, "&7FFA режимы: " + sb);
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (!s.hasPermission("bduels.admin")) return out;
        if (a.length == 1) {
            out.addAll(List.of("create", "arena", "ffa", "delete", "list", "reload"));
        } else if (a.length == 2) {
            switch (a[0].toLowerCase(Locale.ROOT)) {
                case "create" -> out.addAll(List.of("kit", "arena", "ffa"));
                case "delete" -> out.addAll(List.of("kit", "arena", "ffa"));
                case "arena" -> out.addAll(plugin.arenas.duelArenas.keySet());
                case "ffa" -> out.addAll(plugin.arenas.ffaArenas.keySet());
                default -> {
                }
            }
        } else if (a.length == 3) {
            switch (a[0].toLowerCase(Locale.ROOT)) {
                case "arena" -> out.addAll(List.of("spawn", "mobspawn"));
                case "ffa" -> out.addAll(List.of("addspawn", "clearspawns", "kit", "mobspawn"));
                case "delete" -> {
                    switch (a[1].toLowerCase(Locale.ROOT)) {
                        case "kit" -> out.addAll(plugin.kits.names());
                        case "arena" -> out.addAll(plugin.arenas.duelArenas.keySet());
                        case "ffa" -> out.addAll(plugin.arenas.ffaArenas.keySet());
                        default -> {
                        }
                    }
                }
                default -> {
                }
            }
        } else if (a.length == 4) {
            String sub = a[2].toLowerCase(Locale.ROOT);
            if (a[0].equalsIgnoreCase("arena") && sub.equals("spawn")) out.addAll(List.of("1", "2"));
            else if (sub.equals("mobspawn")) out.addAll(List.of("on", "off"));
            else if (a[0].equalsIgnoreCase("ffa") && sub.equals("kit")) out.addAll(plugin.kits.names());
        }
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
