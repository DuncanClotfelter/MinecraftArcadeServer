package minigame.player;

import minigame.Main;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;

public class PlayerEventListener implements Listener {
    public PlayerEventListener() {
        Bukkit.getServer().getPluginManager().registerEvents(this, Main.getInstance());
    }
}
