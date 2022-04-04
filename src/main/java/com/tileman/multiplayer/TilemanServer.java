package com.tileman.multiplayer;

import com.tileman.TilemanModeTile;
import lombok.Getter;
import net.runelite.api.Tile;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TilemanServer extends Thread implements IShutdown {

    @Getter
    private final int portNumber;
    private ServerSocket serverSocket;

    boolean isServerRunning;

    // Data structure gore, I'm sorry.
    // It's a thread safe map by userHash of each user's tile data. Tile data is mapped by region and stored in hashsets.
    ConcurrentHashMap<Long, ConcurrentSetMap<Integer, TilemanModeTile>> playerTileData = new ConcurrentHashMap<>();

    ConcurrentHashMap<Socket, ConcurrentLinkedQueue<Object>> outputQueueBySocket = new ConcurrentHashMap<>();

    private final Set<Socket> activeConnections = ConcurrentHashMap.newKeySet();

    public TilemanServer(int portNumber) {
        this.portNumber = portNumber;
        isServerRunning = true;
    }

    public boolean isShutdown() { return !isServerRunning; }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(portNumber);
            TilemanMultiplayerService.invokeMultiplayerStateChanged();
            handleNewConnections(serverSocket);
            if (!serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void shutdown() {
        isServerRunning = false;
        try {
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        TilemanMultiplayerService.invokeMultiplayerStateChanged();
    }

    private void handleNewConnections(ServerSocket serverSocket) {
        while (isServerRunning) {
            try {
                Socket connection = serverSocket.accept();
                startConnectionThread(connection);
            } catch (IOException e){
                e.printStackTrace();
            }
        }
    }

    private void startConnectionThread(Socket connection) {
        Runnable connectionThread = () -> handleConnection(connection);
        Thread thread = new Thread(connectionThread);
        thread.start();
    }

    private void handleConnection(Socket connection) {
        outputQueueBySocket.put(connection, new ConcurrentLinkedQueue<>());
        activeConnections.add(connection);
        TilemanMultiplayerService.invokeMultiplayerStateChanged();

        ObjectInputStreamBufferThread inputThread = null;

        try {
            ObjectOutputStream output = new ObjectOutputStream(connection.getOutputStream());
            inputThread = new ObjectInputStreamBufferThread(connection.getInputStream());
            inputThread.start();

            while (!connection.isClosed()) {
                TilemanPacket packet = inputThread.getNextPacket();
                if (packet != null) {
                    handlePacket(packet, inputThread, output);
                }
                sleep(50);
            }
        } catch (ShutdownException e) {
            // Do nothing
        } catch (IOException | ClassNotFoundException | InterruptedException | UnexpectedPacketTypeException e) {
            e.printStackTrace();
        } finally {
            activeConnections.remove(connection);
            outputQueueBySocket.remove(connection);
            TilemanMultiplayerService.invokeMultiplayerStateChanged();

            if (inputThread != null) {
                inputThread.teardown();
            }
        }
    }

    private TilemanPacket getNextPacket(ObjectInputStream input) throws IOException, ClassNotFoundException {
        try {
            TilemanPacket packet = (TilemanPacket)input.readObject();
            return packet;
        } catch (EOFException e) {
            return null;
        }
    }

    private void handlePacket(TilemanPacket packet, ObjectInputStreamBufferThread input, ObjectOutputStream output) throws IOException, ClassNotFoundException, ShutdownException, InterruptedException, UnexpectedPacketTypeException {
        switch (packet.packetType) {
            case REGION_DATA_REQUEST:
                handleRegionDataRequest(packet, output);
                break;
            case TILE_UPDATE:
                handleTileUpdate(packet, input);
            case REGION_DATA_RESPONSE:
                handleRegionDataResponse(packet, input);
                break;
            default:
                throw new IOException("Unexpected packet type in server: " + packet.packetType);
        }
        output.flush();
    }

    private void handleRegionDataRequest(TilemanPacket packet, ObjectOutputStream output) throws IOException {
        int regionId = Integer.parseInt(packet.message);

        Set<TilemanModeTile> tileSet = gatherTilesInRegionForUser(packet.sender, regionId);
        List<TilemanModeTile> tiles = new ArrayList<>();
        for (TilemanModeTile tile : tileSet) {
            tiles.add(tile);
        }
        output.writeObject(TilemanPacket.createRegionDataResponse(TilemanPacket.SERVER_ID, regionId));
        output.writeObject(tiles);
        output.writeObject(TilemanPacket.createEndOfDataPacket(TilemanPacket.SERVER_ID));
    }

    private void handleRegionDataResponse(TilemanPacket packet, ObjectInputStreamBufferThread input) throws ShutdownException, InterruptedException, UnexpectedPacketTypeException {
        int regionId = Integer.parseInt(packet.message);

        while (!isShutdown()) {
            Object object = input.waitForData(this);
            if (object instanceof List) {
                List<TilemanModeTile> tiles = (List<TilemanModeTile>)object;
                addTileData(packet.sender, regionId, tiles);
            } else {
                validateEndOfDataPacket(object);
                break;
            }
        }
    }

    private void handleTileUpdate(TilemanPacket packet, ObjectInputStreamBufferThread input) throws InterruptedException, ShutdownException {
        boolean state = Boolean.parseBoolean(packet.message);

        Object object = input.waitForData(this);
        TilemanModeTile tile = (TilemanModeTile)object;

        ensurePlayerEntry(packet.sender);

        if (state) {
            playerTileData.get(packet.sender).get(tile.getRegionId()).add(tile);
        } else {
            playerTileData.get(packet.sender).get(tile.getRegionId()).remove(tile);
        }

        // Send the update to all connected players
        TilemanPacket responsePacket = TilemanPacket.createTileUpdatePacket(TilemanPacket.SERVER_ID, state);
        queueOutputForAllConnections(responsePacket, tile);
    }

    private void ensurePlayerEntry(long playerId) {
        if (!playerTileData.containsKey(playerId)) {
            playerTileData.put(playerId, new ConcurrentSetMap<>());
        }
    }

    private void addTileData(long playerId, int regionId, List<TilemanModeTile> tiles) {
        ensurePlayerEntry(playerId);
        playerTileData.get(playerId).get(regionId).addAll(tiles);
    }

    private Set<TilemanModeTile> gatherTilesInRegionForUser(long userId, int regionId) {
        Set<TilemanModeTile> gatheredRegionData = new HashSet<>();

        for (long id : playerTileData.keySet()) {
            if (id == userId) {
                continue; // Skip sending a user their own tiles
            }
            Set<TilemanModeTile> regionData = playerTileData.get(id).get(regionId);
            gatheredRegionData.addAll(regionData);
        }
        return gatheredRegionData;
    }

    private void queueOutputForAllConnections(TilemanPacket packet, Object... objects) {
        for (Socket connection : activeConnections) {
            queueOutputForConnection(connection, packet, objects);
        }
    }

    private void queueOutputForConnection(Socket connection, TilemanPacket packet, Object... data) {
        Queue<Object> outputQueue = outputQueueBySocket.get(connection);
        outputQueue.add(packet);

        if (data != null) {
            Collections.addAll(outputQueue, data);
        }
    }

    private static void validateEndOfDataPacket(Object data) throws UnexpectedPacketTypeException {
        if (data instanceof TilemanPacket) {
            TilemanPacket packet = (TilemanPacket) data;
            if (packet.packetType != PacketType.END_OF_DATA) {
                throw new UnexpectedPacketTypeException("Expected an END_OF_DATA packet. Received " + packet.packetType + " packet.");
            }
            return;
        }
        throw new UnexpectedPacketTypeException("Expected an END_OF_DATA packet. Received object: " + data.getClass().getSimpleName());
    }
}
