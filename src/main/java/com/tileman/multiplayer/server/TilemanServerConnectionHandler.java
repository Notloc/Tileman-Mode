package com.tileman.multiplayer.server;

import com.tileman.GroupTileData;
import com.tileman.TilemanModeTile;
import com.tileman.managers.GroupTilemanProfileUtil;
import com.tileman.multiplayer.*;
import com.tileman.TilemanProfile;

import java.io.IOException;
import java.net.Socket;

class TilemanServerConnectionHandler extends TilemanMultiplayerThread {

    private final TilemanServer server;
    private final Socket connection;

    private final GroupTilemanProfileUtil profileManager;

    private TilemanProfile connectionProfile;
    private ObjectInputStreamBufferThread inputThread;

    public TilemanServerConnectionHandler(TilemanServer server, Socket connection) throws IOException {
        this.server = server;
        this.connection = connection;
        this.profileManager = server.getGroupProfileManager();
        this.outputQueue = new ConcurrentOutputQueue<>(connection.getOutputStream());
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
        TilemanPacket packet = inputThread.waitForNextPacket(this);
        assertPacketType(packet, TilemanPacketType.AUTHENTICATION);
        connectionProfile = inputThread.waitForNextObject(this);

        if (profileManager.isMember(connectionProfile.getAccountHashLong())) {
            return true;
        } else if (server.isRequiresPassword()) {
            String hashedPassword = packet.message;
            if (!server.authenticatePassword(hashedPassword)) {
                return false;
            }
        }

        profileManager.addMember(connectionProfile);
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
        } catch (IOException e) {
            // Do nothing
        }

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
                handlePacket(server.getGroupTileData(), packet, inputThread);
            }
        } catch (UnexpectedPacketTypeException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected boolean handlePacket(GroupTileData groupTileData, TilemanPacket packet, ObjectInputStreamBufferThread input) throws NetworkShutdownException, InterruptedException, UnexpectedPacketTypeException, NetworkTimeoutException {
        if (super.handlePacket(groupTileData, packet, input)) {
            return true;
        }

        switch (packet.packetType) {
            case TILE_SYNC_REQUEST:
                handleTileSyncRequest(groupTileData);
                break;
            default:
                throw new UnexpectedPacketTypeException("Unexpected packet type in server: " + packet.packetType);
        }

        return true;
    }


    @Override
    protected void handleTileUpdate(GroupTileData groupTileData, TilemanPacket packet, ObjectInputStreamBufferThread input) throws InterruptedException, NetworkShutdownException, UnexpectedPacketTypeException, NetworkTimeoutException {
        TilemanModeTile tile = input.waitForNextObject(this);
        assertPacketType(input.waitForNextPacket(this), TilemanPacketType.END_OF_DATA);
        boolean state = Boolean.parseBoolean(packet.message);
        long accountHash = connectionProfile.getAccountHashLong();

        groupTileData.setTile(accountHash, tile, state);
        server.forwardTileUpdate(accountHash, tile, state);
    }

    private void handleTileSyncRequest(GroupTileData groupTileData) {
        Long myAccount = connectionProfile.getAccountHashLong();
        groupTileData.forEachProfile(accountHash -> {
            if (!accountHash.equals(myAccount)) {
                sendRegionHashReport(groupTileData.getProfileTileData(accountHash), accountHash);
            }
        });
    }

    public void queueData(Object... data) {
        outputQueue.queueData(data);
    }
}
