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

    private ConcurrentLinkedQueue<Object> outputQueue = new ConcurrentLinkedQueue<>();

    public TilemanServerConnectionHandler(TilemanServer server, Socket connection) {
        this.server = server;
        this.connection = connection;
        server.addConnection(connection, outputQueue);
    }

    @Override
    public void run() {
        TilemanMultiplayerService.invokeMultiplayerStateChanged();
        ObjectInputStreamBufferThread inputThread = null;

        try {
            ObjectOutputStream output = new ObjectOutputStream(connection.getOutputStream());
            inputThread = new ObjectInputStreamBufferThread(connection.getInputStream());
            inputThread.start();

            while (!connection.isClosed()) {
                handleOutputQueue(output);

                TilemanPacket packet = inputThread.getNextPacket();
                if (packet != null) {
                    handlePacket(packet, inputThread, output);
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

    private void handleOutputQueue(ObjectOutputStream output) throws IOException {
        if (outputQueue.peek() == null) {
            return;
        }

        while (outputQueue.peek() != null) {
            output.writeObject(outputQueue.remove());
        }
        output.flush();
    }

    private void handlePacket(TilemanPacket packet, ObjectInputStreamBufferThread input, ObjectOutputStream output) throws IOException, ClassNotFoundException, ShutdownException, InterruptedException, UnexpectedPacketTypeException {
        switch (packet.packetType) {
            case REGION_DATA_REQUEST:
                handleRegionDataRequest(packet, output, input);
                break;
            case TILE_UPDATE:
                handleTileUpdate(packet, input);
            case REGION_DATA_RESPONSE:
                handleRegionDataResponse(packet, input);
                break;
            default:
                throw new IOException("Unexpected packet type in server: " + packet.packetType);
        }
        output.flush();
    }

    private void handleRegionDataRequest(TilemanPacket packet, ObjectOutputStream output, ObjectInputStreamBufferThread input) throws IOException, ShutdownException, InterruptedException, UnexpectedPacketTypeException {
        validateEndOfDataPacket(input.waitForData(this));

        int regionId = Integer.parseInt(packet.message);
        Set<TilemanModeTile> tileSet = server.gatherTilesInRegionForUser(packet.sender, regionId);
        List<TilemanModeTile> tiles = new ArrayList<>();
        for (TilemanModeTile tile : tileSet) {
            tiles.add(tile);
        }

        output.writeObject(TilemanPacket.createRegionDataResponse(TilemanPacket.SERVER_ID, regionId));
        output.writeObject(tiles);
        output.writeObject(TilemanPacket.createEndOfDataPacket(TilemanPacket.SERVER_ID));
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
