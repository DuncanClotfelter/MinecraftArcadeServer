package minigame.game;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import lombok.Getter;
import lombok.NonNull;
import minigame.Main;
import minigame.io.output.record.GameRecord;
import minigame.player.PlayerManager;
import minigame.util.AdjustedLocation;
import minigame.util.GlobalSettings;
import minigame.util.MinigameTeam;
import minigame.util.Misc;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;

public abstract class Minigame implements Listener {
    private static int minigameCounter = 0;

    @Getter private final MinigameSettings settings;
    @Getter private final World world;
    @Getter private final String regionID;
    @Getter private final List<MinigameTeam> teams;
    private final Location exit;

    @Getter private final int minigameID;
    @Getter private final GameRecord gameRecord;
    @Getter private final ProtectedRegion gameArea;

    @Getter private final Instant gameStart;
    @Getter private final int startingPlayers;
    @Getter private int playersRemaining;

    private final HashMap<UUID, ItemStack[]> oldInventories = new HashMap<>();
    private final Map<Location, String> setBlocks = new HashMap<>();//Maps pasted Block Locations to a given value
    private final EditSession editSession;
    private final HashMap<String, Clipboard> models;
    @Getter private final Location baseLoc; //The smallest x/y/z the game area is located at
    private final int[] scale = {1, 1, 1}; //x/y/z multiplier on pasted models
    private final ArrayList<BukkitTask> timers = new ArrayList<>();
    private AffineTransform transform;
    private final Scoreboard scoreboard;
    private boolean gameOver = false;

    public Minigame(MinigameSettings settings, World world, String regionID, List<MinigameTeam> teams, Location exit) {
        this.settings = settings;
        this.world = world;
        this.regionID = regionID;
        this.teams = teams;
        this.exit = exit;
        this.minigameID = minigameCounter++;
        this.gameRecord = new GameRecord(this, this.teams);
        this.gameArea = MinigameManager.getRegion(this.regionID);

        System.out.println("Initializing minigame "+this.settings.name());

        //Find the minimum point to place all blocks on
        BlockVector3 min = MinigameManager.getRegion(this.regionID).getMinimumPoint();
        this.baseLoc = new Location(this.world, min.getX(), min.getY(), min.getZ());

        //Load Schematics
        editSession = WorldEdit.getInstance().getEditSessionFactory().getEditSession(BukkitAdapter.adapt(this.world), -1);
        models = new HashMap<>(this.settings.getSchemLength());
        prepareSchematics();

        //Manage Players
        scoreboard = Bukkit.getServer().getScoreboardManager().getNewScoreboard();
        setupScoreboard();

        int playerCount = 0;
        for(MinigameTeam team : this.teams) {
            for(Player p : team.getPlayers()) {
                playerCount++;
                joinGame(p);
            }
        }
        this.startingPlayers = playerCount;
        this.playersRemaining = startingPlayers;

        //Register this as an event listener
        Bukkit.getServer().getPluginManager().registerEvents(this, Main.getInstance());

        this.gameStart = Instant.now();
    }

    /**
     * Calculates the scale multiplier, loads all subclass schematics, and then loads, stores, and pastes the main schematic.
     */
    private void prepareSchematics() {
        try {
            //Load the additional models for individual game use later
            ClipboardFormat format;
            for(int i = 0; i < settings.getSchemLength(); i++) {
                format = ClipboardFormats.findByFile(new File(settings.getSchemDir(i)));
                try(ClipboardReader reader = format.getReader(new FileInputStream(settings.getSchemDir(i)))) {
                    models.put(settings.getSchemName(i), reader.read());
                }
            }

            if(!settings.hasMainSchem()) {return;}

            Clipboard clipboard;
            format = ClipboardFormats.findByFile(new File(settings.getSchemDir(-1)));
            try(ClipboardReader reader = format.getReader(new FileInputStream(settings.getSchemDir(-1)))) {
                clipboard = reader.read();
            }

            //Find out the maximum scale proportions for the main schematic in this region
            BlockVector3 regionSize = MinigameManager.getRegion(regionID).getMaximumPoint()
                                        .subtract(MinigameManager.getRegion(regionID).getMinimumPoint())
                                        .add(BlockVector3.at(1, 1, 1));
            BlockVector3 schemSize = clipboard.getDimensions();
            int maxScale = 100; //Arbitrary max of 100x block multiplier
            maxScale = Math.min(maxScale, settings.getScaleDirs()[0]? regionSize.getX() / schemSize.getX() : maxScale);
            maxScale = Math.min(maxScale, settings.getScaleDirs()[1]? regionSize.getY() / schemSize.getY() : maxScale);
            maxScale = Math.min(maxScale, settings.getScaleDirs()[2]? regionSize.getZ() / schemSize.getZ() : maxScale);
            for(int i = 0; i < 3; i++) {
                scale[i] = settings.getScaleDirs()[i] ? maxScale : 1;
            }
            resetTransform();

            //Paste the main model now
            paste(clipboard, adj(0, 0, 0), null);
        } catch(Exception e) {
            abort(e);
        }
    }

