package com.tileman.managers;

import com.tileman.multiplayer.GroupTilemanProfile;
import com.tileman.runelite.TilemanModeConfig;
import com.tileman.TilemanGameMode;
import com.tileman.TilemanGameRules;
import com.tileman.TilemanProfile;

public class TilemanGameRulesManager {

    private final TilemanStateManager stateManager;
    private final PersistenceManager persistenceManager;

    private TilemanGameRules gameRules = TilemanGameRules.GetDefaultRules();

    public TilemanGameRulesManager(TilemanStateManager profileManager, PersistenceManager persistenceManager) {
        this.stateManager = profileManager;
        this.persistenceManager = persistenceManager;
    }

    public TilemanGameMode getGameMode() { return gameRules.getGameMode(); }
    public void setGameMode(TilemanGameMode mode) {
        gameRules.setGameMode(mode);
        saveGameRules(stateManager.getActiveProfile(), gameRules, persistenceManager);
    }

    public boolean isEnableCustomGameMode() { return gameRules.isEnableCustomGameMode(); }
    public void setEnableCustomGameMode(boolean state) {
        gameRules.setEnableCustomGameMode(state);
        saveGameRules(stateManager.getActiveProfile(), gameRules, persistenceManager);
    }

    public boolean isAllowTileDeficit() { return gameRules.isAllowTileDeficit(); }
    public void setAllowTileDeficit(boolean state) {
        gameRules.setAllowTileDeficit(state);
        saveGameRules(stateManager.getActiveProfile(), gameRules, persistenceManager);
    }

    public boolean isTilesFromTotalLevel() { return gameRules.isTilesFromTotalLevel(); }
    public void setTilesFromTotalLevel(boolean state) {
        gameRules.setTilesFromTotalLevel(state);
        saveGameRules(stateManager.getActiveProfile(), gameRules, persistenceManager);
    }

    public boolean isTilesFromExp() { return gameRules.isTilesFromExp(); }
    public void setTilesFromExp(boolean state) {
        gameRules.setTilesFromExp(state);
        saveGameRules(stateManager.getActiveProfile(), gameRules, persistenceManager);
    }

    public int getTilesOffset() { return gameRules.getTilesOffset(); }
    public void setTilesOffset(int offset) {
        gameRules.setTilesOffset(offset);
        saveGameRules(stateManager.getActiveProfile(), gameRules, persistenceManager);
    }

    public int getExpPerTile() { return gameRules.getExpPerTile(); }
    public void setExpPerTile(int exp) {
        gameRules.setExpPerTile(exp);
        saveGameRules(stateManager.getActiveProfile(), gameRules, persistenceManager);
    }

    public void setActiveProfile(TilemanProfile profile) {
        this.gameRules = loadGameRules(profile);
    }

    public void setActiveGroupProfile(TilemanProfile profile, GroupTilemanProfile groupProfile) {
        TilemanProfile leaderProfile = TilemanProfileUtil.loadProfile(groupProfile.getGroupCreatorAccountHashLong(), persistenceManager);
        this.gameRules = loadGameRules(leaderProfile);
    }

    private TilemanGameRules loadGameRules(TilemanProfile profile) {
        String rulesKey = profile.getGameRulesKey();
        return persistenceManager.loadFromJsonOrDefault(TilemanModeConfig.CONFIG_GROUP, rulesKey, TilemanGameRules.class, TilemanGameRules.GetDefaultRules());
    }

    private static void saveGameRules(TilemanProfile profile, TilemanGameRules rules, PersistenceManager persistenceManager) {
        if (profile.equals(TilemanProfile.NONE)) {
            return;
        }
        String rulesKey = profile.getGameRulesKey();
        persistenceManager.saveToJson(TilemanModeConfig.CONFIG_GROUP, rulesKey, rules);
    }

    public static void createAndSaveGameRulesWithLegacyData(TilemanProfile profile, PersistenceManager persistenceManager) {
        saveGameRules(profile, loadGameRulesFromLegacySaveDataOrUseDefaults(persistenceManager), persistenceManager);
    }

    private static TilemanGameRules loadGameRulesFromLegacySaveDataOrUseDefaults(PersistenceManager persistenceManager) {
        TilemanGameRules defaults = TilemanGameRules.GetDefaultRules();
        TilemanGameRules rules = new TilemanGameRules();
        rules.setGameMode(persistenceManager.loadOrDefault(TilemanModeConfig.CONFIG_GROUP, "gameMode", TilemanGameMode.class, defaults.getGameMode()));
        rules.setAllowTileDeficit(persistenceManager.loadOrDefault(TilemanModeConfig.CONFIG_GROUP, "allowTileDeficit", boolean.class, defaults.isAllowTileDeficit()));
        rules.setEnableCustomGameMode(persistenceManager.loadOrDefault(TilemanModeConfig.CONFIG_GROUP, "enableCustomGameMode", boolean.class, defaults.isEnableCustomGameMode()));
        rules.setTilesOffset(persistenceManager.loadOrDefault(TilemanModeConfig.CONFIG_GROUP, "tilesOffset", int.class, defaults.getTilesOffset()));
        rules.setTilesFromTotalLevel(persistenceManager.loadOrDefault(TilemanModeConfig.CONFIG_GROUP, "includeTotalLevels", boolean.class, defaults.isTilesFromTotalLevel()));
        rules.setTilesFromExp(!persistenceManager.loadOrDefault(TilemanModeConfig.CONFIG_GROUP, "excludeExp", boolean.class, !defaults.isTilesFromExp())); // Negations are intentional due to the option being renamed to have the opposite meaning
        rules.setExpPerTile(persistenceManager.loadOrDefault(TilemanModeConfig.CONFIG_GROUP, "expPerTile", int.class, defaults.getExpPerTile()));
        return rules;
    }
}
