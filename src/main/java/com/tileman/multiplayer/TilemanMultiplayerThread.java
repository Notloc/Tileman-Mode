package com.tileman.multiplayer;

import com.tileman.TilemanModeTile;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public abstract class TilemanMultiplayerThread extends Thread {

    protected ConcurrentOutputQueue<Object> outputQueue;

    protected boolean isShuttingDown = false;
    public boolean isShutdown() { return isShuttingDown; }

    @Override
    public void run() {
        if (onStart()) {
            try {
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

    protected boolean handlePacket(GroupTileData groupTileData, TilemanPacket packet, ObjectInputStreamBufferThread input) throws InterruptedException, NetworkShutdownException, UnexpectedPacketTypeException, NetworkTimeoutException {
        switch (packet.packetType) {
            case TILE_UPDATE:
                handleTileUpdate(groupTileData, packet, input);
                break;
            case REGION_DATA_REQUEST:
                handleRegionDataRequest(groupTileData, input);
                break;
            case REGION_DATA_RESPONSE:
                handleRegionDataResponse(groupTileData, input);
                break;
            case REGION_HASH_REPORT:
                handleRegionHashReport(groupTileData, input);
                break;
            default:
                return false;
        }
        return true;
    }

    protected abstract void handleTileUpdate(GroupTileData groupTileData, TilemanPacket packet, ObjectInputStreamBufferThread input) throws InterruptedException, NetworkShutdownException, UnexpectedPacketTypeException, NetworkTimeoutException;

    protected void sendRegionHashReport(ConcurrentSetMap<Integer, TilemanModeTile> tileDataByRegion, long accountHash) {
        List<RegionDataHash> regionHashData = new ArrayList<>();

        tileDataByRegion.keySet().stream().forEach(regionId -> {
            int regionHash = tileDataByRegion.get(regionId).hashCode();
            regionHashData.add(new RegionDataHash(accountHash, regionId, regionHash));
        });

        outputQueue.queueData(
                TilemanPacket.createRegionHashReport(),
                regionHashData,
                TilemanPacket.createEndOfDataPacket()
        );
    }

    protected void handleRegionHashReport(GroupTileData groupTileData, ObjectInputStreamBufferThread input) throws NetworkShutdownException, UnexpectedPacketTypeException, InterruptedException, NetworkTimeoutException {
        Object object = input.waitForNextObject(this);
        List<RegionDataHash> regionDataHashes = (ArrayList<RegionDataHash>)object;

        List<AccountRegionId> regionsToRequest = new ArrayList<>();

        regionDataHashes.forEach(regionInfo -> {
            int hash = 0;
            if (groupTileData.hasRegion(regionInfo.accountHash, regionInfo.regionId)) {
                hash = groupTileData.getRegionHash(regionInfo.accountHash, regionInfo.regionId);
            }

            if (hash != regionInfo.dataHash) {
                regionsToRequest.add(new AccountRegionId(regionInfo.accountHash, regionInfo.regionId));
            }
        });

        requestRegionData(regionsToRequest);
    }

    protected void requestRegionData(List<AccountRegionId> regionIds) {
        outputQueue.queueData(
                TilemanPacket.createRegionDataRequest(),
                regionIds,
                TilemanPacket.createEndOfDataPacket()
        );
    }

    protected void handleRegionDataRequest(GroupTileData groupTileData, ObjectInputStreamBufferThread input) throws NetworkShutdownException, InterruptedException, UnexpectedPacketTypeException, NetworkTimeoutException {
        List<AccountRegionId> requestedRegions = input.waitForNextObject(this);
        assertPacketType(input.waitForNextPacket(this), TilemanPacketType.END_OF_DATA);

        requestedRegions.forEach(playerRegionId -> {
            int regionId = playerRegionId.regionId;
            Set<TilemanModeTile> regionTiles = groupTileData.getRegion(playerRegionId.accountHash, regionId);
            if (regionTiles != null) {
                outputQueue.queueData(
                        TilemanPacket.createRegionDataResponse(),
                        playerRegionId,
                        regionTiles.toArray(),
                        TilemanPacket.createEndOfDataPacket()
                );
            }
        });
    }

    protected void handleRegionDataResponse(GroupTileData groupTileData, ObjectInputStreamBufferThread input) throws InterruptedException, NetworkShutdownException, UnexpectedPacketTypeException, NetworkTimeoutException {
        AccountRegionId regionId = input.waitForNextObject(this);
        TilemanModeTile[] tiles = input.waitForNextObject(this);
        assertPacketType(input.waitForNextPacket(this), TilemanPacketType.END_OF_DATA);

        groupTileData.setRegionTiles(regionId.accountHash, regionId.regionId, tiles);
    }


    public static void assertPacketType(TilemanPacket packet, TilemanPacketType expectedType) throws UnexpectedPacketTypeException {
        if (packet.packetType != expectedType) {
            throw new UnexpectedPacketTypeException(String.format("Unexpected packet type. Expected %s but received %s.", expectedType, packet.packetType));
        }
    }

    public void executeInBusyLoop(BusyFunction function) throws InterruptedException, UnexpectedPacketTypeException, NetworkShutdownException, NetworkTimeoutException {
        executeInBusyLoop(function, 25, 25000);
    }

    public void executeInBusyLoop(BusyFunction function, long sleepMs, long timeout) throws InterruptedException, UnexpectedPacketTypeException, NetworkShutdownException, NetworkTimeoutException {
        double time = System.currentTimeMillis();
        while (!isShutdown()) {
            if (function.run() == BusyFunction.Status.CONTINUE) {
                return;
            }
            sleep(sleepMs);

            if (time + timeout < System.currentTimeMillis()) {
                throw new NetworkTimeoutException();
            }
        }
        throw new NetworkShutdownException();
    }
}

