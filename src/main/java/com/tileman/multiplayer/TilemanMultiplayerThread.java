package com.tileman.multiplayer;

import com.tileman.GroupTileData;
import com.tileman.ProfileTileData;
import com.tileman.TilemanModeTile;
import com.tileman.multiplayer.model.*;
import lombok.Getter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public abstract class TilemanMultiplayerThread extends Thread {
    private static final int NETWORK_TIMEOUT_MS = 15000;

    protected ConcurrentOutputQueue<Object> outputQueue;

    @Getter
    protected boolean isReady = false;
    protected boolean isShuttingDown = false;
    public boolean isShutdown() { return isShuttingDown; }

    @Override
    public void run() {
        if (onStart()) {
            try {
                isReady = true;
                while (!isShutdown()) {
                    onUpdate();
                }
            } catch (NetworkShutdownException | NetworkTimeoutException e) {}
        }
        onShutdown();
    }

    protected abstract boolean onStart();
    protected abstract void onUpdate() throws NetworkShutdownException, NetworkTimeoutException;
    protected abstract void onShutdown();

    protected void sendGroupProfileRequest() throws IOException {
        outputQueue.queueData(new GroupProfileRequest());
        outputQueue.flush();
    }

    protected void sendGroupProfileResponse(GroupTilemanProfile groupProfile) throws IOException {
        outputQueue.queueData(new GroupProfileResponse(groupProfile));
        outputQueue.flush();
    }

    protected void sendRegionHashReport(ProfileTileData tileData, long accountHash) throws IOException {
        List<RegionDataHash> regionHashData = new ArrayList<>();

        tileData.forEachRegion((regionId, regionTiles) -> {
            int regionHash = regionTiles.hashCode();
            regionHashData.add(new RegionDataHash(accountHash, regionId, regionHash));
        });

        outputQueue.queueData(new RegionHashReportResponse(regionHashData));
        outputQueue.flush();
    }

    protected void sendRegionDataRequest(List<AccountRegionId> regionIds) {
        outputQueue.queueData(new RegionDataRequest(regionIds));
    }

    protected boolean handleNetworkObject(GroupTilemanProfile groupProfile, Object networkObject) throws UnexpectedNetworkObjectException {
        if (networkObject instanceof RegionDataRequest) {
            handleRegionDataRequest((RegionDataRequest) networkObject, groupProfile.getGroupTileData());
        } else if (networkObject instanceof RegionDataResponse) {
            handleRegionDataResponse((RegionDataResponse) networkObject, groupProfile.getGroupTileData());
        } else if (networkObject instanceof RegionHashReportResponse) {
            handleRegionHashReport((RegionHashReportResponse) networkObject, groupProfile.getGroupTileData());
        } else {
            return false;
        }

        return true;
    }

    protected void handleRegionHashReport(RegionHashReportResponse regionHashReportResponse, GroupTileData groupTileData) {
        List<AccountRegionId> regionsToRequest = new ArrayList<>();
        regionHashReportResponse.getRegionHashData().forEach(regionInfo -> {
            int hash = 0;
            if (groupTileData.hasRegion(regionInfo.accountHash, regionInfo.regionId)) {
                hash = groupTileData.getRegionHash(regionInfo.accountHash, regionInfo.regionId);
            }

            if (hash != regionInfo.dataHash) {
                regionsToRequest.add(new AccountRegionId(regionInfo.accountHash, regionInfo.regionId));
            }
        });
        sendRegionDataRequest(regionsToRequest);
    }

    protected void handleRegionDataRequest(RegionDataRequest regionDataRequest, GroupTileData groupTileData) {
        regionDataRequest.getAccountRegionIds().forEach(accountRegionId -> {
            int regionId = accountRegionId.regionId;
            Set<TilemanModeTile> regionTiles = groupTileData.getRegion(accountRegionId.accountHash, regionId);
            if (regionTiles != null) {
                outputQueue.queueData(new RegionDataResponse(accountRegionId, regionTiles));
            }
        });
    }

    protected void handleRegionDataResponse(RegionDataResponse regionDataResponse, GroupTileData groupTileData) {
        AccountRegionId accountRegionId = regionDataResponse.getAccountRegionId();
        Set<TilemanModeTile> tiles = regionDataResponse.getRegionTiles();

        groupTileData.setRegionTiles(accountRegionId.accountHash, accountRegionId.regionId, tiles);
        TilemanMultiplayerService.updatedRegionIds.add(accountRegionId.regionId);
    }

    public void executeInBusyLoop(BusyFunction function) throws InterruptedException, UnexpectedNetworkObjectException, NetworkShutdownException, NetworkTimeoutException {
        executeInBusyLoop(function, 25, NETWORK_TIMEOUT_MS);
    }

    public void executeInBusyLoop(BusyFunction function, long sleepMs, long timeout) throws InterruptedException, UnexpectedNetworkObjectException, NetworkShutdownException, NetworkTimeoutException {
        double time = System.currentTimeMillis();
        while (!isShutdown()) {
            if (function.run() == BusyFunction.Status.FINISHED) {
                return;
            }
            sleep(sleepMs);

            if (time + timeout < System.currentTimeMillis()) {
                throw new NetworkTimeoutException();
            }
        }
        throw new NetworkShutdownException();
    }

    protected GroupTilemanProfile waitForGroupProfileResponse(ObjectInputStreamBufferThread input) throws NetworkShutdownException, UnexpectedNetworkObjectException, InterruptedException, NetworkTimeoutException {
        GroupProfileResponse groupProfileResponse = input.waitForNextObject(this);
        return groupProfileResponse.getGroupProfile();
    }
}

