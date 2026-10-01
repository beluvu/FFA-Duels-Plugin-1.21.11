package dev.beluvu.bduels;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class FfaCommand implements TabExecutor {
    private final Main plugin;

    FfaCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!(s instanceof Player p)) {
            plugin.msg.send(s, "players-only");
            return true;
        }
        if (!p.hasPermission("bduels.ffa")) {
            plugin.msg.send(p, "no-permission");
            return true;
        }
        if (a.length == 0) {
            plugin.msg.send(p, "ffa.list", "modes", String.join(", ", plugin.arenas.ffaArenas.keySet()));
        } else if (a[0].equalsIgnoreCase("leave")) {
            plugin.ffa.leave(p);
        } else {
            plugin.ffa.join(p, a[0]);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            out.addAll(plugin.arenas.ffaArenas.keySet());
            out.add("leave");
            String last = a[0].toLowerCase(Locale.ROOT);
            out.removeIf(x -> !x.startsWith(last));
        }
        return out;
    }
}
