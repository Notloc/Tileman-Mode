package com.tileman;

import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Util {

    public static void translateTilesToWorldPoints(Client client, Collection<TilemanModeTile> tiles, List<WorldPoint> worldPointsOut) {
        if (tiles == null) {
            return;
        }
        tiles.forEach(tile -> {
            WorldPoint point = WorldPoint.fromRegion(tile.getRegionId(), tile.getRegionX(), tile.getRegionY(), tile.getZ());
            worldPointsOut.addAll(WorldPoint.toLocalInstance(client, point));
        });
    }

    public static <T> boolean containsAnyOf(T[] haystack, T[] needles) {
        if (haystack.length == 0 || needles.length == 0) {
            return false;
        }
        for (T needle : needles) {
            for (T hay : haystack) {
                if (hay.equals(needle)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }

    public static <T> Set<T> set(T... items) {
        Set<T> set = new HashSet<T>();
        for (T item : items) {
            set.add(item);
        }
        return set;
    }
}
