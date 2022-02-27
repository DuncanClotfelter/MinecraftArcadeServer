package minigame.game.child;

import lombok.AllArgsConstructor;
import lombok.Getter;
import minigame.game.MinigameSettings;
import minigame.util.MinigameTeam;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public final class Connect4 extends TwoPlayerMinigame implements Listener {
    private final Piece[][] board = new Piece[7][7];
    private final Material BORDER = Material.GLASS_PANE;
    private boolean gameOver = false;

    public Connect4(World world, String region, List<MinigameTeam> teams, Location exit) {
        super(MinigameSettings.CONNECT4, world, region, teams, exit);

        for(Piece[] pieces : board) {
            Arrays.fill(pieces, Piece.EMPTY);
        }
    }

    @EventHandler
    protected void onPlayerInteract(@NotNull PlayerInteractEvent e) {
        if (inGame(e.getPlayer())) {
            System.out.println("hit0");
            e.setCancelled(true);
            //Preliminary checks
            Player p = e.getPlayer();
            Block b = e.getPlayer().getTargetBlockExact(getLongestSide() + 5);//arbitrary value 5 to acc for hypotenuse

            if(b == null || b.getType() != BORDER) {return;}
            System.out.println("hit1");
            if ((getTeams().get(0).getPlayers().contains(p) && !isHomeTurn()) ||
                    (getTeams().get(1).getPlayers().contains(p) && isHomeTurn())) {
                p.sendMessage("Please wait for your turn!");
                return;
            }

            System.out.println(b.getLocation());
            Location chosen = getNeighbor(b.getLocation(), 2);//Change if adding rotation support
            System.out.println(chosen);
            int[] coords = XYcoords(coords(chosen));
            System.out.println(Arrays.toString(coords));
            if (coords == null) {return;}
            coords[1] = board[0].length-1;
            System.out.println("hit2");

            if(getPiece(coords) != Piece.EMPTY) {return;}//This column is already full
            System.out.println("hit3");

            //Add our player's piece
            Piece piece = isHomeTurn()? Piece.HOME : Piece.AWAY;
            System.out.println(adj(new int[] {coords[0], coords[1], 0}).add(0, 0, 0));
            addModel(piece.getModel(), adj(new int[] {coords[0], coords[1], 0}));

            //Find the end coordinates of the falling piece
            int finalX = coords[0];
            int finalY;
            for(finalY = board[coords[0]].length-2; finalY >= 0; finalY--) {
                if(getPiece(finalX, finalY) != Piece.EMPTY) {
                    break;
                }
            }
            finalY++;//Get piece above found piece, or -1 -> 0

            board[finalX][finalY] = piece;//Store our piece to calculate whether the player has won later

            gameOver = isRowFinished(finalX, finalY, piece);
            startNextTurn();
        }
    }

    /**
     * Checks for 4 in a row in the following directions: horizontal, vertical, linear diagonal, negative diagonal
     * Will work for any board size.
     * @param origX X to search from
     * @param origY Y to search from
     * @return Returns whether the newly placed piece finishes a row of 4
     */
    private boolean isRowFinished(int origX, int origY, Piece toMatch) {
        int before = 0; int after = 0;
        //Horizontal
        for(int x = origX+1; x < board.length; x++) {
            if(board[x][origY] != toMatch) {break;}
            after++;
        }
        for(int x = origX-1; x >= 0; x--) {
            if(board[x][origY] != toMatch) {break;}
            before++;
        }
        if(before + after >= 3) {return true;}

        //Vertical
        before = 0; after = 0;
        for(int y = origY+1; y < board[origX].length; y++) {
            if(board[origX][y] != toMatch) {break;}
            after++;
        }
        for(int y = origY-1; y >= 0; y--) {
            if(board[origX][y] != toMatch) {break;}
            before++;
        }
        if(before + after >= 3) {return true;}

        //Positive linear
        before = 0; after = 0;
        for(int i = 1; origX + i < board.length && origY+i < board[origX + i].length; i++) {
            if(board[origX+i][origY+i] != toMatch) {break;}
            after++;
        }
        for(int i = -1; origX + i >= 0 && origY+i >= 0; i--) {
            if(board[origX+i][origY+i] != toMatch) {break;}
            before++;
        }
        if(before + after >= 3) {return true;}

        //Negative linear
        before = 0; after = 0;
        for(int i = 1; origX + i < board.length && origY - i >= 0; i++) {
            if(board[origX+i][origY-i] != toMatch) {break;}
            after++;
        }
        for(int i = 1; origX - i >= 0 && origY + i < board[origX - i].length; i++) {
            if(board[origX-i][origY+i] != toMatch) {break;}
            before++;
        }
        return before + after >= 3;
    }

    private Piece getPiece(int x, int y) {return board[x][y];}
    private Piece getPiece(@NotNull int[] coords) {
        return board[coords[0]][coords[1]];
    }

    /**
     * Retreives the X/Y coordinates from the given int[]. Assumes Z is static - sadly this will not work if the board is rotated
     * @param coords int[] x,y,z of the gamearea
     * @return int[] x,y if valid, else null
     */
    private int[] XYcoords(int[] coords) {
        if(coords != null) {
            for(int i = 0; i < 2; i++) {
                if(coords[i] < 0 || coords[i] >= board.length) {
                    return null;
                }
            }
            return new int[] {coords[0], coords[1]};
        }
        return null;
    }

    protected boolean isGameOver() {
        return gameOver;
    }

    @Getter
    @AllArgsConstructor
    private enum Piece {
        HOME, AWAY, EMPTY;

        @NotNull public String getModel() {return "connect4_"+this.name().toLowerCase();}
    }
}
