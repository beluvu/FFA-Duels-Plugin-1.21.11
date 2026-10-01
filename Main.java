package dev.beluvu.bduels;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class Main extends JavaPlugin {
    Msg msg;
    KitManager kits;
    ArenaManager arenas;
    StateStore states;
    DuelManager duels;
    FfaManager ffa;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        msg = new Msg(this);
        kits = new KitManager(this);
        arenas = new ArenaManager(this);
        states = new StateStore(this);
        duels = new DuelManager(this);
        ffa = new FfaManager(this);

        getServer().getPluginManager().registerEvents(new ModeListener(this), this);
        register("bduels", new BDuelsCommand(this));
        register("duels", new DuelsCommand(this));
        register("ffa", new FfaCommand(this));

        // anyone online with a leftover saved state (crash / reload) gets their stuff back
        Bukkit.getScheduler().runTask(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (states.has(p.getUniqueId()) && !busy(p)) states.restore(p);
            }
        });
    }

    @Override
    public void onDisable() {
        if (duels != null) duels.abortAll();
        if (ffa != null) ffa.leaveAll();
    }

    private void register(String name, TabExecutor ex) {
        PluginCommand c = getCommand(name);
        if (c != null) {
            c.setExecutor(ex);
            c.setTabCompleter(ex);
        }
    }

    boolean busy(Player p) {
        return duels.inDuel(p) || ffa.in(p);
    }

    void reloadAll() {
        reloadConfig();
        kits.load();
        arenas.load();
    }
}
