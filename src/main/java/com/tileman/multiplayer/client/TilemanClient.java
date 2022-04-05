package com.tileman.multiplayer.client;

import com.tileman.TilemanModePlugin;
import com.tileman.TilemanModeTile;
import com.tileman.Util;
import com.tileman.multiplayer.*;
import lombok.Getter;
import net.runelite.api.Client;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TilemanClient extends NetworkedThread {

    private final Client client;
    private final TilemanModePlugin plugin;

    private final String hostname;
    private final int portNumber;

    @Getter
    private final ConcurrentSetMap<Integer, TilemanModeTile> multiplayerTileData = new ConcurrentSetMap<>();
    private ConcurrentOutputQueue<Object> outputQueue;
    private TilemanClientState clientState;

    public TilemanClient(Client client, TilemanModePlugin plugin, String hostname, int portNumber) {
        this.client = client;
        this.plugin = plugin;

        this.hostname = hostname;
        this.portNumber = portNumber;
        this.clientState = TilemanClientState.CONNECTING;
    }

    public TilemanClientState getClientState() { return clientState; }

    @Override
    public void run() {
        System.out.println("Launching MP client");
        if (Util.isEmpty(hostname)) {
            return;
        }

        try {
            Socket socket = new Socket(hostname, portNumber);

            outputQueue = new ConcurrentOutputQueue<>(socket.getOutputStream());
            ObjectInputStreamBufferThread inputThread = new ObjectInputStreamBufferThread(socket.getInputStream());
            inputThread.start();

            clientState = TilemanClientState.SYNCING;
            TilemanMultiplayerService.invokeMultiplayerStateChanged();

            uploadTileDataToServer(plugin.getTilesByRegion());

            clientState = TilemanClientState.CONNECTED;
            TilemanMultiplayerService.invokeMultiplayerStateChanged();

            requestRegionData(client.getMapRegions());
            while (!isShutdown()) {
                outputQueue.flush();

                TilemanPacket packet = inputThread.getNextPacket();
                if (packet != null) {
                    handlePacket(packet, inputThread);
                }

                sleep(50);
            }
            System.out.println("Closing!");
            socket.close();
        } catch (ShutdownException e) {
            // Do nothing
        }
        catch (IOException | ClassNotFoundException | InterruptedException | UnexpectedPacketTypeException e) {
            e.printStackTrace();
        } finally {
            clientState = TilemanClientState.DISCONNECTED;
            TilemanMultiplayerService.invokeMultiplayerStateChanged();
        }
    }

    private void uploadTileDataToServer(Map<Integer, List<TilemanModeTile>> tileData) throws IOException {
        long sender = client.getAccountHash();
         for (Integer regionId : tileData.keySet()) {
             outputQueue.queueData(
                 TilemanPacket.createRegionDataResponse(sender, regionId),
                 tileData.get(regionId),
                 TilemanPacket.createEndOfDataPacket(sender)
             );
         }
        outputQueue.flush();
    }

    private void handlePacket(TilemanPacket packet, ObjectInputStreamBufferThread input) throws IOException, ClassNotFoundException, InterruptedException, ShutdownException, UnexpectedPacketTypeException {
        switch (packet.packetType) {
            case REGION_DATA_RESPONSE:
                handleIncomingRegionData(packet, input);
                break;
            case TILE_UPDATE:
                handleTileUpdate(packet, input);
                break;
            default:
                throw new IOException("Unexpected packet type in client: " + packet.packetType);
        }
    }

    private void handleIncomingRegionData(TilemanPacket packet, ObjectInputStreamBufferThread input) throws InterruptedException, ShutdownException, UnexpectedPacketTypeException {
        int regionId = Integer.parseInt(packet.message);
        while (!isShutdown()) {
            Object object = input.waitForData(this);
            if (object instanceof List) {
                List<TilemanModeTile> tiles = (List<TilemanModeTile>) object;
                multiplayerTileData.addAll(regionId, tiles);
            } else {
                validateEndOfDataPacket(object);
                break;
            }
        }
    }

    private void handleTileUpdate(TilemanPacket packet, ObjectInputStreamBufferThread input) throws InterruptedException, ShutdownException, UnexpectedPacketTypeException {
        Object object = input.waitForData(this);
        TilemanModeTile tile = (TilemanModeTile)object;
        validateEndOfDataPacket(input.waitForData(this));

        if (multiplayerTileData.containsKey(tile.getRegionId())) {
            boolean tileState = Boolean.parseBoolean(packet.message);
            if (tileState) {
                multiplayerTileData.add(tile.getRegionId(), tile);
            } else {
                multiplayerTileData.remove(tile.getRegionId(), tile);
            }
        }
    }

    public void requestRegionData(int[] regionIds) {
        for (int regionId : regionIds) {
            outputQueue.queueData(
                TilemanPacket.createRegionDataRequest(client.getAccountHash(), regionId),
                TilemanPacket.createEndOfDataPacket(client.getAccountHash())
            );
        }
    }

    public void sendTileUpdate(TilemanModeTile tile, boolean state) {
        long sender = client.getAccountHash();
        outputQueue.queueData(
            TilemanPacket.createTileUpdatePacket(sender, state),
            tile,
            TilemanPacket.createEndOfDataPacket(sender)
        );
    }

    public void disconnect() {
        isShuttingDown = true;
    }
}