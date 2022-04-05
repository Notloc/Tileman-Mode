package com.tileman.multiplayer.server;

import com.tileman.TilemanModeTile;
import com.tileman.multiplayer.*;
import lombok.Getter;
import net.runelite.api.Tile;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TilemanServer extends NetworkedThread {

    @Getter
    private final int portNumber;
    private ServerSocket serverSocket;

    // Data structure gore, I'm sorry.
    // It's a thread safe map by userHash of each user's tile data. Tile data is mapped by region and stored in hashsets.
    ConcurrentHashMap<Long, ConcurrentSetMap<Integer, TilemanModeTile>> playerTileData = new ConcurrentHashMap<>();

    ConcurrentHashMap<Socket, ConcurrentLinkedQueue<Object>> outputQueueBySocket = new ConcurrentHashMap<>();

    private final Set<Socket> activeConnections = ConcurrentHashMap.newKeySet();

    public TilemanServer(int portNumber) {
        this.portNumber = portNumber;
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
        isShuttingDown = true;
        try {
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        TilemanMultiplayerService.invokeMultiplayerStateChanged();
    }

    private void handleNewConnections(ServerSocket serverSocket) {
        while (!isShutdown()) {
            try {
                Socket connection = serverSocket.accept();
                startConnectionThread(connection);
            } catch (IOException e){
                e.printStackTrace();
            }
        }
    }

    private void startConnectionThread(Socket connection) {
        TilemanServerConnectionHandler connectionHandler = new TilemanServerConnectionHandler(this, connection);
        connectionHandler.start();
    }

    void addConnection(Socket connection, ConcurrentLinkedQueue<Object> outputQueue) {
        activeConnections.add(connection);
        outputQueueBySocket.put(connection, outputQueue);
    }

    void removeConnection(Socket connection) {
        activeConnections.remove(connection);
        outputQueueBySocket.remove(connection);
    }

    Set<TilemanModeTile> gatherTilesInRegionForUser(long userId, int regionId) {
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

    void addTileData(long playerId, int regionId, List<TilemanModeTile> tiles) {
        ensurePlayerEntry(playerId);
        playerTileData.get(playerId).addAll(regionId, tiles);
    }

    void setTile(long sender, TilemanModeTile tile, boolean state) {
        ensurePlayerEntry(sender);

        if (state) {
            playerTileData.get(sender).add(tile.getRegionId(), tile);
        } else {
            playerTileData.get(sender).remove(tile.getRegionId(), tile);
        }
        // Send the update to all connected players
        TilemanPacket updatePacket = TilemanPacket.createTileUpdatePacket(TilemanPacket.SERVER_ID, state);
        TilemanPacket eod = TilemanPacket.createEndOfDataPacket(TilemanPacket.SERVER_ID);
        queueOutputForAllConnections(updatePacket, tile, eod);
    }

    private void ensurePlayerEntry(long playerId) {
        if (!playerTileData.containsKey(playerId)) {
            playerTileData.put(playerId, new ConcurrentSetMap<>());
        }
    }

    void queueOutputForAllConnections(TilemanPacket packet, Object... objects) {
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
