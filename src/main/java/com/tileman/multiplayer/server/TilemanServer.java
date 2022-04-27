package com.tileman.multiplayer.server;

import com.tileman.shared.TilemanModeTile;
import com.tileman.multiplayer.shared.*;
import com.tileman.shared.TilemanProfile;
import lombok.Getter;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TilemanServer extends NetworkedThread {

    @Getter
    private final int portNumber;
    private ServerSocket serverSocket;
    private String hashedPassword;

    @Deprecated
    @Getter private GroupTilemanProfile multiplayerGroup;

    // Data structure gore, I'm sorry.
    // It's a thread safe map, keyed by account hash, of each user's tile data. Tile data is mapped by region and stored in hashsets.
    ConcurrentHashMap<Long, ConcurrentSetMap<Integer, TilemanModeTile>> playerTileData = new ConcurrentHashMap<>();

    ConcurrentHashMap<Socket, ConcurrentOutputQueue<Object>> outputQueueBySocket = new ConcurrentHashMap<>();

    private final Set<Socket> activeConnections = ConcurrentHashMap.newKeySet();

    public TilemanServer(int portNumber, String password) {
        this.portNumber = portNumber;
        this.hashedPassword = MpUtil.sha512(password);
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
        try {
            TilemanServerConnectionHandler connectionHandler = new TilemanServerConnectionHandler(this, connection);
            connectionHandler.start();
        } catch (IOException e) {}
    }

    void addConnection(Socket connection, ConcurrentOutputQueue<Object> outputQueue) {
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
        queueOutputForAllConnections(
                TilemanPacket.createTileUpdatePacket(state),
                tile,
                TilemanPacket.createEndOfDataPacket()
        );
    }

    private void ensurePlayerEntry(long playerId) {
        if (!playerTileData.containsKey(playerId)) {
            playerTileData.put(playerId, new ConcurrentSetMap<>());
        }
    }

    void queueOutputForAllConnections(Object... objects) {
        for (Socket connection : activeConnections) {
            queueOutputForConnection(connection, objects);
        }
    }

    private void queueOutputForConnection(Socket connection, Object... data) {
        ConcurrentOutputQueue<Object> outputQueue = outputQueueBySocket.get(connection);
        outputQueue.queueData(data);
    }

    public boolean isRequiresPassword() {
        return hashedPassword != null && !hashedPassword.isEmpty();
    }

    public boolean authenticatePassword(String hashedPassword) {
        if (this.hashedPassword == null || this.hashedPassword.isEmpty()) {
            return true;
        }
        return this.hashedPassword.equals(hashedPassword);
    }
}
