package com.tileman.multiplayer;

import net.runelite.api.Client;

import java.math.BigInteger;

public class TilemanMultiplayerService {
    private static TilemanServer server;
    private static TilemanClient serverClient;

    public static void startServer(int port) {
        if (server != null && server.isAlive()) {
            return;
        }
        server = new TilemanServer(port);
        server.start();
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
