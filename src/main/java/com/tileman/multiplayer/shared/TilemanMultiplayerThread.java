package com.tileman.multiplayer.shared;

import com.tileman.shared.TilemanModeTile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public abstract class TilemanMultiplayerThread extends Thread implements IShutdown {

    protected ConcurrentHashMap<Long, ConcurrentSetMap<Integer, TilemanModeTile>> tileDataByPlayer = new ConcurrentHashMap<>();
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

    protected boolean handlePacket(TilemanPacket packet, ObjectInputStreamBufferThread input) throws IOException, ClassNotFoundException, InterruptedException, NetworkShutdownException, UnexpectedPacketTypeException, NetworkTimeoutException {
        switch (packet.packetType) {
            case TILE_UPDATE:
                handleTileUpdate(packet, input);
                break;
            case REGION_DATA_REQUEST:
                handleRegionDataRequest(packet, input);
            case REGION_DATA_RESPONSE:
                handleRegionDataResponse(input);
                break;
            case REGION_HASH_REPORT:
                handleRegionHashReport(input);
                break;

            default:
                return false;
        }
        return true;
    }

    protected abstract void handleTileUpdate(TilemanPacket packet, ObjectInputStreamBufferThread input) throws InterruptedException, NetworkShutdownException, UnexpectedPacketTypeException, NetworkTimeoutException;

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

    protected void handleRegionHashReport(ObjectInputStreamBufferThread input) throws NetworkShutdownException, UnexpectedPacketTypeException, InterruptedException, NetworkTimeoutException {
        Object object = input.waitForNextObject(this);
        List<RegionDataHash> regionDataHashes = (ArrayList<RegionDataHash>)object;

        List<PlayerRegionId> regionsToRequest = new ArrayList<>();

        regionDataHashes.forEach(hashData -> {
            int hash = 0;
            if (tileDataByPlayer.containsKey(hashData.accountHash)) {
                if (tileDataByPlayer.get(hashData.accountHash).containsKey(hashData.regionId)) {
                    hash = tileDataByPlayer.get(hashData.accountHash).get(hashData.regionId).hashCode();
                }
            }
            if (hash != hashData.dataHash) {
                regionsToRequest.add(new PlayerRegionId(hashData.accountHash, hashData.regionId));
            }
        });

        requestRegionData(regionsToRequest);
    }

    protected void requestRegionData(List<PlayerRegionId> regionIds) {
        outputQueue.queueData(
                TilemanPacket.createRegionDataRequest(),
                regionIds,
                TilemanPacket.createEndOfDataPacket()
        );
    }

    protected void handleRegionDataRequest(TilemanPacket packet, ObjectInputStreamBufferThread input) throws IOException, NetworkShutdownException, InterruptedException, UnexpectedPacketTypeException, NetworkTimeoutException {
        List<PlayerRegionId> requestedRegions = input.waitForNextObject(this);
        assertPacketType(input.waitForNextPacket(this), TilemanPacketType.END_OF_DATA);

        requestedRegions.forEach(playerRegionId -> {
            int regionId = playerRegionId.regionId;
            outputQueue.queueData(
                    TilemanPacket.createRegionDataResponse(),
                    playerRegionId,
                    tileDataByPlayer.get(playerRegionId.accountHash).get(regionId).toArray(),
                    TilemanPacket.createEndOfDataPacket()
            );
        });
    }

    protected void handleRegionDataResponse(ObjectInputStreamBufferThread input) throws InterruptedException, NetworkShutdownException, UnexpectedPacketTypeException, NetworkTimeoutException {
        PlayerRegionId regionId = input.waitForNextObject(this);
        TilemanModeTile[] tiles = input.waitForNextObject(this);
        assertPacketType(input.waitForNextPacket(this), TilemanPacketType.END_OF_DATA);

        if (!tileDataByPlayer.containsKey(regionId.accountHash)) {
            tileDataByPlayer.put(regionId.accountHash, new ConcurrentSetMap<>());
        }

        tileDataByPlayer.get(regionId.accountHash).remove(regionId.regionId);
        tileDataByPlayer.get(regionId.accountHash).addAll(regionId.regionId, tiles);
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

