package com.tileman.multiplayer;

import com.tileman.TilemanModeTile;
import lombok.Getter;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TilemanServer extends Thread {

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
        try {
            ObjectOutputStream output = new ObjectOutputStream(connection.getOutputStream());
            output.flush();
            ObjectInputStream input = new ObjectInputStream(connection.getInputStream());

            while (!connection.isClosed()) {
                TilemanPacket packet = getNextPacket(input);
                if (packet != null) {
                    handlePacket(packet, input, output);
                }
                sleep(5);
            }
        } catch (IOException | ClassNotFoundException | InterruptedException e) {
            e.printStackTrace();
        } finally {
            activeConnections.remove(connection);
            outputQueueBySocket.remove(connection);
            TilemanMultiplayerService.invokeMultiplayerStateChanged();
        }
    }

    private TilemanPacket getNextPacket(ObjectInputStream input) throws IOException, ClassNotFoundException {
        if (input.available() > 0) {
            return (TilemanPacket)input.readObject();
        }
        return null;
    }

    private void handlePacket(TilemanPacket packet, ObjectInputStream input, ObjectOutputStream output) throws IOException, ClassNotFoundException {
        switch (packet.packetType) {
            case REGION_DATA_REQUEST:
                handleRegionDataRequest(packet, output);
                break;
            case TILE_UPDATE:
                handleTileUpdate(packet, input);
            default:
                throw new IOException("Unexpected packet type in server: " + packet.packetType);
        }
        output.flush();
    }

    private void handleRegionDataRequest(TilemanPacket packet, ObjectOutputStream output) throws IOException {
        int regionId = Integer.parseInt(packet.message);

        Set<TilemanModeTile> tiles = gatherTilesInRegionForUser(packet.sender, regionId);

        output.writeObject(TilemanPacket.CreateRegionDataResponse(TilemanPacket.SERVER_ID, regionId));
        output.writeObject(tiles);
    }

    private void handleTileUpdate(TilemanPacket packet, ObjectInputStream input) throws IOException, ClassNotFoundException {
        boolean state = Boolean.parseBoolean(packet.message);
        TilemanModeTile tile = (TilemanModeTile)input.readObject();
        if (state) {
            playerTileData.get(packet.sender).get(tile.getRegionId()).add(tile);
        } else {
            playerTileData.get(packet.sender).get(tile.getRegionId()).remove(tile);
        }

        // Send the update to all connected players
        TilemanPacket responsePacket = TilemanPacket.CreateTileUpdatePacket(TilemanPacket.SERVER_ID, state);
        queueOutputForAllConnections(responsePacket, tile);
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
}
