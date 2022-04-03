package com.tileman.multiplayer;

import net.runelite.api.Client;

import java.math.BigInteger;

public class TilemanMultiplayerService {
    private static TilemanServer server;
    private static TilemanClient serverClient;

    public static boolean isHosting() {
        return server != null && server.isAlive();
    }
    public static boolean isConnected() {
        return serverClient != null && serverClient.isAlive() && serverClient.clientState == ClientState.CONNECTED;
    }

    public static boolean isSyncing() {
        return serverClient != null && serverClient.isAlive() && serverClient.clientState == ClientState.SYNCING;
    }

    public static int getServerPort() {
        return server != null ? server.getPortNumber() : -1;
    }

    public static void startServer(Client client, int port) {
        if (server != null && server.isAlive()) {
            return;
        }
        server = new TilemanServer(port);
        server.start();

        connect(client, "localhost", port);
    }

    public static void stopServer() {
        if (server.isAlive()) {
            server.shutdown();
        }
    }

    public static void connect(Client client, String hostname, int port) {
        validateHostname(hostname);
        if (serverClient != null && serverClient.isAlive()) {
            return;
        }

        serverClient = new TilemanClient(client, hostname, port);
        serverClient.start();
    }

    public static void disconnect() {
        //serverClient.disconnect();
    }

    private static void validateHostname(String hostname) {
        // TODO: validate ip format? DNS/localhost
    }
}
