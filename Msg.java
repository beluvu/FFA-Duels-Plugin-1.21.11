package dev.beluvu.bduels;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

final class Msg {
    static final LegacyComponentSerializer LS = LegacyComponentSerializer.legacyAmpersand();
    private final Main plugin;

    Msg(Main plugin) {
        this.plugin = plugin;
    }

    /** Raw (still &-coded) text from messages.* with {placeholders} replaced. kv = key, value, key, value... */
    String raw(String key, String... kv) {
        String s = plugin.getConfig().getString("messages." + key, key);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            s = s.replace("{" + kv[i] + "}", kv[i + 1]);
        }
        return s;
    }

    Component c(String key, String... kv) {
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        return LS.deserialize(prefix + raw(key, kv));
    }

    void send(CommandSender to, String key, String... kv) {
        if (to != null) to.sendMessage(c(key, kv));
    }

    /** Admin/one-off messages written directly in code. */
    void text(CommandSender to, String text) {
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        if (to != null) to.sendMessage(LS.deserialize(prefix + text));
    }
}
