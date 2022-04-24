package com.tileman.multiplayer.server;

import com.tileman.shared.TilemanModeTile;
import com.tileman.multiplayer.shared.*;
import com.tileman.shared.TilemanProfile;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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

            boolean authenticated = awaitAuthentication(inputThread);
            sendAuthenticationResponse(authenticated);
            if (!authenticated) {
                return;
            }

            while (!server.isShutdown()) {
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
            try {
                connection.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            server.removeConnection(connection);
            TilemanMultiplayerService.invokeMultiplayerStateChanged();
            if (inputThread != null) {
                inputThread.forceStop();
            }
        }
    }

    private boolean awaitAuthentication(ObjectInputStreamBufferThread inputThread) throws InterruptedException, UnexpectedPacketTypeException {
        ValueHolder<Boolean> valueHolder = new ValueHolder<>(false);
        server.executeInBusyLoop(() -> {
            TilemanPacket packet = inputThread.getNextPacket();
            if (packet != null) {
                assertPacketType(packet, TilemanPacketType.AUTHENTICATION);
                if (server.isRequiresPassword()) {
                    String hashedPassword = packet.message;
                    valueHolder.value = server.authenticatePassword(hashedPassword);
                } else {
                    valueHolder.value = true;
                }
                return BusyFunction.Status.FINISHED;
            }
            return BusyFunction.Status.CONTINUE;
        });
        return valueHolder.value;
    }

    private void sendAuthenticationResponse(boolean authenticated) throws IOException {
        outputQueue.queueData(
                TilemanPacket.createAuthenticationResponsePacket(TilemanProfile.NONE, authenticated),
                TilemanPacket.createEndOfDataPacket(TilemanProfile.NONE)
        );
        outputQueue.flush();
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
            TilemanPacket.createRegionDataResponse(TilemanProfile.NONE, regionId),
            tiles,
            TilemanPacket.createEndOfDataPacket(TilemanProfile.NONE)
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
