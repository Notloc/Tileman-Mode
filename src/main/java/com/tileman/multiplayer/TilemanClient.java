package com.tileman.multiplayer;

import com.tileman.TilemanModeTile;
import com.tileman.Util;
import net.runelite.api.Client;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TilemanClient extends Thread {

    private Client client;
    private String hostname;
    private int portNumber = 7777;

    private ConcurrentLinkedQueue<Object> queuedPacketsAndData = new ConcurrentLinkedQueue<>();
    private ConcurrentSetMap<Integer, TilemanModeTile> serverTileData = new ConcurrentSetMap<>();

    private TilemanPacket cachedPacket;
    private boolean stayConnected;

    ClientState clientState;

    public TilemanClient(Client client, String hostname, int portNumber) {
        this.client = client;
        this.hostname = hostname;
        this.portNumber = portNumber;
        this.clientState = ClientState.CONNECTING;
        this.stayConnected = true;
    }

    @Override
    public void run() {
        System.out.println("Launching MP client");
        if (Util.isEmpty(hostname)) {
            return;
        }

        try(Socket socket = new Socket(hostname, portNumber)) {
            ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream input = new ObjectInputStream(socket.getInputStream());

            clientState = ClientState.SYNCING;
            //TODO: sync tile data before beginning normal operation
            //      send ALL your tiles for other players
            //      (use hashing to determine what can be skipped, server should save ur profile/tiles from prev connections,
            //          so you dont need to be logged in for others to use your tiles.)

            clientState = ClientState.CONNECTED;
            requestRegionData(client.getMapRegions());

            while (stayConnected) {
                while (queuedPacketsAndData.peek() != null) {
                    output.writeObject(queuedPacketsAndData.remove());
                }

                TilemanPacket packet = getNextPacket(input);
                if (packet != null) {
                    handlePacket(packet, input, output);
                }

                sleep(5);
            }

        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            clientState = ClientState.DISCONNECTED;
        }
    }

    private TilemanPacket getNextPacket(ObjectInputStream input) throws IOException, ClassNotFoundException {
        if (cachedPacket != null) {
            TilemanPacket packet = cachedPacket;
            cachedPacket = null;
            return packet;
        }
        if (input.available() > 0) {
            return (TilemanPacket)input.readObject();
        }
        return null;
    }

    private void handlePacket(TilemanPacket packet, ObjectInputStream input, ObjectOutputStream output) throws IOException, ClassNotFoundException {
        switch (packet.packetType) {
            case REGION_DATA_RESPONSE:
                handleIncomingRegionData(input);
                break;
            case TILE_UPDATE:
                handleTileUpdate(packet, input);
            default:
                throw new IOException("Unexpected packet type!");
        }
    }

    private void handleIncomingRegionData(ObjectInputStream input) throws IOException, ClassNotFoundException {
        while (input.available() > 0) {
            Object object = input.readObject();
            if (object instanceof TilemanModeTile) {
                TilemanModeTile tile = (TilemanModeTile)object;
                serverTileData.add(tile.getRegionId(), tile);
            } else {
                cachedPacket = (TilemanPacket)object;
                break;
            }
        }
    }

    private void handleTileUpdate(TilemanPacket packet, ObjectInputStream input) throws IOException, ClassNotFoundException {
        Object object = input.readObject();
        TilemanModeTile tile = (TilemanModeTile)object;
        if (serverTileData.containsKey(tile.getRegionId())) {
            Boolean tileState = Boolean.valueOf(packet.message);
            if (tileState) {
                serverTileData.add(tile.getRegionId(), tile);
            } else {
                serverTileData.remove(tile.getRegionId(), tile);
            }
        }
    }

    private static void print(String string) {
        System.out.println("Client: " + string);
    }

    public void requestRegionData(int[] regionIds) {
        for (int regionId : regionIds) {
            queuedPacketsAndData.add(TilemanPacket.CreateRegionDataRequest(client.getUsername(), regionId));
        }
    }

    public void sendTileUpdate(TilemanModeTile tile, boolean state) {
        queuedPacketsAndData.add(TilemanPacket.CreateTileUpdatePacket(client.getUsername(), state));
        queuedPacketsAndData.add(tile);
    }

    public void disconnect() {
        stayConnected = false;
    }
}

enum ClientState {
    CONNECTING,
    SYNCING,
    CONNECTED,
    DISCONNECTED
}