    /**
     * Pastes a loaded Clipboard at the given (BlockVector3) relative location from the base
     * @param clipboard Clipboard, most commonly obtained from clipboards.get
     * @param adj Relative Location from the Base Location. E.g, (1, 0, 0) is one x unit to the "right"
     * @param contents Maps each block's coordinates to this Value for later processing onClick
     */
    private void paste(@NonNull Clipboard clipboard, AdjustedLocation adj, @Nullable String contents) {
        try {
            ClipboardHolder holder = new ClipboardHolder(clipboard);
            holder.setTransform(transform);
            for(int x = 0; x < scale[0]; x++) {
                for(int y = 0; y < scale[1]; y++) {
                    for(int z = 0; z < scale[2]; z++) {
                        Operation operation = holder
                                .createPaste(editSession)
                                .to(getVector(adj.add(x, y, z)))
                                .ignoreAirBlocks(false)
                                .build();
                        Operations.complete(operation);
                        if(contents != null) {setBlocks.put(adj.add(x, y, z), contents);}
                    }
                }
            }
            editSession.flushSession();
        } catch(Exception e) {
            abort(e);
        }
    }

    protected void undo() {
        editSession.undo(editSession);
    }

    public AffineTransform getTransform() {
        return transform;
    }

    public void setTransform(AffineTransform affineTransform) {transform = affineTransform;}

    public void resetTransform() {
        transform = (new AffineTransform()).scale(scale[0], scale[1], scale[2]);
    }

    /*
        Player Management functions
     */

    protected void changeTeam(Player p, MinigameTeam dest) {
        for(MinigameTeam team : teams) {
            team.getPlayers().remove(p);
        }
        gameRecord.changeTeam(p, dest.getName());
    }

    /**
     * Teleports Players back to to the viewing location after their game is finished (or they are kicked). Null for no teleport.
     * @param p Player to teleport
     */
    private void toViewerLocation(Player p) {
        if(exit != null) {
            p.teleport(exit);
        }
    }

    /**
     * Send our Player "Minigame Message" in settings on Minigame start. Message can be set to NULL in settings for no message.
     * @param p Player to message
     */
    private void welcomePlayer(@NotNull Player p) {
        if(settings.getMinigameMessage() != null) {
            p.sendMessage(settings.getMinigameMessage());
        }
    }

    /**
     * Deduct this minigame's token cost per stated in settings. Cost can be set to 0 for no action.
     * @param p Player to take tokens from
     */
    private void deductTokens(@NotNull Player p) {
        PlayerManager.takeTokens(p, settings.getTokenCost());
    }

    /**
     * Adds the amount of tickets per stated in settings; overrideable to allow for custom payouts.
     */
    protected int getTicketsAwarded() {
        if(settings.getTicketReward() >= 0) {
            return settings.getTicketReward();
        } else {
            return (int)(startingPlayers * settings.getTokenCost() * GlobalSettings.getTicketTokenRatio());
        }
    }

    private void awardTickets(@NotNull Player p) {
        PlayerManager.awardTickets(p, getTicketsAwarded());
    }

    protected void awardLoserTickets(Player p) {/*Override*/}

    /**
     * Adds a player AFTER the game has already started
     * @param p Player to add
     * @return Returns FALSE if all Teams were already full (the Player wasn't added), else TRUE
     */
    public boolean addLatePlayer(Player p) {
        MinigameTeam smallest = null;
        for(MinigameTeam team : teams) {
            if(team.getPlayers().size() < settings.getMinTeamSize()) {
                if(smallest == null || smallest.getPlayers().size() > team.getPlayers().size()) {
                    smallest = team;
                }
            }
        }
        if(smallest != null) {
            smallest.getPlayers().add(p);
            joinGame(p);
            return true;
        }
        return false;
    }

