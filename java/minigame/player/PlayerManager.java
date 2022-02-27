package minigame.player;

import minigame.Main;
import minigame.game.MinigameManager;
import minigame.io.InputOutputManager;
import minigame.util.GlobalSettings;
import minigame.util.Misc;
import net.md_5.bungee.api.chat.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class PlayerManager implements Listener {
    private static final HashMap<UUID, PlayerData> playerData = new HashMap<>();
    private static final HashMap<UUID, Scoreboard> playerBoard = new HashMap<>();

    public static void enable() {
        Bukkit.getServer().getPluginManager().registerEvents(new PlayerManager(), Main.getInstance());
    }

    /**
     * Updates and opens a Player's personal scoreboard
     * @param p Player to open
     */
    public static void updateScoreboard(Player p) {
        if(MinigameManager.inGame(p) || MinigameManager.inLobby(p)) {return;}
        playerBoard.computeIfAbsent(p.getUniqueId(), (k) -> {
            Scoreboard board = Bukkit.getServer().getScoreboardManager().getNewScoreboard();
            Objective obj = board.registerNewObjective(p.getName(), "foo",
                    Misc.color("&a&l>> &7"+GlobalSettings.getServerName()+" &a&l<<"));
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            obj.getScore(ChatColor.RESET.toString()).setScore(9);
            return board;
        });
        int idx = 8;

        Scoreboard board = playerBoard.get(p.getUniqueId());
        Objective obj = board.getObjective(p.getName());
        PlayerData pd = playerData.get(p.getUniqueId());

        String[] lastText = pd.getLastSignText();
        String[] newText = new String[] {
                Misc.color("&c&lTokens: &f")+pd.getTokens(),
                Misc.color("&f&lTickets: &f")+pd.getTickets(),
                Misc.color("&9&lOnline:   &f")+Bukkit.getOnlinePlayers().size()
        };

        if(lastText != null) {
            for (String text : lastText) {
                playerBoard.get(p.getUniqueId()).resetScores(text);
            }
        }

        for(String text : newText) {
            obj.getScore(text).setScore(idx--);
        }

        pd.setLastSignText(newText);

        p.setScoreboard(board);
    }

    protected static void setStartingInventory(Player p) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta bookMeta = (BookMeta) book.getItemMeta();
        List<BaseComponent[]> pages = bookMeta.spigot().getPages();

        ComponentBuilder builder = new ComponentBuilder("Welcome to "+GlobalSettings.getServerName()+"!\n\n");
        //builder.append("Minigame Teleport - Token Cost / Ticket Reward - Required Players\n\n");
        for(String game : MinigameManager.getAllMinigames()) {
            TextComponent line = new TextComponent(MinigameManager.getMinigameName(game));
            line.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/minigame " + game));
            line.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Play now!").create()));
            builder.append(line);
            builder.append(" - "+MinigameManager.getMinigameSettings(game).getTokenCost()+" tokens"/*+" / "+gameType.getTicketReward()+" - "+gameType.getRequiredPlayers()*/+"\n");
        }

        bookMeta.spigot().addPage(builder.create());
        //bookMeta.setPages(pages);
        bookMeta.setTitle("Minigame List");
        bookMeta.setAuthor(GlobalSettings.getServerName());
        book.setItemMeta(bookMeta);
        p.getInventory().setItem(0, book);
    }

    public static void refundTokens(Player p, int amt) {
        PlayerData pd = playerData.get(p.getUniqueId());
        if(!pd.isGamePassActive()) {
            pd.subtractTokens(-amt);
            p.sendMessage("You have been refunded " + amt + " tokens! New balance: " + pd.getTokens());
        }
    }

    public static void takeTokens(Player p, int amt) {
        PlayerData pd = playerData.get(p.getUniqueId());
        if(pd.isGamePassActive() && amt > 0) {
            p.sendMessage("You avoided paying "+amt+" tokens due to your "+ GlobalSettings.getGamePassName()+"!");
        } else {
            pd.subtractTokens(amt);
        }
    }

    public static boolean hasTokens(Player p, int amt) {
        PlayerData pd = playerData.get(p.getUniqueId());
        return pd.isGamePassActive() || amt < pd.getTokens();
    }

    public static void awardTickets(Player p, int amt) {
        PlayerData pd = playerData.get(p.getUniqueId());
        amt = (int)(amt * GlobalSettings.getTicketMultiplier());
        if(amt > 0) {
            pd.addTickets(amt);
            p.sendMessage("Congratulations! You won " + amt + " tickets! New balance: " + pd.getTickets());
        } else if(amt == 0) {
            p.sendMessage("You didn't win any tickets :( -- Better luck next time!");
        }
    }

    public static void setHighScore(UUID p, String gameType, Double amt) {
        playerData.get(p).setElo(gameType, amt);
    }

    public static double getHighScore(UUID p, String gameType) {
        return playerData.get(p).getElo(gameType);
    }

    public static double getHighScore(Player p, String gameType) {
        return playerData.get(p.getUniqueId()).getElo(gameType);
    }

    public static void changeHighScore(UUID p, String gameType, double change) {
        playerData.get(p).addElo(gameType, change);
    }

    public static void changeHighScore(Player p, String gameType, double change) {
        playerData.get(p.getUniqueId()).addElo(gameType, change);
    }

    public static boolean adminCommand(Player admin, Player victim, String field, int change, boolean relative) {
        PlayerData pd = playerData.get(victim.getUniqueId());
        int amt = change + (relative? pd.getTokens() : 0);
        switch(field) {
            case "tokens":
                pd.setTokens(amt);
                victim.sendMessage("Admin "+admin.getName()+" has set your token count to "+amt);
            break;

            case "tickets":
                pd.setTickets(amt);
                victim.sendMessage("Admin "+admin.getName()+" has set your ticket count to "+amt);
            break;

            case "checktokens":
                admin.sendMessage(victim.getName()+" has "+pd.getTokens()+" tokens.");
            break;

            case "checktickets":
                admin.sendMessage(victim.getName()+" has "+pd.getTickets()+" tickets.");
            break;

            default:
                return false;
        }
        if(!field.startsWith("check")) {
            admin.sendMessage("Successfully enacted command: "+victim.getName()+" "+field+" "+amt+" "+(relative?"change":"set"));}
            updateScoreboard(victim);
        return true;
    }

    public static void savePlayer(Player p) {
        InputOutputManager.savePlayer(p, playerData.get(p.getUniqueId()));
    }

    public static boolean loadPlayer(Player p) {
        PlayerData pd = new PlayerData();
        playerData.put(p.getUniqueId(), pd);
        int pastVisits = InputOutputManager.loadPlayer(p, pd);
        if(pastVisits == 0) {
            p.sendMessage(GlobalSettings.getFirstTimeMessage());
            int awardTokens = GlobalSettings.getFirstJoinTokenAward();
            pd.setTokens(awardTokens);
            p.sendMessage("You have been awarded "+awardTokens+" tokens for joining the server! Enjoy!");
            setStartingInventory(p);
        } else if(pastVisits > 0) {
            p.sendMessage(GlobalSettings.getWelcomeMessage());
            setStartingInventory(p);//TODO remove
        }
        return pastVisits >= 0;
    }

    /*
        Player Events
     */

    @EventHandler(priority = EventPriority.LOWEST)//First
    private void onPlayerJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if(!PlayerManager.loadPlayer(p)) {
            p.kickPlayer("Your profile could not be loaded by our server :(" +
                    " Please try again later or contact us at our website: "+GlobalSettings.getWebsiteAddress());
            return;
        }
        updateOnlineCount();
    }

    @EventHandler(priority = EventPriority.HIGHEST)//Last
    private void onPlayerQuit(PlayerQuitEvent e) {
        PlayerManager.savePlayer(e.getPlayer());
        updateOnlineCount();
    }

    @EventHandler
    private void onPlayerChat(AsyncPlayerChatEvent e) {
        try {
            playerData.get(e.getPlayer().getUniqueId()).incrementMsgCount();
        } catch(Exception ex) {/*If the PlayerData was nulled during this, we don't really care*/}
    }

    @EventHandler
    public void onFoodChange(FoodLevelChangeEvent e) {
        if(GlobalSettings.isHungerDisabled()) {
            e.setFoodLevel(20);
        }
    }

    private void updateOnlineCount() {
        for(Player p : Bukkit.getOnlinePlayers()) {
            PlayerManager.updateScoreboard(p);
        }

    }
}
