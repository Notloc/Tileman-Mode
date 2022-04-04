package com.tileman.multiplayer;

import com.tileman.TilemanModeTile;
import com.tileman.Util;
import net.runelite.api.Client;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TilemanClient extends Thread implements IShutdown {

    private final Client client;
    private final String hostname;
    private final int portNumber;

    private final ConcurrentLinkedQueue<Object> queuedPacketsAndData = new ConcurrentLinkedQueue<>();
    final ConcurrentSetMap<Integer, TilemanModeTile> multiplayerTileData = new ConcurrentSetMap<>();

    private boolean stayConnected;

    ClientState clientState;

    public TilemanClient(Client client, String hostname, int portNumber) {
        this.client = client;
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
            //TODO: sync tile data before beginning normal operation
            //      send ALL your tiles for other players
            //      (use hashing to determine what can be skipped, server should save ur profile/tiles from prev connections,
            //          so you don't need to be logged in for others to use your tiles.)

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
        catch (IOException | ClassNotFoundException | InterruptedException e) {
            e.printStackTrace();
        } finally {
            clientState = ClientState.DISCONNECTED;
            TilemanMultiplayerService.invokeMultiplayerStateChanged();
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

    private void handlePacket(TilemanPacket packet, ObjectInputStreamBufferThread input, ObjectOutputStream output) throws IOException, ClassNotFoundException, InterruptedException, ShutdownException {
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

    private void handleIncomingRegionData(TilemanPacket packet, ObjectInputStreamBufferThread input) throws InterruptedException, ShutdownException {
        int regionId = Integer.parseInt(packet.message);
        while (!isShutdown()) {
            Object object = input.waitForData(this);
            if (object instanceof List) {
                List<TilemanModeTile> tiles = (List<TilemanModeTile>) object;
                multiplayerTileData.addAll(regionId, tiles);
            } else {
                TilemanPacket endPacket = (TilemanPacket)object;
                if (endPacket.packetType != PacketType.END_OF_DATA) {
                    System.out.println("Illegal packet received in client handleIncomingRegionData()");
                }
                break;
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
}

enum ClientState {
    CONNECTING,
    SYNCING,
    CONNECTED,
    DISCONNECTED
}