    /**
     * Adds a player AFTER the game has already started.
     * @param p Player to add
     * @param teamName Team name to search for
     * @return Returns FALSE if this team name does not exist, or if that team is already full.
     */
    public boolean addLatePlayer(Player p, String teamName) {
        if(settings.isTeamRebalanced()) {return addLatePlayer(p);}

        for(MinigameTeam team : teams) {
            if(team.getName().equalsIgnoreCase(teamName) && team.getPlayers().size() < settings.getMaxTeamSize()) {
                team.getPlayers().add(p);
                joinGame(p);
                return true;
            }
        }
        return false;
    }

    /**
     * Opens the scoreboard for all players
     */
    protected void setupScoreboard() {
        Objective obj = scoreboard.registerNewObjective("general", "dummy", settings.getDisplayName());
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        for(MinigameTeam minigameTeam : teams) {
            Team team = scoreboard.registerNewTeam(minigameTeam.getName());
            team.setDisplayName(minigameTeam.getName());
            //team.setColor(ChatColor.valueOf(ChatColor.getLastColors(minigameTeam.getName())));
            team.setCanSeeFriendlyInvisibles(settings.isFriendlyInvisibleVisible());
            team.setAllowFriendlyFire(settings.isFriendlyFireAllowed());
            minigameTeam.setScoreboardTeam(team);

            for(Player p : minigameTeam.getPlayers()) {
                team.addEntry(p.getDisplayName());
                obj.getScore(p.getDisplayName()).setScore((int)PlayerManager.getHighScore(p, getName()));
            }
        }
    }

    /**
     * Return the Player's scoreboard to the default
     */
    protected void closeScoreboard(Player p) {
        System.out.println("Resetting scoreboard");
        PlayerManager.updateScoreboard(p);
    }

    /*
        Spatial Functions to correct for minigame scaling
     */

