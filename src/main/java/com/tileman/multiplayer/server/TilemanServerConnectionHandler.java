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

    private TilemanProfile connectionProfile;

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

                TilemanPacket packet = inputThread.tryGetNextPacket();
                if (packet != null) {
                    handlePacket(packet, inputThread);
                }
                sleep(50);
            }
        } catch (NetworkShutdownException e) {
            // Do nothing
        } catch (IOException | ClassNotFoundException | InterruptedException | UnexpectedPacketTypeException e) {
            e.printStackTrace();
        } catch (NetworkTimeoutException e) {
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

    private boolean awaitAuthentication(ObjectInputStreamBufferThread inputThread) throws InterruptedException, UnexpectedPacketTypeException, NetworkShutdownException, NetworkTimeoutException {
        TilemanPacket packet = inputThread.waitForNextPacket(server);
        assertPacketType(packet, TilemanPacketType.AUTHENTICATION);
        TilemanProfile profile = inputThread.waitForNextObject(server);

        if (server.getMultiplayerGroup().isMember(profile.getAccountHash())) {
            return true;
        } else if (server.isRequiresPassword()) {
            String hashedPassword = packet.message;
            boolean passwordMatches = server.authenticatePassword(hashedPassword);
            // add member to group
            return passwordMatches;
        }

        // add member to group
        return true;
    }

    private void sendAuthenticationResponse(boolean authenticated) throws IOException {
        outputQueue.queueData(
                TilemanPacket.createAuthenticationResponsePacket(authenticated),
                TilemanPacket.createEndOfDataPacket()
        );
        outputQueue.flush();
    }

    private void handlePacket(TilemanPacket packet, ObjectInputStreamBufferThread input) throws IOException, ClassNotFoundException, NetworkShutdownException, InterruptedException, UnexpectedPacketTypeException, NetworkTimeoutException {
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

    private void handleRegionDataRequest(TilemanPacket packet, ObjectInputStreamBufferThread input) throws IOException, NetworkShutdownException, InterruptedException, UnexpectedPacketTypeException, NetworkTimeoutException {
        assertPacketType(input.waitForNextPacket(server), TilemanPacketType.END_OF_DATA);

        int regionId = Integer.parseInt(packet.message);
        Set<TilemanModeTile> tileSet = server.gatherTilesInRegionForUser(connectionProfile.getAccountHashLong(), regionId);
        List<TilemanModeTile> tiles = new ArrayList<>();
        for (TilemanModeTile tile : tileSet) {
            tiles.add(tile);
        }

        outputQueue.queueData(
            TilemanPacket.createRegionDataResponse(regionId),
            tiles,
            TilemanPacket.createEndOfDataPacket()
        );
    }

    private void handleRegionDataResponse(TilemanPacket packet, ObjectInputStreamBufferThread input) throws NetworkShutdownException, InterruptedException, UnexpectedPacketTypeException, NetworkTimeoutException {
        int regionId = Integer.parseInt(packet.message);

        while (!server.isShutdown()) {
            Object object = input.waitForNextObject(server);
            if (object instanceof List) {
                List<TilemanModeTile> tiles = (List<TilemanModeTile>)object;
                server.addTileData(connectionProfile.getAccountHashLong(), regionId, tiles);
            } else {
                assertPacketType((TilemanPacket)object, TilemanPacketType.END_OF_DATA);
                break;
            }
        }
    }

    private void handleTileUpdate(TilemanPacket packet, ObjectInputStreamBufferThread input) throws InterruptedException, NetworkShutdownException, UnexpectedPacketTypeException, NetworkTimeoutException {
        TilemanModeTile tile = input.waitForNextObject(this);
        assertPacketType(input.waitForNextPacket(server), TilemanPacketType.END_OF_DATA);

        boolean state = Boolean.parseBoolean(packet.message);
        server.setTile(connectionProfile.getAccountHashLong(), tile, state);
    }
}
