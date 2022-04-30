package com.tileman.multiplayer.server;

import com.tileman.shared.TilemanModeTile;
import com.tileman.multiplayer.shared.*;
import lombok.Getter;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TilemanServer extends Thread implements IShutdown {

    @Getter
    private final GroupTilemanProfileManager profileManager;

    @Getter
    private final int portNumber;
    private ServerSocket serverSocket;
    private final String hashedPassword;

    @Getter
    private GroupTileData groupTileData;
    private final Set<TilemanServerConnectionHandler> activeConnections = ConcurrentHashMap.newKeySet();

    private boolean isShutdown;

    public TilemanServer(GroupTilemanProfileManager profileManager, int portNumber, String password) {
        this.profileManager = profileManager;
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
        isShutdown = true;
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
            activeConnections.add(connectionHandler);
        } catch (IOException e) {}
    }

    void forwardTileUpdate(long sender, TilemanModeTile tile, boolean state) {
        // Send the update to all connected players
        queueOutputForAllConnections(
                TilemanPacket.createTileUpdatePacket(state),
                sender,
                tile,
                TilemanPacket.createEndOfDataPacket()
        );
    }

    void queueOutputForAllConnections(Object... objects) {
        for (TilemanServerConnectionHandler connection : activeConnections) {
            queueOutputForConnection(connection, objects);
        }
    }

    private void queueOutputForConnection(TilemanServerConnectionHandler connection, Object... data) {
        connection.queueData(data);
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

    @Override
    public boolean isShutdown() {
        return isShutdown;
    }
}
