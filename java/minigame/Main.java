package minigame;

import lombok.Getter;
import minigame.game.Minigame;
import minigame.game.MinigameManager;
import minigame.io.InputOutputManager;
import minigame.player.PlayerManager;
import minigame.util.GlobalSettings;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class Main extends JavaPlugin
{
	@Getter
	private static JavaPlugin instance;

	public Main() {
		instance = this;
	}

	@Override
	public void onEnable() {
		this.saveDefaultConfig();
		setDefaults();
		this.getConfig().options().copyDefaults(true);
		this.saveConfig();
		InputOutputManager.enable();
		MinigameManager.findRegions();
		PlayerManager.enable();
		GlobalSettings.getMinigameWorld().setAutoSave(false);
	}
	
	@Override
	public void onLoad() {
		MinigameManager.registerFlags();
	}
	
	@Override
	public void onDisable() {

	}
	
	//Handles our plugin's command, /report
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (!(sender instanceof Player)) {return false;}
		Player p = (Player) sender;

		if(p.hasPermission("Minigame.Admin")) {
			switch (label.toLowerCase()) {
				case "killallgames":
					MinigameManager.killAllGames();
					break;

				case "tickets":
				case "tokens":
					Player victim = Bukkit.getPlayer(args[0]);
					if(victim == null) {//TODO for offline players
						p.sendMessage("That player is not currently online.");
						return true;
					}
					int amt = 0;
					boolean relative = true;
					if(args.length > 1) {
						amt = Integer.parseInt(args[2]);
						amt = args[1].equals("subtract") ? -amt : amt;
						relative = args[1].equals("add") || args[1].equals("subtract");
					} else {label = "check"+label;}
					return PlayerManager.adminCommand(p, victim, label, amt, relative);
			}
		}

		switch(label.toLowerCase()) {
			case "quit":
				Minigame toQuit = MinigameManager.getGame(p);
				if(toQuit != null) {
					toQuit.kickPlayer(p);
					p.sendMessage("Leaving "+toQuit.getSettings().getDisplayName()+"...");
				} else {
					p.sendMessage("You are not currently in a game!");
				}
				return true;

			case "minigame":
				try {
					p.teleport(MinigameManager.getViewerLocation(args[0]));
				} catch(Exception e) {
					p.sendMessage("Minigame "+args[0]+" not found.");
				}
			return true;
		}
		
		return false;
	}
	
	//Returns the given String from our plug-in's default ConfigFile
	private String getCfg(String dataToGet) {
		return this.getConfig().getString(dataToGet);
	}
	
	//Sets the configuration defaults for the end-user to set up our plug-in more easily
	//Currently set to my personal machine's defaults, but would not be at production.
	private void setDefaults() {
		/*this.getConfig().addDefault("store_in_database", false);
		this.getConfig().addDefault("backup_data", true);
		this.getConfig().addDefault("use_SSL", false);
		this.getConfig().addDefault("db_table_name", "reports");
		this.getConfig().addDefault("db_ip_address", "localhost");
		this.getConfig().addDefault("db_name", "test_database");
		this.getConfig().addDefault("db_username", "root");
		this.getConfig().addDefault("db_password", "password");*/
	}
}
