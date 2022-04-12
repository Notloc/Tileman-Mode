package com.tileman;

import com.google.common.base.Strings;
import com.google.gson.reflect.TypeToken;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.components.PanelComponent;

import java.awt.*;
import java.util.*;
import java.util.List;

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

    public static int getLongestStringWidth(Graphics2D graphics, String... strings) {
        int longest = graphics.getFontMetrics().stringWidth("000000");
        for(String i: strings) {
            int currentItemWidth = graphics.getFontMetrics().stringWidth(i);
            if(currentItemWidth > longest) {
                longest = currentItemWidth;
            }
        }
        return longest;
    }

    public static String addCommasToNumber(int number) {
        String input = Integer.toString(number);
        StringBuilder output = new StringBuilder();
        for(int x = input.length() - 1; x >= 0; x--) {
            int lastPosition = input.length() - x - 1;
            if(lastPosition != 0 && lastPosition % 3 == 0) {
                output.append(",");
            }
            output.append(input.charAt(x));
        }
        return output.reverse().toString();
    }
}
