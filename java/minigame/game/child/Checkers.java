package minigame.game.child;

import lombok.AllArgsConstructor;
import lombok.Getter;
import minigame.game.MinigameSettings;
import minigame.util.MinigameTeam;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Arrays;
import java.util.List;

public final class Checkers extends TwoPlayerMinigame implements Listener {
    private int[] selPiece = null;
    private boolean selKing = false;
    private boolean doubleJumping = false;
    private int homePieceCount;
    private int awayPieceCount;
    private final Piece[][] board = new Piece[8][8];

    public Checkers(World world, String region, List<MinigameTeam> teams, Location exit) {
        super(MinigameSettings.CHECKERS, world, region, teams, exit);
        for (Piece[] pieces : board) {
            Arrays.fill(pieces, Piece.EMPTY);
        }

        for(int x = 0; x < board.length; x++) {
            for (int z = 0; z < board[x].length; z++) {
                if (x % 2 != z % 2) {
                    continue;
                }

                if (z < 3) {
                    setPiece(new int[]{x, z}, Piece.HOME);
                } else if (z > 4) {
                    setPiece(new int[]{x, z}, Piece.AWAY);
                } else {
                    setPiece(new int[]{x, z}, Piece.EMPTY);
                }
            }
        }
    }

    private void setPiece(int[] coords, Piece newPiece) {
        Piece oldPiece = board[coords[0]][coords[1]];
        if(oldPiece != Piece.EMPTY) {
            if (oldPiece.isHomeTeam()) {homePieceCount--;}
            else {awayPieceCount--;}
        }

        if(newPiece == Piece.EMPTY) {
            removeModel(adj(new int[] {coords[0], 1, coords[1]}));
        } else {
            replaceModel(newPiece.getModel(), adj(new int[]{coords[0], 1, coords[1]}));
            if (newPiece.isHomeTeam()) {homePieceCount++;}
            else {awayPieceCount++;}
        }
        board[coords[0]][coords[1]] = newPiece;
    }

    private Piece getPiece(int[] coords) {
        return board[coords[0]][coords[1]];
    }

    /**
     * Calculates and applies a move, and kings and/or reselects the checker if necessary.
     * @param dest The EMPTY coordinates to move to
     * @return True if the move was successful, or false if not
     */
    private boolean move(int[] dest) {
        int avgX = (dest[0] + selPiece[0]) / 2;
        int avgY = (dest[1] + selPiece[1]) / 2;

        if((dest[1] - selPiece[1] == getDir()*2) || //Forward Jump
                (dest[1] - selPiece[1] == getDir()*-2 && selKing)) {//Backward King Jump
            if(Math.abs(dest[0] - selPiece[0]) == 2) {//X must be 2 distant
                Piece middle = board[avgX][avgY];
                if(middle != Piece.EMPTY && middle.isHomeTeam() != isHomeTurn()) {//Middle piece must be of the other's team
                    Piece moving = getPiece(selPiece);
                    getGameRecord().increment("move", getMove(dest));
                    if((isHomeTurn() && dest[1] == board[0].length-1) || (!isHomeTurn() && dest[1] == 0)) {//King checkers that make it to the end
                        moving = moving.getKing();
                        getGameRecord().set("king", true);//Log event
                    }
                    setPiece(dest, moving);
                    setPiece(new int[] {avgX, avgY}, Piece.EMPTY);//Remove jumped piece
                    setPiece(selPiece, Piece.EMPTY);//Set old spot to empty
                    selPiece = dest;//Set selected piece for calculation purposes
                    selKing = moving.isKing();
                    if(hasMoreJumps()) {//Calculate if there are more jumps available
                        doubleJumping = true;
                    } else {
                        unselect();
                        doubleJumping = false;
                    }
                    return true;
                }
            }
        } else if(doubleJumping) {
            getPlayer().sendMessage("You have an additional jump available!");
            return false;
        } else if(((dest[1] - selPiece[1] == getDir()) ||
                    (dest[1] - selPiece[1] == -getDir() && selKing)) &&
                    Math.abs(dest[0] - selPiece[0]) == 1) {//Regular move
                if(jumpAvailable()) {
                    getPlayer().sendMessage("You must take the jump available!");
                    return false;
                }
                getGameRecord().increment("move", getMove(dest));
                Piece moving = getPiece(selPiece);
                if((isHomeTurn() && dest[1] == board[0].length-1) || (!isHomeTurn() && dest[1] == 0)) {//King checkers that make it to the end
                    moving = moving.getKing();
                    getGameRecord().set("king", true);//Log event
                }
                setPiece(dest, moving);
                setPiece(selPiece, Piece.EMPTY);
                selPiece = dest;
                selKing = moving.isKing();
                unselect();
                doubleJumping = false;
                return true;
        }
        return false;
    }

    /**
     * Calculates whether the currently selected piece, selPiece[], is eligible for additional jumps after the first one
     * @return True/False
     */
    private boolean hasMoreJumps() {
        return checkJumps(selPiece);
    }