    /**
     * Converts a Location to a WorldEdit BlockVector3
     * @param loc Location to convert
     * @return Equivalent BlockVector3
     */
    protected BlockVector3 getVector(@NotNull Location loc) {
        return BlockVector3.at(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    protected Location getLocation(@NotNull BlockVector3 loc) {
        return new Location(GlobalSettings.getMinigameWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    /**
     * Shortcut for the triple for loop needed to check all Blocks that have been scaled
     * @param f Function to utilize
     * @return Returns true if p returns true for any of the given coordinates
     */
    private String allBlocks(Function<int[], String> f) {
        for(int x = 0; x < scale[0]; x++) {
            for(int y = 0; y < scale[1]; y++) {
                for(int z = 0; z < scale[2]; z++) {
                    String result = f.apply(new int[] {x, y, z});
                    if(result != null) {
                        return result;
                    }
                }
            }
        }
        return null;
    }

    //TODO a better way to do both of these? Theoretically should be able to inverse the schematic, right?
    //new BlockArrayClipboard(new CuboidRegion(getGameArea().getMinimumPoint(), getGameArea().getMaximumPoint()));
    protected void removeModel(/*String modelID, */AdjustedLocation adj) {
        allBlocks((i) -> {
            Location toRemove = adj.add(i);
            setBlocks.remove(toRemove);
            toRemove.getBlock().setType(Material.AIR);
            return null;
        });
    }

    protected String getModelAt(AdjustedLocation adj) {
        return allBlocks((i) -> setBlocks.get(adj.add(i)));
    }

    //TODO probably just make paste do this by default and rename it -- no added functionality I know of from this
    protected void addModel(String model, AdjustedLocation adj) {
        paste(models.get(model), adj, model);
    }

    protected void replaceModel(String model, AdjustedLocation adj) {
        //removeModel(adj); //TODO currently unnecessary
        addModel(model, adj);
    }

    protected boolean hasModelAt(AdjustedLocation adj) {
        return getModelAt(adj) != null;
    }

    protected boolean hasModelAt(@NotNull String modelID, AdjustedLocation adj) {
        return modelID.equals(getModelAt(adj));
    }

    //Allows subclasses to change real Locations given by the Listener to Relative coordinates that are easy to work with
    protected int[] coords(Location loc) {
        return new int[] {
                (loc.getBlockX()-baseLoc.getBlockX())/scale[0],
                (loc.getBlockY()-baseLoc.getBlockY())/scale[1],
                (loc.getBlockZ()-baseLoc.getBlockZ())/scale[2]
        };
    }

    //Allows subclasses to correctly Adjust their relative Locations. Uses the Minigame's current world and adds the Minigame's base Location.
    public AdjustedLocation adj(int x, int y, int z) {
        return new AdjustedLocation(world, baseLoc, scale, x, y, z);
    }
    public AdjustedLocation adj(Player p) {return new AdjustedLocation(p);}
    public AdjustedLocation adj(Block b) {return new AdjustedLocation(b);}
    public AdjustedLocation adj(int[] i) {return adj(i[0], i[1], i[2]);}
    public AdjustedLocation adj(Location loc) {return new AdjustedLocation(loc);}

    /*
        Shortcut Convenience Methods for Subclasses
     */
    public boolean inGameArea(Location loc) {
        return gameArea.contains(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    /**
     * Loops through neighboring blocks to the given location and returns the direction to the first block within
     * the gameArea that it finds
     * @param loc Location to search from
     * @return Vector to add to the original Location
     */
    protected Location getNeighbor(Location loc) {
        Location toReturn;
        for(int axis = 0; axis < 3; axis++) {
            toReturn = getNeighbor(loc, axis);
            if(toReturn != null) {return toReturn;}
        }
        return null;
    }

    protected Location getNeighbor(Location loc, int axis) {
        int x, y, z;
        for (int adj = -1; adj <= 1; adj += 2) {
            x = loc.getBlockX() + adj * (axis == 0 ? 1 : 0);
            y = loc.getBlockY() + adj * (axis == 1 ? 1 : 0);
            z = loc.getBlockZ() + adj * (axis == 2 ? 1 : 0);
            if (gameArea.contains(x, y, z)) {
                return new Location(loc.getWorld(), x, y, z);
            }
        }
        return null;
    }

    protected boolean inGame(Player p) {
        for(MinigameTeam team : teams) {
            if(team.getPlayers().contains(p)) {return true;}
        }
        return false;
    }

    public String getName() {
        return settings.name();
    }

    protected MinigameTeam getTeam(Player p) {
        for(MinigameTeam team : teams) {
            if(team.getPlayers().contains(p)) {return team;}
        }
        return null;
    }

    protected void messageAll(String msg) {
        for(MinigameTeam team : teams) {
            for(Player p : team.getPlayers()) {
                p.sendMessage(msg);
            }
        }
    }

    protected Clipboard getModel(String modelName) {
        return models.get(modelName);
    }

    protected int getLongestSide() {
        BlockVector3 vector = gameArea.getMaximumPoint().subtract(gameArea.getMinimumPoint()).add(1, 1, 1);
        return Math.max(vector.getX(), Math.max(vector.getY(), vector.getZ()));
    }

    public int getXLength() {
        BlockVector3 vector = gameArea.getMaximumPoint().subtract(gameArea.getMinimumPoint()).add(1, 1, 1);
        return vector.getX();
    }

    public int getYLength() {
        BlockVector3 vector = gameArea.getMaximumPoint().subtract(gameArea.getMinimumPoint()).add(1, 1, 1);
        return vector.getY();
    }

    public int getZLength() {
        BlockVector3 vector = gameArea.getMaximumPoint().subtract(gameArea.getMinimumPoint()).add(1, 1, 1);
        return vector.getZ();
    }

    /*
        Minigame Rounds (logging & timers)
     */
    protected void startTimer(int secondsDuration, int notifyFrequency, Function<Integer, String> getMessage, Function<Integer, List<MinigameTeam>> getRecipient) {
        timers.add(new BukkitRunnable() {
            int remaining = secondsDuration;

            @Override
            public void run() {
                List<MinigameTeam> toMsg = getRecipient.apply(remaining);
                if(toMsg == null) {cancel(); return;}
                String toSend = getMessage.apply(remaining);
                if(secondsDuration % notifyFrequency == 0 && toSend != null) {
                    for (MinigameTeam team : toMsg) {
                        for (Player p : team.getPlayers()) {
                            p.sendMessage(toSend);
                        }
                    }
                }
                if(remaining == 0) {
                    remaining = secondsDuration;
                } else {
                    remaining--;
                }
            }
        }.runTaskTimer(Main.getInstance(), 0, 20));
    }

    protected void stopTimer(int idx) {
        if(timers.size() <= idx) {return;}
        timers.remove(idx).cancel();
    }

    /*
        Integral Minigame Object management & destruction
     */

    protected void abort(Exception e) {
        for(MinigameTeam team : teams) {
            for(Player p : team.getPlayers()) {
                if(p == null) {continue;}
                p.sendMessage("Sorry, this minigame ended unexpectedly. Please contact the administrator.");
                PlayerManager.refundTokens(p, settings.getTokenCost());
            }
        }
        Bukkit.getLogger().severe("Minigame "+getName()+" ended unexpectedly: "+ Misc.getError(e));
        exit();
    }

    /**
     * Kick a Player from this Minigame prematurely. Will usually be used when a Player dies or uses /quit
     * @param p Player to kick
     */
    public void kickPlayer(Player p) {
        playersRemaining--;
        awardLoserTickets(p);
        for(MinigameTeam team : teams) {
            team.getPlayers().remove(p);
        }
        leaveGame(p);

        //Check if this kick lead to insufficient players to play
        int activeTeams = 0;
        MinigameTeam lastTeam = null;
        for(MinigameTeam team : teams) {
            if(team.getPlayers().size() > 0) {
                lastTeam = team;
                activeTeams++;
            }
        }
        if(activeTeams < 2) {
            if(lastTeam != null) {
                gameRecord.setGameWinner(lastTeam);
                for(Player other : lastTeam.getPlayers()) {
                    other.sendMessage("Game ending due to insufficient players!");
                }
            }
            exit();
            return;
        }
    }

    protected void exit() {
        if(gameOver) {
            Main.getInstance().getLogger().severe("Minigame is attempting to exit twice: "+Misc.getError(new Exception()));
            return;
        }
        gameOver = true;

        for(BukkitTask task : timers) {
            if(task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
        for(MinigameTeam team : teams) {
            for(Player p : team.getPlayers()) {
                leaveGame(p);
                if(team.getName().equals(gameRecord.getWinningTeam())) {
                    awardTickets(p);
                }
            }
        }

        HandlerList.unregisterAll(this);
        gameRecord.save();
        MinigameManager.endGame(this);
    }

    /**
     * Shared, Player specific code between kickPlayer and exit to reduce redundancy. Do not call from elsewhere - call kickPlayer
     * @param p Player in the final stages of leaving the game
     */
    private void leaveGame(Player p) {
        toViewerLocation(p);
        p.getInventory().setContents(oldInventories.remove(p.getUniqueId()));
        MinigameManager.kickPlayer(p);
        closeScoreboard(p);
    }

    /**
     * Shared, Player specific code between the constructor and addLatePlayer to reduce redundancy. Do not call from elsewhere
     * @param p Player joining the game
     */
    protected void joinGame(Player p) {
        welcomePlayer(p);
        deductTokens(p);
        oldInventories.put(p.getUniqueId(), p.getInventory().getContents());
        p.getInventory().clear();
        playersRemaining++;
    }

    /*
        Event listening
     */

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        if(inGame(e.getPlayer())) {
            kickPlayer(e.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        if(!inGame(e.getEntity())) {return;}
        if(!settings.isDropOnDeath()) {e.getDrops().clear();}
        Player p = e.getEntity();
        if(!settings.isMultiDeathsAllowed()) {
            gameRecord.set(p, "life_length", Duration.between(gameStart, Instant.now()));
        } else {
            gameRecord.increment(p,"deaths", 1);
        }

        if(p.getKiller() != null) {
            gameRecord.increment(p.getKiller(), "kills", 1);
        }

        if(settings.isDeadPlayerKicked()) {
            kickPlayer(p);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDamage(EntityDamageEvent e) {
        if(settings.isDeathAllowed()) {return;}
        if(!(e.getEntity() instanceof Player)) {return;}
        Player p = (Player) e.getEntity();
        if(!inGame(p)) {return;}
        e.setCancelled(true);
        /*if(p.getHealth() - e.getFinalDamage() <= 0) { //I am not sure we will ever want the player to take damage but not die (?)
            e.setCancelled(true);
        }*/
    }


    /**
     * Restrict the game area per set in GameSettings. Teleport the player out if they somehow get stuck.
     * @param e PlayerMoveEvent
     */
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent e) {
        if(e.getTo() == null) {return;}
        if(e.getFrom().getBlock().equals(e.getTo().getBlock())) {return;}

        if(settings.isGameAreaRestrictedIn() && inGameArea(e.getTo()) && !inGame(e.getPlayer())) {
            e.setCancelled(true);
            if(exit != null && inGameArea(e.getFrom())) {
                e.getPlayer().teleport(exit);
            }
        } else if(settings.isGameAreaRestrictedOut() && !inGameArea(e.getTo()) && inGameArea(e.getFrom()) && inGame(e.getPlayer())) {//In game and leaving the game area
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        if(inGameArea(e.getBlock().getLocation()) && !inGame(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        if(inGameArea(e.getBlock().getLocation()) && !inGame(e.getPlayer())) {
            e.setCancelled(true);
        }
    }
}
