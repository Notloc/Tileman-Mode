package com.tileman.multiplayer;

import com.tileman.TilemanModePlugin;
import com.tileman.TilemanModeTile;
import com.tileman.Util;
import net.runelite.api.Client;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TilemanClient extends Thread implements IShutdown {

    private final Client client;
    private final TilemanModePlugin plugin;

    private final String hostname;
    private final int portNumber;

    private final ConcurrentLinkedQueue<Object> queuedPacketsAndData = new ConcurrentLinkedQueue<>();
    final ConcurrentSetMap<Integer, TilemanModeTile> multiplayerTileData = new ConcurrentSetMap<>();

    private boolean stayConnected;

    ClientState clientState;


    public TilemanClient(Client client, TilemanModePlugin plugin, String hostname, int portNumber) {
        this.client = client;
        this.plugin = plugin;

        this.hostname = hostname;
        this.portNumber = portNumber;
        this.clientState = ClientState.CONNECTING;
        this.stayConnected = true;
    }

    public boolean isShutdown() { return !stayConnected; }

    @Override
    public void run() {
        System.out.println("Launching MP client");
        if (Util.isEmpty(hostname)) {
            return;
        }

        try {
            Socket socket = new Socket(hostname, portNumber);

            ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());

            ObjectInputStreamBufferThread inputThread = new ObjectInputStreamBufferThread(socket.getInputStream());
            inputThread.start();

            clientState = ClientState.SYNCING;
            TilemanMultiplayerService.invokeMultiplayerStateChanged();

            uploadTileDataToServer(plugin.getTilesByRegion(), output);

            clientState = ClientState.CONNECTED;
            TilemanMultiplayerService.invokeMultiplayerStateChanged();

            requestRegionData(client.getMapRegions());
            while (stayConnected) {
                handleOutputQueue(output);

                TilemanPacket packet = inputThread.getNextPacket();
                if (packet != null) {
                    handlePacket(packet, inputThread, output);
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
            clientState = ClientState.DISCONNECTED;
            TilemanMultiplayerService.invokeMultiplayerStateChanged();
        }
    }

    private void uploadTileDataToServer(Map<Integer, List<TilemanModeTile>> tileData, ObjectOutputStream output) throws IOException {
        long sender = client.getAccountHash();
         for (Integer regionId : tileData.keySet()) {
             output.writeObject(TilemanPacket.createRegionDataResponse(sender, regionId));
             output.writeObject(tileData.get(regionId));
             output.writeObject(TilemanPacket.createEndOfDataPacket(sender));
             output.flush();
         }
    }

    private void handleOutputQueue(ObjectOutputStream output) throws IOException {
        if (queuedPacketsAndData.peek() == null) {
            return;
        }

        while (queuedPacketsAndData.peek() != null) {
            output.writeObject(queuedPacketsAndData.remove());
        }
        output.flush();
    }

    private void handlePacket(TilemanPacket packet, ObjectInputStreamBufferThread input, ObjectOutputStream output) throws IOException, ClassNotFoundException, InterruptedException, ShutdownException, UnexpectedPacketTypeException {
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
            }
        }
    }

    private void handleTileUpdate(TilemanPacket packet, ObjectInputStreamBufferThread input) throws InterruptedException, ShutdownException {
        Object object = input.waitForData(this);

        TilemanModeTile tile = (TilemanModeTile)object;
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
            queuedPacketsAndData.add(TilemanPacket.createRegionDataRequest(client.getAccountHash(), regionId));
        }
    }

    public void sendTileUpdate(TilemanModeTile tile, boolean state) {
        queuedPacketsAndData.add(TilemanPacket.createTileUpdatePacket(client.getAccountHash(), state));
        queuedPacketsAndData.add(tile);
    }

    public void disconnect() {
        stayConnected = false;
    }

    private static void validateEndOfDataPacket(Object data) throws UnexpectedPacketTypeException {
        if (data instanceof TilemanPacket) {
            TilemanPacket packet = (TilemanPacket) data;
            if (packet.packetType != PacketType.END_OF_DATA) {
                throw new UnexpectedPacketTypeException("Expected an END_OF_DATA packet. Received " + packet.packetType + " packet.");
            }
        }
        throw new UnexpectedPacketTypeException("Expected an END_OF_DATA packet. Received object: " + data.getClass().getSimpleName());
    }
}

enum ClientState {
    CONNECTING,
    SYNCING,
    CONNECTED,
    DISCONNECTED
}