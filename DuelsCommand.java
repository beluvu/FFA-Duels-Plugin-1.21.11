package dev.beluvu.bduels;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class DuelsCommand implements TabExecutor {
    private final Main plugin;

    DuelsCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!(s instanceof Player p)) {
            plugin.msg.send(s, "players-only");
            return true;
        }
        if (!p.hasPermission("bduels.duels")) {
            plugin.msg.send(p, "no-permission");
            return true;
        }
        if (a.length == 2 && a[0].equalsIgnoreCase("accept")) {
            plugin.duels.accept(p, a[1]);
        } else if (a.length == 2 && a[0].equalsIgnoreCase("decline")) {
            plugin.duels.decline(p, a[1]);
        } else if (a.length == 3 && a[1].equalsIgnoreCase("kit")) {
            plugin.duels.invite(p, Bukkit.getPlayerExact(a[0]), a[2]);
        } else {
            plugin.msg.send(p, "duel.usage");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            out.add("accept");
            out.add("decline");
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        } else if (a.length == 2) {
            if (a[0].equalsIgnoreCase("accept") || a[0].equalsIgnoreCase("decline")) {
                for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            } else {
                out.add("kit");
            }
        } else if (a.length == 3 && a[1].equalsIgnoreCase("kit")) {
            out.addAll(plugin.kits.names());
        }
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
