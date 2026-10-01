package dev.beluvu.bduels;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class KitManager {
    private final Main plugin;
    private final File file;
    private final Map<String, ItemStack[]> kits = new LinkedHashMap<>();

    KitManager(Main plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "kits.yml");
        load();
    }

    static String norm(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    static boolean validName(String name) {
        return name.matches("[A-Za-z0-9_-]{1,32}");
    }

    void load() {
        kits.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String key : y.getKeys(false)) {
            ItemStack[] arr = new ItemStack[41];
            List<?> list = y.getList(key);
            if (list != null) {
                for (int i = 0; i < list.size() && i < arr.length; i++) {
                    if (list.get(i) instanceof ItemStack is) arr[i] = is;
                }
            }
            kits.put(norm(key), arr);
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<String, ItemStack[]> e : kits.entrySet()) {
            y.set(e.getKey(), Arrays.asList(e.getValue()));
        }
        try {
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save kits.yml: " + ex.getMessage());
        }
    }

    boolean exists(String name) {
        return kits.containsKey(norm(name));
    }

    List<String> names() {
        return new ArrayList<>(kits.keySet());
    }

    /** Create or overwrite a kit from the player's current inventory (hotbar, armor, offhand). */
    void createFrom(Player p, String name) {
        kits.put(norm(name), Util.cloneAll(p.getInventory().getContents()));
        save();
    }

    boolean delete(String name) {
        boolean removed = kits.remove(norm(name)) != null;
        if (removed) save();
        return removed;
    }

    void give(Player p, String name) {
        ItemStack[] kit = kits.get(norm(name));
        if (kit == null) return;
        p.getInventory().clear();
        p.getInventory().setContents(Util.cloneAll(kit));
    }

    /** "Kit: Netherite Sword, Totem x2, ..." with item hover (enchants visible). */
    Component describe(String name) {
        ItemStack[] kit = kits.get(norm(name));
        List<Component> parts = new ArrayList<>();
        if (kit != null) {
            for (ItemStack it : kit) {
                if (it == null || it.getType().isAir()) continue;
                Component c = Component.translatable(it.getType().translationKey()).color(NamedTextColor.WHITE);
                if (it.getAmount() > 1) {
                    c = c.append(Component.text(" x" + it.getAmount(), NamedTextColor.GRAY));
                }
                parts.add(c.hoverEvent(it.asHoverEvent()));
            }
        }
        return Component.text("Кит ", NamedTextColor.GRAY)
                .append(Component.text(norm(name), NamedTextColor.YELLOW))
                .append(Component.text(": ", NamedTextColor.GRAY))
                .append(Component.join(JoinConfiguration.commas(true), parts));
    }
}
