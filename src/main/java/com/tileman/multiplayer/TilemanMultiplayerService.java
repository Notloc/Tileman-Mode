package com.tileman.multiplayer;

import com.tileman.managers.GroupTilemanProfileUtil;
import com.tileman.managers.RunelitePersistenceManager;
import com.tileman.runelite.TilemanModePlugin;
import com.tileman.managers.TilemanProfileUtil;
import com.tileman.TilemanModeTile;
import com.tileman.multiplayer.client.TilemanClient;
import com.tileman.multiplayer.server.TilemanServer;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;

import java.util.Collection;
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
        return serverClient != null && serverClient.isAlive();
    }


    public static int getServerPort() {
        return server != null ? server.getPortNumber() : -1;
    }

    public static void startServer(GroupTilemanProfile groupTilemanProfile, String password, ConfigManager configManager, int port) {
        if (server != null && server.isAlive()) {
            return;
        }

        GroupTilemanProfileUtil groupProfileManager = new GroupTilemanProfileUtil(new RunelitePersistenceManager(configManager), groupTilemanProfile);
        server = new TilemanServer(groupProfileManager, port, password);
        server.start();
    }

    public static void stopServer() {
        if (server.isAlive()) {
            server.shutdown();
        }
        invokeMultiplayerStateChanged();
    }

    public static void connect(Client client, TilemanModePlugin plugin, TilemanProfileUtil profileManager, String hostname, int port, String password) {
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

    public static void sendMultiplayerTileUpdate(TilemanModeTile tile, boolean state) {
        if (isConnected()) {
            serverClient.sendTileUpdate(tile, state);
        }
    }
}
