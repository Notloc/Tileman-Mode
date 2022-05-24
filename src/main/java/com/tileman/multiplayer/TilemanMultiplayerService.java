package com.tileman.multiplayer;

import com.tileman.managers.RunelitePersistenceManager;
import com.tileman.managers.TilemanStateManager;
import com.tileman.TilemanModeTile;
import com.tileman.multiplayer.client.TilemanClient;
import com.tileman.multiplayer.server.TilemanServer;
import com.tileman.runelite.TilemanModePlugin;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TilemanMultiplayerService {
    private static TilemanServer server;
    private static TilemanClient serverClient;

    /**
     * Event for when the multiplayer state changes (i.e. server goes up/down, client connects/disconnects)
     * NOT CALLED ON MAIN THREAD
     */
    public static Collection<Runnable> onMultiplayerStateChanged = new ConcurrentLinkedQueue<>();

    public static Set<Integer> updatedRegionIds = ConcurrentHashMap.newKeySet();

    public static boolean isHosting() {
        return server != null && server.isAlive() && !server.isShutdown();
    }
    public static boolean isConnected() {
        return serverClient != null && serverClient.isAlive();
    }


    public static int getServerPort() {
        return server != null ? server.getPortNumber() : -1;
    }

    public static void startServer(TilemanStateManager stateManager, RunelitePersistenceManager persistenceManager, String password, int port) {
        if (server != null && server.isAlive()) {
            return;
        }

        if (!stateManager.getActiveProfile().isGroupTileman()) {
            return;
        }

        server = new TilemanServer(stateManager.getActiveGroupProfile(), persistenceManager, port, password);
        server.start();
    }

    public static void stopServer() {
        if (server.isAlive()) {
            server.shutdown();
        }
        invokeMultiplayerStateChanged();
    }

    public static void connect(TilemanModePlugin plugin, TilemanStateManager stateManager, String hostname, int port, String password) {
        if (isConnected()) {
            disconnect();
        }

        serverClient = new TilemanClient(plugin, stateManager, hostname, port, password);
        serverClient.start();
    }

    public static void disconnect() {
        serverClient.disconnect();
    }

    public static void invokeMultiplayerStateChanged() {
        for (Runnable runnable : onMultiplayerStateChanged) {
            runnable.run();
        }
    }

    public static void sendMultiplayerTileUpdate(TilemanModeTile tile, boolean state) {
        if (isConnected()) {
            serverClient.sendTileUpdateRequest(tile, state);
        }
    }

    public static void leaveGroup() {
        if (isConnected()) {
            serverClient.leaveGroup();
        }
    }
}
