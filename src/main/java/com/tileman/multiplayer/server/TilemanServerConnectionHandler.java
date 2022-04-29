package com.tileman.multiplayer.server;

import com.tileman.shared.TilemanModeTile;
import com.tileman.multiplayer.shared.*;
import com.tileman.shared.TilemanProfile;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

public class TilemanServerConnectionHandler extends TilemanMultiplayerThread {

    private TilemanServer server;
    Socket connection;

    private TilemanProfile connectionProfile;
    private ObjectInputStreamBufferThread inputThread;

    public TilemanServerConnectionHandler(TilemanServer server, Socket connection) throws IOException {
        this.server = server;
        this.connection = connection;
        this.outputQueue = new ConcurrentOutputQueue<>(connection.getOutputStream());
        server.addConnection(connection, outputQueue);
    }

    @Override
    protected boolean onStart() {
        TilemanMultiplayerService.invokeMultiplayerStateChanged();

        try {
            inputThread = new ObjectInputStreamBufferThread(connection.getInputStream());
            inputThread.start();

            boolean authenticated = awaitAuthentication(inputThread);
            sendAuthenticationResponse(authenticated);
            if (authenticated) {
                return true;
            }
        } catch (NetworkShutdownException e) {
            e.printStackTrace();
        } catch (UnexpectedPacketTypeException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (NetworkTimeoutException e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean awaitAuthentication(ObjectInputStreamBufferThread inputThread) throws InterruptedException, UnexpectedPacketTypeException, NetworkShutdownException, NetworkTimeoutException {
        TilemanPacket packet = inputThread.waitForNextPacket(server);
        server.assertPacketType(packet, TilemanPacketType.AUTHENTICATION);
        connectionProfile = inputThread.waitForNextObject(server);

        if (server.getMultiplayerGroup().isMember(connectionProfile.getAccountHash())) {
            return true;
        } else if (server.isRequiresPassword()) {
            String hashedPassword = packet.message;
            if (!server.authenticatePassword(hashedPassword)) {
                return false;
            }
        }

        // add member to group
        server.getMultiplayerGroup().addMember(connectionProfile);
        return true;
    }

    private void sendAuthenticationResponse(boolean authenticated) throws IOException {
        outputQueue.queueData(
                TilemanPacket.createAuthenticationResponsePacket(authenticated),
                TilemanPacket.createEndOfDataPacket()
        );
        outputQueue.flush();
    }

    @Override
    protected void onShutdown() {
        try {
            if (!connection.isClosed()) {
                connection.close();
            }
        } catch (IOException e) {}

        server.removeConnection(connection);
        TilemanMultiplayerService.invokeMultiplayerStateChanged();
        if (inputThread != null) {
            inputThread.forceStop();
        }
    }


    @Override
    protected void onUpdate() throws NetworkShutdownException, NetworkTimeoutException {
        try {
            outputQueue.flush();

            TilemanPacket packet = inputThread.tryGetNextPacket();
            if (packet != null) {
                handlePacket(packet, inputThread);
            }
        } catch (UnexpectedPacketTypeException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected boolean handlePacket(TilemanPacket packet, ObjectInputStreamBufferThread input) throws IOException, ClassNotFoundException, NetworkShutdownException, InterruptedException, UnexpectedPacketTypeException, NetworkTimeoutException {
        if (super.handlePacket(packet, input)) {
            return true;
        }

        switch (packet.packetType) {
            case TILE_SYNC_REQUEST:
                handleTileSyncRequest();
                break;
            default:
                throw new IOException("Unexpected packet type in server: " + packet.packetType);
        }

        return true;
    }


    @Override
    protected void handleTileUpdate(TilemanPacket packet, ObjectInputStreamBufferThread input) throws InterruptedException, NetworkShutdownException, UnexpectedPacketTypeException, NetworkTimeoutException {
        TilemanModeTile tile = input.waitForNextObject(server);
        server.assertPacketType(input.waitForNextPacket(server), TilemanPacketType.END_OF_DATA);

        boolean state = Boolean.parseBoolean(packet.message);
        server.setTile(connectionProfile.getAccountHashLong(), tile, state);
    }

    private void handleTileSyncRequest() {
        server.tileDataByPlayer.keySet().stream().forEach(accountHash -> {
            if (!accountHash.equals(connectionProfile.getAccountHashLong())) {
                sendRegionHashReport(server.tileDataByPlayer.get(accountHash), accountHash);
            }
        });
    }
}
