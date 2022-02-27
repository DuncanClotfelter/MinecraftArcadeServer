package minigame.io;

import lombok.Getter;
import minigame.Main;
import minigame.player.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class InputOutputManager {
    @Getter private static InputOutputStrategy output;

    public static void enable() {//TODO with settings
        try {
            output = new SQLInputOutputStrategy();
        } catch(Exception e) {
            e.printStackTrace();
            Main.getInstance().getLogger().severe("CRITICAL: Unable to instantiate plugin I/O! Disabling plugin.");
            Bukkit.getPluginManager().disablePlugin(Main.getInstance());
        }
    }

    public static void savePlayer(Player p, PlayerData pd) {
        output.savePlayer(p, pd);
    }

    public static int loadPlayer(Player p, PlayerData pd) {
        return output.loadPlayer(p, pd);
    }
}
