/*
 * Copyright (c) 2018, TheLonelyDev <https://github.com/TheLonelyDev>
 * Copyright (c) 2018, Adam <Adam@sigterm.info>
 * Copyright (c) 2020, ConorLeckey <https://github.com/ConorLeckey>
 * Copyright (c) 2022, Colton Campbell <https://github.com/Notloc>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.tileman.runelite;

import com.google.inject.Provides;
import com.tileman.ProfileTileData;
import com.tileman.Util;
import com.tileman.managers.RunelitePersistenceManager;
import com.tileman.managers.TilemanGameRulesManager;
import com.tileman.managers.TilemanStateManager;
import com.tileman.multiplayer.TilemanMultiplayerService;
import com.tileman.TilemanModeTile;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import java.util.*;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

@Slf4j
@PluginDescriptor(
        name = "Tileman Mode",
        description = "Automatically draws tiles where you walk",
        tags = {"overlay", "tiles"}
)
class TilemanModePlugin extends Plugin {
    private static final String MARK = "Unlock Tileman tile";
    private static final String UNMARK = "Clear Tileman tile";
    private static final String WALK_HERE = "Walk here";

    private final Set<Integer> tutorialIslandRegionIds = Util.set(12079, 12080, 12335, 12336, 12592);

    @Inject private Client client;
    @Inject private TilemanModeConfig config;
    @Inject private ConfigManager configManager;
    @Inject private OverlayManager overlayManager;
    @Inject private TilemanModeOverlay overlay;
    @Inject private TilemanModeMinimapOverlay minimapOverlay;
    @Inject private TilemanModeWorldMapOverlay worldMapOverlay;
    @Inject private TileInfoOverlay infoOverlay;
    @Inject private ClientToolbar clientToolbar;

    @Provides
    TilemanModeConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(TilemanModeConfig.class);
    }

    private final MovementFlag[] fullBlock = new MovementFlag[]
        {
            MovementFlag.BLOCK_MOVEMENT_FLOOR,
            MovementFlag.BLOCK_MOVEMENT_FLOOR_DECORATION,
            MovementFlag.BLOCK_MOVEMENT_OBJECT,
            MovementFlag.BLOCK_MOVEMENT_FULL
        };

    private final MovementFlag[] allDirections = new MovementFlag[]
        {
            MovementFlag.BLOCK_MOVEMENT_NORTH_WEST,
            MovementFlag.BLOCK_MOVEMENT_NORTH,
            MovementFlag.BLOCK_MOVEMENT_NORTH_EAST,
            MovementFlag.BLOCK_MOVEMENT_EAST,
            MovementFlag.BLOCK_MOVEMENT_SOUTH_EAST,
            MovementFlag.BLOCK_MOVEMENT_SOUTH,
            MovementFlag.BLOCK_MOVEMENT_SOUTH_WEST,
            MovementFlag.BLOCK_MOVEMENT_WEST
        };

    private int totalTilesUsed, remainingTiles, xpUntilNextTile;
    private LocalPoint lastTile;
    private int lastPlane;
    private boolean lastAutoTilesConfig = false;
    private boolean inHouse = false;
    private long totalXp;
    private GameState lastSeenGameState = GameState.LOGIN_SCREEN;

    @Getter
    private boolean isLoggedIn = false;

    @Getter
    private TilemanStateManager tilemanStateManager;

    @Getter
    private Map<Long, List<WorldPoint>> visiblePoints = new HashMap<>();


    public List<BiConsumer<Boolean, Long>> onLoginStateChangedEvent = new ArrayList<>();

    public boolean isShowAdvancedOptions() {
        return config.showAdvancedOptions();
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        if (event.getMenuAction().getId() != MenuAction.RUNELITE.getId() ||
                !(event.getMenuOption().equals(MARK) || event.getMenuOption().equals(UNMARK))) {
            return;
        }

        Tile target = client.getSelectedSceneTile();
        if (target == null) {
            return;
        }
        handleMenuOption(target.getLocalLocation(), event.getMenuOption().equals(MARK));
    }

    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event) {
        final boolean hotKeyPressed = client.isKeyPressed(KeyCode.KC_SHIFT);
        if (hotKeyPressed && tilemanStateManager.hasActiveProfile() && event.getOption().equals(WALK_HERE)) {
            final Tile selectedSceneTile = client.getSelectedSceneTile();

            if (selectedSceneTile == null) {
                return;
            }

            final WorldPoint worldPoint = WorldPoint.fromLocalInstance(client, selectedSceneTile.getLocalLocation());
            final int regionId = worldPoint.getRegionID();
            final TilemanModeTile tile = new TilemanModeTile(regionId, worldPoint.getRegionX(), worldPoint.getRegionY(), client.getPlane());

            boolean hasTile = tilemanStateManager.getActiveProfileTileData().hasTile(regionId, tile);
            client.createMenuEntry(-1)
                    .setOption(hasTile ? UNMARK : MARK)
                    .setTarget(event.getTarget())
                    .setType(MenuAction.RUNELITE);
        }
    }

    @Subscribe
    public void onGameTick(GameTick tick) {
        autoMark();
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged) {
        updateGameState(gameStateChanged.getGameState());
        if (gameStateChanged.getGameState() != GameState.LOGGED_IN) {
            lastTile = null;
            return;
        }

        requestMultiplayerTiles();
        updateAllVisiblePoints();
        updateTileInfoDisplay();
        inHouse = false;
    }

    private void updateGameState(GameState gameState) {
        if (gameState == GameState.LOGGED_IN && lastSeenGameState != GameState.LOGGED_IN) {
            isLoggedIn = true;
            lastSeenGameState = GameState.LOGGED_IN;
            onLoginStateChangedEvent.forEach(func -> func.accept(isLoggedIn, client.getAccountHash()));
        } else if (gameState == GameState.LOGIN_SCREEN && lastSeenGameState != GameState.LOGIN_SCREEN) {
            isLoggedIn = false;
            lastSeenGameState = GameState.LOGIN_SCREEN;
            onLoginStateChangedEvent.forEach(func -> func.accept(isLoggedIn, null));
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (client.getLocalPlayer() == null) {
            return;
        }
        // Check if automark tiles is on, and if so attempt to step on current tile
        final WorldPoint playerPos = client.getLocalPlayer().getWorldLocation();
        final LocalPoint playerPosLocal = LocalPoint.fromWorld(client, playerPos);
        if (playerPosLocal != null && config.automarkTiles() && !lastAutoTilesConfig) {
            handleWalkedToTile(playerPosLocal);
        }
        lastAutoTilesConfig = config.automarkTiles();
        updateTileInfoDisplay();
    }


    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event) {
        GameObject gameObject = event.getGameObject();

        if (gameObject.getId() == 4525) {
            inHouse = true;
        }
    }

    @Override
    protected void startUp() {
        tutorialIslandRegionIds.add(12079);
        tutorialIslandRegionIds.add(12080);
        tutorialIslandRegionIds.add(12335);
        tutorialIslandRegionIds.add(12336);
        tutorialIslandRegionIds.add(12592);

        tilemanStateManager = new TilemanStateManager(new RunelitePersistenceManager(configManager));
        onLoginStateChangedEvent.add((isLoggedIn, accountHash) -> {
           if (isLoggedIn) {
               tilemanStateManager.onLoggedIn(accountHash);
           } else {
               tilemanStateManager.onLoggedOut();
           }
        });

        overlayManager.add(overlay);
        overlayManager.add(minimapOverlay);
        overlayManager.add(worldMapOverlay);
        overlayManager.add(infoOverlay);

        updateTileInfoDisplay();
        log.debug("startup");

        TilemanPluginPanel panel = new TilemanPluginPanel(this, client, tilemanStateManager);

        NavigationButton navButton = NavigationButton.builder()
                .tooltip("Tileman Mode")
                .icon(ImageUtil.getResourceStreamFromClass(getClass(), "/icon.png"))
                .priority(70)
                .panel(panel)
                .build();

        TilemanMultiplayerService.onMultiplayerStateChanged.add(() -> panel.rebuild());
        tilemanStateManager.onProfileChangedEvent.add((localProfile, groupProfile) -> {
            panel.rebuild();
            updateTileInfoDisplay();
            updateAllVisiblePoints();
        });
        clientToolbar.addNavigation(navButton);
    }

    @Override
    protected void shutDown() {
        tutorialIslandRegionIds.clear();
        overlayManager.remove(overlay);
        overlayManager.remove(minimapOverlay);
        overlayManager.remove(worldMapOverlay);
        overlayManager.remove(infoOverlay);
    }

    private void updateAllVisiblePoints() {
        visiblePoints.clear();
        if (tilemanStateManager.getActiveGroupTileData() != null) {
            tilemanStateManager.getActiveGroupProfile().getGroupMemberAccountHashes().forEach(accountHashString -> {
                updateVisiblePointsForAccountHash(Long.parseLong(accountHashString));
            });
        } else if (tilemanStateManager.getActiveProfileTileData() != null) {
            updateVisiblePointsForAccountHash(tilemanStateManager.getActiveProfile().getAccountHashLong());
        }
    }

    private void updateVisiblePointsForAccountHash(long accountHash) {
        visiblePoints.remove(accountHash);

        ProfileTileData tileData = tilemanStateManager.getProfileDataForProfile(accountHash);
        if (tileData == null) {
            return;
        }

        List<WorldPoint> points = new ArrayList<>();

        int[] regionIds = client.getMapRegions();
        for (int regionId : regionIds) {
            Set<TilemanModeTile> tiles = tileData.getRegion(regionId);
            if (tiles == null) {
                continue;
            }
            Util.translateTilesToWorldPoints(client, tiles, points);
        }
        visiblePoints.put(accountHash, points);
    }

    private void autoMark() {
        final WorldPoint playerPos = client.getLocalPlayer().getWorldLocation();
        if (playerPos == null) {
            return;
        }

        final LocalPoint playerPosLocal = LocalPoint.fromWorld(client, playerPos);
        if (playerPosLocal == null) {
            return;
        }

        long currentTotalXp = client.getOverallExperience();

        // If we have no last tile, we probably just spawned in, so make sure we walk on our current tile
        if ((lastTile == null
                || (lastTile.distanceTo(playerPosLocal) != 0 && lastPlane == playerPos.getPlane())
                || lastPlane != playerPos.getPlane()) && !regionIsOnTutorialIsland(playerPos.getRegionID())) {
            // Player moved
            handleWalkedToTile(playerPosLocal);
            lastTile = playerPosLocal;
            lastPlane = client.getPlane();
            updateTileInfoDisplay();
            log.debug("player moved");
            log.debug("last tile={}  distance={}", lastTile, lastTile == null ? "null" : lastTile.distanceTo(playerPosLocal));
        } else if (totalXp != currentTotalXp) {
            updateTileInfoDisplay();
            totalXp = currentTotalXp;
        }
    }

    private void updateTileInfoDisplay() {
        int totalTiles = tilemanStateManager.countUnlockedTiles();
        log.debug("Updating tile counter");
        updateTotalTilesUsed(totalTiles);
        updateRemainingTiles(totalTiles);
        updateXpUntilNextTile();
    }

    private void updateTotalTilesUsed(int totalTilesCount) {
        totalTilesUsed = totalTilesCount;
    }

    private void updateRemainingTiles(int placedTiles) {
        TilemanGameRulesManager gameRules = tilemanStateManager.getGameRulesManager();
        // Start with tiles offset. We always get these
        int earnedTiles = gameRules.getTilesOffset();

        // If including xp, add those tiles in
        if (gameRules.isTilesFromExp()) {
            earnedTiles += (int) client.getOverallExperience() / gameRules.getExpPerTile();
        }

        // If including total level, add those tiles in
        if (gameRules.isTilesFromTotalLevel()) {
            earnedTiles += client.getTotalLevel();
        }

        remainingTiles = earnedTiles - placedTiles;
    }

    private void updateXpUntilNextTile() {
        TilemanGameRulesManager gameRules = tilemanStateManager.getGameRulesManager();
        xpUntilNextTile = gameRules.getExpPerTile() - Integer.parseInt(Long.toString(client.getOverallExperience() % gameRules.getExpPerTile()));
    }

    int getTotalTiles() {
        return totalTilesUsed;
    }

    int getRemainingTiles() {
        return remainingTiles;
    }

    private void handleMenuOption(LocalPoint selectedPoint, boolean markedValue) {
        if (selectedPoint == null) {
            return;
        }
        updateTileMark(selectedPoint, markedValue);
    }

    private void handleWalkedToTile(LocalPoint currentPlayerPoint) {
        if (currentPlayerPoint == null ||
                inHouse ||
                !config.automarkTiles()) {
            return;
        }

        // Mark the tile they walked to
        updateTileMark(currentPlayerPoint, true);

        // If player moves 2 tiles in a straight line, fill in the middle tile
        // TODO Fill path between last point and current point. This will fix missing tiles that occur when you lag
        // TODO   and rendered frames are skipped. See if RL has an api that mimic's OSRS's pathing. If so, use that to
        // TODO   set all tiles between current tile and lastTile as marked
        if(lastTile != null){
            int xDiff = currentPlayerPoint.getX() - lastTile.getX();
            int yDiff = currentPlayerPoint.getY() - lastTile.getY();
            int yModifier = yDiff / 2;
            int xModifier = xDiff / 2;

            switch(lastTile.distanceTo(currentPlayerPoint)) {
                case 0: // Haven't moved
                case 128: // Moved 1 tile
                    return;
                case 181: // Moved 1 tile diagonally
                    handleCornerMovement(xDiff, yDiff);
                    break;
                case 256: // Moved 2 tiles straight
                case 362: // Moved 2 tiles diagonally
                    fillTile(new LocalPoint(lastTile.getX() + xModifier, lastTile.getY() + yModifier));
                    break;
                case 286: // Moved in an 'L' shape
                    handleLMovement(xDiff, yDiff);
                    break;
            }
        }
    }

    private void handleLMovement(int xDiff, int yDiff) {
        int xModifier = xDiff / 2;
        int yModifier = yDiff / 2;
        int tileBesideXDiff, tileBesideYDiff;

        // Whichever direction has moved only one, keep it 0. This is the translation to the potential 'problem' gameObject
        if (Math.abs(yDiff) == 128) {
            tileBesideXDiff = xDiff;
            tileBesideYDiff = 0;
        } else {
            tileBesideXDiff = 0;
            tileBesideYDiff = yDiff;
        }

        MovementFlag[] tileBesideFlagsArray = getTileMovementFlags(lastTile.getX() + tileBesideXDiff, lastTile.getY() + tileBesideYDiff);

        if (tileBesideFlagsArray.length == 0) {
            fillTile(new LocalPoint(lastTile.getX() + tileBesideXDiff / 2, lastTile.getY() + tileBesideYDiff / 2));
        } else if (Util.containsAnyOf(fullBlock, tileBesideFlagsArray)) {
            if (yModifier == 64) {
                yModifier = 128;
            } else if (xModifier == 64) {
                xModifier = 128;
            }
            fillTile(new LocalPoint(lastTile.getX() + xModifier, lastTile.getY() + yModifier));
        } else if (Util.containsAnyOf(allDirections, tileBesideFlagsArray)){
            MovementFlag direction1, direction2;
            if (yDiff == 256 || yDiff == -128) {
                // Moving 2 North or 1 South
                direction1 = MovementFlag.BLOCK_MOVEMENT_SOUTH;
            } else {
                // Moving 2 South or 1 North
                direction1 = MovementFlag.BLOCK_MOVEMENT_NORTH;
            }
            if (xDiff == 256 || xDiff == -128) {
                // Moving 2 East or 1 West
                direction2 = MovementFlag.BLOCK_MOVEMENT_WEST;
            } else {
                // Moving 2 West or 1 East
                direction2 = MovementFlag.BLOCK_MOVEMENT_EAST;
            }

            if (Util.containsAnyOf(tileBesideFlagsArray, new MovementFlag[]{direction1, direction2})) {
                // Interrupted
                if (yModifier == 64) {
                    yModifier = 128;
                } else if (xModifier == 64) {
                    xModifier = 128;
                }
                fillTile(new LocalPoint(lastTile.getX() + xModifier, lastTile.getY() + yModifier));
            } else {
                // Normal Pathing
                fillTile(new LocalPoint(lastTile.getX() + tileBesideXDiff / 2, lastTile.getY() + tileBesideYDiff / 2));
            }
        }
    }

    private void handleCornerMovement(int xDiff, int yDiff) {
        LocalPoint northPoint;
        LocalPoint southPoint;
        if(yDiff > 0) {
            northPoint = new LocalPoint(lastTile.getX(), lastTile.getY() + yDiff);
            southPoint = new LocalPoint(lastTile.getX() + xDiff, lastTile.getY());
        } else {
            northPoint = new LocalPoint(lastTile.getX() + xDiff, lastTile.getY());
            southPoint = new LocalPoint(lastTile.getX(), lastTile.getY() + yDiff);
        }

        MovementFlag[] northTile = getTileMovementFlags(northPoint);
        MovementFlag[] southTile = getTileMovementFlags(southPoint);

        if (xDiff + yDiff == 0) {
            // Diagonal tilts north west
            if(Util.containsAnyOf(fullBlock, northTile)
                    || Util.containsAnyOf(northTile, new MovementFlag[]{MovementFlag.BLOCK_MOVEMENT_SOUTH, MovementFlag.BLOCK_MOVEMENT_WEST})){
                fillTile(southPoint);
            } else if (Util.containsAnyOf(fullBlock, southTile)
                    || Util.containsAnyOf(southTile, new MovementFlag[]{MovementFlag.BLOCK_MOVEMENT_NORTH, MovementFlag.BLOCK_MOVEMENT_EAST})){
                fillTile(northPoint);
            }
        } else {
            // Diagonal tilts north east
            if(Util.containsAnyOf(fullBlock, northTile)
                    || Util.containsAnyOf(northTile, new MovementFlag[]{MovementFlag.BLOCK_MOVEMENT_SOUTH, MovementFlag.BLOCK_MOVEMENT_EAST})){
                fillTile(southPoint);
            } else if (Util.containsAnyOf(fullBlock, southTile)
                    || Util.containsAnyOf(southTile, new MovementFlag[]{MovementFlag.BLOCK_MOVEMENT_NORTH, MovementFlag.BLOCK_MOVEMENT_WEST})){
                fillTile(northPoint);
            }
        }
    }

    private MovementFlag[] getTileMovementFlags(int x, int y) {
        LocalPoint pointBeside = new LocalPoint(x, y);

        CollisionData[] collisionData = client.getCollisionMaps();
        assert collisionData != null;
        int[][] collisionDataFlags = collisionData[client.getPlane()].getFlags();

        Set<MovementFlag> tilesBesideFlagsSet = MovementFlag.getSetFlags(collisionDataFlags[pointBeside.getSceneX()][pointBeside.getSceneY()]);
        MovementFlag[] tileBesideFlagsArray = new MovementFlag[tilesBesideFlagsSet.size()];
        tilesBesideFlagsSet.toArray(tileBesideFlagsArray);

        return tileBesideFlagsArray;
    }

    private MovementFlag[] getTileMovementFlags(LocalPoint localPoint) {
        return  getTileMovementFlags(localPoint.getX(), localPoint.getY());
    }



    private boolean regionIsOnTutorialIsland(int regionId) {
        return tutorialIslandRegionIds.contains(regionId);
    }

    private void fillTile(LocalPoint localPoint){
        if(lastPlane != client.getPlane()) {
            return;
        }
        updateTileMark(localPoint, true);
    }

    private void updateTileMark(LocalPoint localPoint, boolean markedValue) {
        if(Util.containsAnyOf(getTileMovementFlags(localPoint), fullBlock)) {
            return;
        }
        WorldPoint worldPoint = WorldPoint.fromLocalInstance(client, localPoint);
        int regionId = worldPoint.getRegionID();
        TilemanModeTile tile = new TilemanModeTile(regionId, worldPoint.getRegionX(), worldPoint.getRegionY(), client.getPlane());

        tilemanStateManager.updateTileMark(regionId, tile, markedValue);
    }

    int getXpUntilNextTile() {
        return xpUntilNextTile;
    }
    public Client getClient() { return client; }

    @AllArgsConstructor
    enum MovementFlag {
        BLOCK_MOVEMENT_NORTH_WEST(CollisionDataFlag.BLOCK_MOVEMENT_NORTH_WEST),
        BLOCK_MOVEMENT_NORTH(CollisionDataFlag.BLOCK_MOVEMENT_NORTH),
        BLOCK_MOVEMENT_NORTH_EAST(CollisionDataFlag.BLOCK_MOVEMENT_NORTH_EAST),
        BLOCK_MOVEMENT_EAST(CollisionDataFlag.BLOCK_MOVEMENT_EAST),
        BLOCK_MOVEMENT_SOUTH_EAST(CollisionDataFlag.BLOCK_MOVEMENT_SOUTH_EAST),
        BLOCK_MOVEMENT_SOUTH(CollisionDataFlag.BLOCK_MOVEMENT_SOUTH),
        BLOCK_MOVEMENT_SOUTH_WEST(CollisionDataFlag.BLOCK_MOVEMENT_SOUTH_WEST),
        BLOCK_MOVEMENT_WEST(CollisionDataFlag.BLOCK_MOVEMENT_WEST),

        BLOCK_MOVEMENT_OBJECT(CollisionDataFlag.BLOCK_MOVEMENT_OBJECT),
        BLOCK_MOVEMENT_FLOOR_DECORATION(CollisionDataFlag.BLOCK_MOVEMENT_FLOOR_DECORATION),
        BLOCK_MOVEMENT_FLOOR(CollisionDataFlag.BLOCK_MOVEMENT_FLOOR),
        BLOCK_MOVEMENT_FULL(CollisionDataFlag.BLOCK_MOVEMENT_FULL);

        @Getter
        private int flag;

        /**
         * @param collisionData The tile collision flags.
         * @return The set of {@link MovementFlag}s that have been set.
         */
        public static Set<MovementFlag> getSetFlags(int collisionData) {
            return Arrays.stream(values())
                    .filter(movementFlag -> (movementFlag.flag & collisionData) != 0)
                    .collect(Collectors.toSet());
        }
    }

    private int[] mapRegionsLastSeen = null;
    private void requestMultiplayerTiles() {
        if (mapRegionsLastSeen == null) {
            mapRegionsLastSeen = client.getMapRegions();
        }

        int[] newMapRegions = client.getMapRegions();
        List<Integer> requestRegions = new ArrayList<>();

        for (int newRegion : newMapRegions) {
            boolean contains = false;
            for (int region : mapRegionsLastSeen) {
                if (newRegion == region) {
                    contains = true;
                    break;
                }
            }
            if (!contains) {
                requestRegions.add(newRegion);
            }
        }

        //TilemanMultiplayerService.requestRegionData(requestRegions);
        mapRegionsLastSeen = client.getMapRegions();
    }
}