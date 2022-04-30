package com.tileman.managers;

import com.tileman.runelite.TilemanModeConfig;
import com.tileman.TilemanGameMode;
import com.tileman.TilemanGameRules;
import com.tileman.TilemanProfile;

public class TilemanGameRulesManager {

    private final TilemanProfileManager profileManager;
    private final PersistenceManager persistenceManager;

    private TilemanGameRules gameRules = TilemanGameRules.GetDefaultRules();

    public TilemanGameRulesManager(TilemanProfileManager profileManager, PersistenceManager persistenceManager) {
        this.profileManager = profileManager;
        this.persistenceManager = persistenceManager;
    }

    public TilemanGameMode getGameMode() { return gameRules.getGameMode(); }
    public void setGameMode(TilemanGameMode mode) {
        gameRules.setGameMode(mode);
        saveGameRules(profileManager.getActiveProfile(), gameRules);
    }

    public boolean isEnableCustomGameMode() { return gameRules.isEnableCustomGameMode(); }
    public void setEnableCustomGameMode(boolean state) {
        gameRules.setEnableCustomGameMode(state);
        saveGameRules(profileManager.getActiveProfile(), gameRules);
    }

    public boolean isAllowTileDeficit() { return gameRules.isAllowTileDeficit(); }
    public void setAllowTileDeficit(boolean state) {
        gameRules.setAllowTileDeficit(state);
        saveGameRules(profileManager.getActiveProfile(), gameRules);
    }

    public boolean isTilesFromTotalLevel() { return gameRules.isTilesFromTotalLevel(); }
    public void setTilesFromTotalLevel(boolean state) {
        gameRules.setTilesFromTotalLevel(state);
        saveGameRules(profileManager.getActiveProfile(), gameRules);
    }

    public boolean isTilesFromExp() { return gameRules.isTilesFromExp(); }
    public void setTilesFromExp(boolean state) {
        gameRules.setTilesFromExp(state);
        saveGameRules(profileManager.getActiveProfile(), gameRules);
    }

    public int getTilesOffset() { return gameRules.getTilesOffset(); }
    public void setTilesOffset(int offset) {
        gameRules.setTilesOffset(offset);
        saveGameRules(profileManager.getActiveProfile(), gameRules);
    }

    public int getExpPerTile() { return gameRules.getExpPerTile(); }
    public void setExpPerTile(int exp) {
        gameRules.setExpPerTile(exp);
        saveGameRules(profileManager.getActiveProfile(), gameRules);
    }

    public void setActiveProfile(TilemanProfile profile) {
        this.gameRules = loadGameRules(profile);
    }

    private TilemanGameRules loadGameRules(TilemanProfile profile) {
        String rulesKey = profile.getGameRulesKey();
        return persistenceManager.loadFromJsonOrDefault(TilemanModeConfig.CONFIG_GROUP, rulesKey, TilemanGameRules.class, TilemanGameRules.GetDefaultRules());
    }

    private void saveGameRules(TilemanProfile profile, TilemanGameRules rules) {
        if (profile.equals(TilemanProfile.NONE)) {
            return;
        }
        String rulesKey = profile.getGameRulesKey();
        persistenceManager.saveToJson(TilemanModeConfig.CONFIG_GROUP, rulesKey, rules);
    }

    public void createGameRulesWithLegacyData(TilemanProfile profile) {
        saveGameRules(profile, loadGameRulesFromLegacySaveDataOrUseDefaults());
    }

    private TilemanGameRules loadGameRulesFromLegacySaveDataOrUseDefaults() {
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