    /**
     * Calculates whether the given Piece location (int[] coords) is eligible for additional jumps after the first one
     * @return True/False
     */
    private boolean checkJumps(int[] coords) {
        int farX, farZ, z;
        for(int i = 0; i < (getPiece(coords).isKing() ? 2 : 1); i++) {
            z = getDir() + (getDir() * -2 * i);
            for(int x = -1; x <= 1; x += 2) {
                farX = coords[0] + x*2;
                farZ = coords[1] + z*2;

                if(offBoard(farX) || offBoard(farZ)) {
                    continue;
                }
                Piece farPiece = board[farX][farZ];
                Piece midPiece = board[farX-x][farZ-z];
                if(farPiece == Piece.EMPTY && midPiece != Piece.EMPTY && midPiece.isHomeTeam() != isHomeTurn() && !midPiece.isSelected()) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean jumpAvailable() {
        for(int x = 0; x < board.length; x++) {
            for(int z = 0; z < board[x].length; z++) {
                if(board[x][z] != Piece.EMPTY && board[x][z].isHomeTeam() == isHomeTurn()) {
                    if(checkJumps(new int[] {x, z})) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean offBoard(int i) {
        return i <= -1 || i >= board.length;
    }

    private int getDir() {
        return isHomeTurn() ? 1 : -1;
    }

    private void select(Piece p, int[] coords) {
        selKing = p.isKing();
        selPiece = coords;
        setPiece(coords, Piece.select(p));
    }

    private void unselect() {
        if(selPiece == null) {return;}
        setPiece(selPiece, Piece.unselect(getPiece(selPiece), isHomeTurn()));
        selPiece = null;
    }

    @EventHandler
    protected void onPlayerInteract(PlayerInteractEvent e) {
        if(inGame(e.getPlayer())) {
            e.setCancelled(true);
            Player p = e.getPlayer();
            if((getTeams().get(0).getPlayers().contains(p) && !isHomeTurn()) ||
                    (getTeams().get(1).getPlayers().contains(p) && isHomeTurn())) {
                p.sendMessage("Please wait for your turn!");
                return;
            }
            Block b = e.getPlayer().getTargetBlockExact(getLongestSide()+10);//arbitrary value 10 to acc for hypotenuse
            if(b == null) {return;}
            int[] coords = XZcoords(coords(b.getLocation()));
            if(coords == null) {return;}
            Piece clicked = getPiece(coords);

            if(clicked != Piece.EMPTY) {//Piece clicked
                if(doubleJumping) {
                    p.sendMessage("You have an additional jump available!");
                    return;
                }
                if (isSelected() && clicked.isSelected()) {//Unselect
                    unselect();
                } else if (clicked.isHomeTeam() == isHomeTurn()) {//Select
                    unselect();
                    select(clicked, coords);
                }
            } else if(isSelected()) {//Board clicked & piece ready to move
                if(move(coords)) {
                    if(doubleJumping) {
                        p.sendMessage("You must take your extra jump!");
                    } else {
                        startNextTurn();
                    }
                }
            }
        }
    }

    private int[] XZcoords(int[] coords) {
        if(coords != null) {
            for(int i = 0; i < coords.length; i+=2) {
                if(coords[i] < 0 || coords[i] >= board.length) {
                    return null;
                }
            }
            if(coords[1] != 0 && coords[1] != 1) { //Above/below the board
                return null;
            } else if(coords[2] % 2 != coords[0] % 2) { //Empty unplayable space
                return null;
            }
            return new int[] {coords[0], coords[2]};
        }
        return null;
    }

    private boolean isSelected() {
        return selPiece != null;
    }

    private String getMove(int[] dest) {
        return ((char)(72 - selPiece[0])) + "" + selPiece[1] + ((char)(72 - dest[0])) + "" + dest[1];
    }

    @Override
    protected boolean isGameOver() {
        return (isHomeTurn() ? awayPieceCount : homePieceCount) <= 0;
    }

    @Getter
    @AllArgsConstructor
    private enum Piece {
        EMPTY(false, false),
        HOME(true, false),
        AWAY(false, false),
        SELECT(true, true),
        HOMEKING(true, false),
        AWAYKING(false, false),
        SELECTKING(true, true);

        private final boolean homeTeam;
        private final boolean selected;

        public static Piece unselect(Piece p, boolean homeTeam) {
            if(p == SELECT) {
                return homeTeam? HOME : AWAY;
            } else {
                return homeTeam? HOMEKING : AWAYKING;
            }
        }

        public static Piece select(Piece p) {
            if(!p.isKing()) {
                return SELECT;
            } else {
                return SELECTKING;
            }
        }

        public boolean isKing() {
            return this == HOMEKING || this == AWAYKING || this == SELECTKING;
        }

        public Piece getKing() {
            if(this == HOME) {return HOMEKING;}
            else {return AWAYKING;}
        }

        public String getModel() {return "checkers_"+this.name().toLowerCase();}
    }
}