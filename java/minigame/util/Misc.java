package minigame.util;

import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Misc {
    public static String sanitize(String str) {
        return Arrays.stream(str.replaceAll("_", " ").toLowerCase().split("\\s+"))
                .map(t -> t.substring(0, 1).toUpperCase() + t.substring(1))
                .collect(Collectors.joining(" "));
    }

    /**
     * Returns the value of two Number objects added together without truncation or rounding,
     * retaining the smallest data-size result possible
     * @param n1 Number 1
     * @param n2 Number 2
     * @return n1 + n2
     */
    public static Number add(Number n1, Number n2) {
        if(n1 instanceof Double || n2 instanceof Double) {
            return n1.doubleValue() + n2.doubleValue();
        } else if(n1 instanceof Float || n2 instanceof Float) {
            return n1.floatValue() + n2.floatValue();
        } else if(n1 instanceof Long || n2 instanceof Long) {
            return n1.longValue() + n2.longValue();
        } else if(n1 instanceof Integer || n2 instanceof Integer) {
            return n1.intValue() + n2.intValue();
        } else if(n1 instanceof Short || n2 instanceof Short) {
            return n1.shortValue() + n2.shortValue();
        } else if(n1 instanceof Byte || n2 instanceof Byte) {
            return n1.byteValue() + n2.byteValue();
        }
        return n1.doubleValue() + n2.doubleValue();
    }

    public static String color(String toColor) {
        return ChatColor.translateAlternateColorCodes('&', toColor);
    }

    public static BlockVector3 getVector(Block b) {return BlockVector3.at(b.getX(), b.getY(), b.getZ());}
    public static BlockVector3 getVector(Location l) {return BlockVector3.at(l.getX(), l.getY(), l.getZ());}

    //Create a Location object from the result of Location.toString()
    public static Location strToLoc(World world, String str) {
        String yawStr = findBetween(str,"yaw=", "\\}");
        if(str.contains("yaw=")) {
            return new Location(
                    world,
                    doubBetween(str, "x=", ","),
                    doubBetween(str, "y=", ","),
                    doubBetween(str, "z=", ","),
                    str.endsWith("\\}") ? floatBetween(str, "yaw=", "\\}") : Float.parseFloat(str.substring(str.indexOf("yaw=")+4)),
                    floatBetween(str, "pitch=", ",")
            );
        } else {
            return new Location(
                    world,
                    doubBetween(str, "x=", ","),
                    doubBetween(str, "y=", ","),
                    doubBetween(str, "z=", ",")
            );
        }
    }

    //Find a substring of given string full between String before and String after
    public static String findBetween(String full, String before, String after) {
        Pattern p = Pattern.compile("(?<="+before+")(.+?)(?="+after+")");
        Matcher m = p.matcher(full);
        if(!m.find()) {return null;}
        return m.group(1);
    }

    //Return the result of findBetween parsed as a Double
    public static double doubBetween(String full, String before, String after) {
        return Double.parseDouble(findBetween(full, before, after));
    }

    //Return the result of findBetween parsed as a float
    public static float floatBetween(String full, String before, String after) {
        return Float.parseFloat(findBetween(full, before, after));
    }

    public static String getError(Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }
}
