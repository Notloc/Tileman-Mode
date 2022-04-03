package com.tileman.multiplayer;

import com.tileman.TilemanModeTile;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TilemanServer extends Thread {

    private int portNumber;
    private ServerSocket serverSocket;

    // Data structure gore, I'm sorry.
    // It's each username's individual tile data, mapped by region id, stored in sets.
    ConcurrentHashMap<String, ConcurrentSetMap<Integer, TilemanModeTile>> playerTileData = new ConcurrentHashMap<>();

    public TilemanServer(int portNumber) {
        this.portNumber = portNumber;
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(portNumber);
            handleNewConnections(serverSocket);
            if (!serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            serverSocket = null;
        }
    }

    public void shutdown() {
        try {
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleNewConnections(ServerSocket serverSocket) {
        boolean running = true;
        while (running) {
            try(Socket connection = serverSocket.accept()) {
                startConnectionThread(connection);
            } catch (SocketException e) {
              running = false;
            } catch (IOException e) {}
        }
    }

    private void startConnectionThread(Socket connection) {
        Runnable connectionThread = () -> handleConnection(connection);
        Thread thread = new Thread(connectionThread);
        thread.start();
    }

    private void handleConnection(Socket connection) {
        try {
            ObjectInputStream input = new ObjectInputStream(connection.getInputStream());
            ObjectOutputStream output = new ObjectOutputStream(connection.getOutputStream());

            while (!connection.isClosed()) {
                TilemanPacket packet = getNextPacket(input);
                if (packet != null) {
                    handlePacket(packet, output);
                }
                sleep(5);
            }
        } catch (IOException | ClassNotFoundException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private TilemanPacket getNextPacket(ObjectInputStream input) throws IOException, ClassNotFoundException {
        if (input.available() > 0) {
            return (TilemanPacket)input.readObject();
        }
        return null;
    }

    private void handlePacket(TilemanPacket packet, ObjectOutputStream output) throws IOException {
        switch (packet.packetType) {
            case REGION_DATA_REQUEST:
                handleRegionDataRequest(packet, output);
                break;
            default:
                throw new IOException("Unexpected packet type in server: " + packet.packetType);
        }
    }

    private void handleRegionDataRequest(TilemanPacket packet, ObjectOutputStream output) throws IOException {
        int regionId = Integer.valueOf(packet.message);

        Set<TilemanModeTile> tiles = gatherTilesInRegionForUser(packet.sender, regionId);

        output.writeObject(TilemanPacket.CreateRegionDataResponse(TilemanPacket.SERVER, regionId));
        output.writeObject(tiles);
    }

    private Set<TilemanModeTile> gatherTilesInRegionForUser(String username, int regionId) {
        Set<TilemanModeTile> gatheredRegionData = new HashSet<>();

        for (String name : playerTileData.keySet()) {
            if (name.equals(username)) {
                continue; // Skip sending a user their own tiles
            }
            Set<TilemanModeTile> regionData = playerTileData.get(name).get(regionId);
            gatheredRegionData.addAll(regionData);
        }
        return gatheredRegionData;
    }

    private static void print(String string) {
        System.out.println("Server: " + string);
    }
}
