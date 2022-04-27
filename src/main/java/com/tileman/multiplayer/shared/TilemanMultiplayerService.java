package com.tileman.multiplayer.shared;

import com.tileman.TilemanModePlugin;
import com.tileman.TilemanProfileManager;
import com.tileman.shared.TilemanModeTile;
import com.tileman.multiplayer.client.TilemanClient;
import com.tileman.multiplayer.client.TilemanClientState;
import com.tileman.multiplayer.server.TilemanServer;
import com.tileman.shared.TilemanProfile;
import net.runelite.api.Client;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TilemanMultiplayerService {
    private static TilemanServer server;
    private static TilemanClient serverClient;

    /**
     * Event for when the multiplayer state changes (i.e. server goes up/down, client connects/disconnects)
     * NOT CALLED ON MAIN THREAD
     */
    public static Collection<Runnable> onMultiplayerStateChanged = new ConcurrentLinkedQueue<>();

    public static boolean isHosting() {
        return server != null && server.isAlive() && !server.isShutdown();
    }
    public static boolean isConnected() {
        return serverClient != null && serverClient.isAlive() && serverClient.getClientState() == TilemanClientState.CONNECTED;
    }

    public static boolean isSyncing() {
        return serverClient != null && serverClient.isAlive() && serverClient.getClientState() == TilemanClientState.SYNCING;
    }

    public static int getServerPort() {
        return server != null ? server.getPortNumber() : -1;
    }

    public static void startServer(Client client, TilemanModePlugin plugin, TilemanProfileManager profileManager, int port, String password) {
        if (server != null && server.isAlive()) {
            return;
        }
        server = new TilemanServer(port, "");
        server.start();

        connect(client, plugin, profileManager, "localhost", port, password);
    }

    public static void stopServer() {
        if (server.isAlive()) {
            server.shutdown();
        }
        invokeMultiplayerStateChanged();
    }

    public static void connect(Client client, TilemanModePlugin plugin, TilemanProfileManager profileManager, String hostname, int port, String password) {
        if (serverClient != null && serverClient.isAlive()) {
            return;
        }

        serverClient = new TilemanClient(client, plugin, profileManager, hostname, port, password);
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

    public static ConcurrentSetMap<Integer, TilemanModeTile> getMultiplayerTileData() {
        return serverClient.getMultiplayerTileData();
    }

    public static void requestRegionData(List<Integer> regions) {
        if (isConnected()) {
            serverClient.requestRegionData(regions);
        }
    }

    public static void sendMultiplayerTileUpdate(TilemanModeTile tile, boolean state) {
        if (isConnected()) {
            serverClient.sendTileUpdate(tile, state);
        }
    }
}