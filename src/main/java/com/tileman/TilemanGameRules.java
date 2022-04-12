package com.tileman;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

public class TilemanGameRules implements Serializable {

    @Getter @Setter private TilemanGameMode gameMode;
    @Getter @Setter private boolean enableCustomGameMode;

    @Setter private boolean allowTileDeficit;
    @Setter private boolean tilesFromExp;
    @Setter private int expPerTile;
    @Setter private boolean tilesFromTotalLevel;
    @Setter private int tilesOffset;
    @Setter private boolean tilesUnlockBankSlots;
    @Setter private int bankSlotScalingFactor;

    public TilemanGameRules() {}

    public static TilemanGameRules GetDefaultRules() {
        TilemanGameRules rules = new TilemanGameRules();

        rules.gameMode = TilemanGameMode.COMMUNITY;
        rules.enableCustomGameMode = false;
        rules.allowTileDeficit = false;
        rules.tilesFromTotalLevel = false;
        rules.tilesFromExp = true;
        rules.tilesOffset = 9;
        rules.expPerTile = 1000;
        rules.tilesUnlockBankSlots = false;
        rules.bankSlotScalingFactor = 5;

        return rules;
    }

    public boolean isTilesFromTotalLevel() {
        return enableCustomGameMode ? tilesFromTotalLevel : getTilesFromTotalLevelByGameMode();
    }

    public int getTilesOffset() {
        return enableCustomGameMode ? tilesOffset : getTilesOffsetByGameMode();
    }

    public boolean isAllowTileDeficit() {
        return enableCustomGameMode ? allowTileDeficit : false;
    }

    public boolean isTilesFromExp() {
        return enableCustomGameMode ? tilesFromExp : true;
    }

    public int getExpPerTile() {
        return enableCustomGameMode ? expPerTile : 1000;
    }

    public boolean isTilesUnlockBankSlots() { return enableCustomGameMode ? tilesUnlockBankSlots : false; }

    public int getBankSlotScalingFactor() { return bankSlotScalingFactor; }

    private boolean getTilesFromTotalLevelByGameMode() {
        switch (gameMode) {
            case ACCELERATED:
                return true;
            default:
                return false;
        }
    }

    private int getTilesOffsetByGameMode() {
        switch (gameMode) {
            case COMMUNITY:
                return 9;
            default:
                return 0;
        }
    }

    public void validate() {
        if (bankSlotScalingFactor == 0) {
            bankSlotScalingFactor = 5;
        }
    }
}
