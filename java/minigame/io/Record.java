package minigame.io;

import lombok.Getter;
import minigame.util.Misc;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;

public class Record {
    @Getter private final HashMap<String, Object> data = new HashMap<>();

    public void overwrite(String field, Object toPut) {
        data.remove(field);
        if(toPut instanceof Duration) {
            toPut = new Long(((Duration)toPut).getSeconds());
        } else if(toPut instanceof Instant) {
            toPut = new Long(((Instant) toPut).getEpochSecond());
        }
        data.put(field, toPut);
    }

    /**
     * Adds the new Number to the old while preserving the amount of bits required to store it :( not pretty though
     * @param field
     * @param toAdd
     */
    public void add(String field, Number toAdd) {
        if(!data.containsKey(field)) {data.put(field, toAdd); return;}
        Number orig = (Number)data.remove(field);
        data.put(field, Misc.add(orig, toAdd));

    }

    public void add(String field, Duration toAdd) {
        add(field, toAdd.getSeconds());
    }

    public void add(String field, String toAdd) {
        if(!data.containsKey(field)) {data.put(field, toAdd); return;}
        data.put(field, data.remove(field)+toAdd);
    }

    public void subtract(String field, Duration toSubtract) {
        add(field, -toSubtract.getSeconds());
    }
}