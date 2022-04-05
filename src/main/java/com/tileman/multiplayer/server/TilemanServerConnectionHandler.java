package com.tileman.multiplayer.server;

import com.tileman.TilemanModeTile;
import com.tileman.multiplayer.*;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TilemanServerConnectionHandler extends NetworkedThread {

    private TilemanServer server;
    Socket connection;

    private ConcurrentOutputQueue<Object> outputQueue;

    public TilemanServerConnectionHandler(TilemanServer server, Socket connection) throws IOException {
        this.server = server;
        this.connection = connection;
        this.outputQueue = new ConcurrentOutputQueue<>(connection.getOutputStream());
        server.addConnection(connection, outputQueue);
    }

    @Override
    public void run() {
        TilemanMultiplayerService.invokeMultiplayerStateChanged();
        ObjectInputStreamBufferThread inputThread = null;

        try {
            inputThread = new ObjectInputStreamBufferThread(connection.getInputStream());
            inputThread.start();

            while (!connection.isClosed()) {
                outputQueue.flush();

                TilemanPacket packet = inputThread.getNextPacket();
                if (packet != null) {
                    handlePacket(packet, inputThread);
                }
                sleep(50);
            }
        } catch (ShutdownException e) {
            // Do nothing
        } catch (IOException | ClassNotFoundException | InterruptedException | UnexpectedPacketTypeException e) {
            e.printStackTrace();
        } finally {
            server.removeConnection(connection);
            TilemanMultiplayerService.invokeMultiplayerStateChanged();
            if (inputThread != null) {
                inputThread.teardown();
            }
        }
    }

    private void handlePacket(TilemanPacket packet, ObjectInputStreamBufferThread input) throws IOException, ClassNotFoundException, ShutdownException, InterruptedException, UnexpectedPacketTypeException {
        switch (packet.packetType) {
            case REGION_DATA_REQUEST:
                handleRegionDataRequest(packet, input);
                break;
            case TILE_UPDATE:
                handleTileUpdate(packet, input);
                break;
            case REGION_DATA_RESPONSE:
                handleRegionDataResponse(packet, input);
                break;
            default:
                throw new IOException("Unexpected packet type in server: " + packet.packetType);
        }
    }

    private void handleRegionDataRequest(TilemanPacket packet, ObjectInputStreamBufferThread input) throws IOException, ShutdownException, InterruptedException, UnexpectedPacketTypeException {
        validateEndOfDataPacket(input.waitForData(this));

        int regionId = Integer.parseInt(packet.message);
        Set<TilemanModeTile> tileSet = server.gatherTilesInRegionForUser(packet.sender, regionId);
        List<TilemanModeTile> tiles = new ArrayList<>();
        for (TilemanModeTile tile : tileSet) {
            tiles.add(tile);
        }

        outputQueue.queueData(
            TilemanPacket.createRegionDataResponse(TilemanPacket.SERVER_ID, regionId),
            tiles,
            TilemanPacket.createEndOfDataPacket(TilemanPacket.SERVER_ID)
        );
    }

    private void handleRegionDataResponse(TilemanPacket packet, ObjectInputStreamBufferThread input) throws ShutdownException, InterruptedException, UnexpectedPacketTypeException {
        int regionId = Integer.parseInt(packet.message);

        while (!server.isShutdown()) {
            Object object = input.waitForData(server);
            if (object instanceof List) {
                List<TilemanModeTile> tiles = (List<TilemanModeTile>)object;
                server.addTileData(packet.sender, regionId, tiles);
            } else {
                validateEndOfDataPacket(object);
                break;
            }
        }
    }

    private void handleTileUpdate(TilemanPacket packet, ObjectInputStreamBufferThread input) throws InterruptedException, ShutdownException, UnexpectedPacketTypeException {
        boolean state = Boolean.parseBoolean(packet.message);

        Object object = input.waitForData(this);
        TilemanModeTile tile = (TilemanModeTile)object;
        validateEndOfDataPacket(input.waitForData(this));

        server.setTile(packet.sender, tile, state);
    }
}
