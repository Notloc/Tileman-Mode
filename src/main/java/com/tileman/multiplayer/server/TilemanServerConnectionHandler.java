package com.tileman.multiplayer.server;

import com.tileman.GroupTileData;
import com.tileman.TilemanModeTile;
import com.tileman.managers.GroupTilemanProfileUtil;
import com.tileman.managers.TilemanProfileUtil;
import com.tileman.multiplayer.*;
import com.tileman.TilemanProfile;

import java.io.IOException;
import java.net.Socket;

class TilemanServerConnectionHandler extends TilemanMultiplayerThread {

    private final TilemanServer server;
    private final Socket connection;


    private TilemanProfile connectionProfile;
    private ObjectInputStreamBufferThread inputThread;

    public TilemanServerConnectionHandler(TilemanServer server, Socket connection) throws IOException {
        this.server = server;
        this.connection = connection;
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
            if (!authenticated) {
                return false;
            }

            handleGroupProfileSync(inputThread);
            return true;

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
        assertPacketType(inputThread.waitForNextPacket(this), TilemanPacketType.END_OF_DATA);

        GroupTilemanProfile group = server.getGroupProfile();

         if (server.isRequiresPassword()) {
            String hashedPassword = packet.message;
            if (!server.authenticatePassword(hashedPassword)) {
                return false;
            }
        }

        if (group.isMember(connectionProfile.getAccountHashLong())) {
            return true;
        }

        // if server is not accepting new members and not on preapproval list
            //return false

        server.addGroupMember(connectionProfile);
        return true;
    }

    private void sendAuthenticationResponse(boolean authenticated) throws IOException {
        outputQueue.queueData(
                TilemanPacket.createAuthenticationResponsePacket(authenticated),
                TilemanPacket.createEndOfDataPacket()
        );
        outputQueue.flush();
    }

    private void handleGroupProfileSync(ObjectInputStreamBufferThread inputThread) throws NetworkShutdownException, UnexpectedPacketTypeException, InterruptedException, NetworkTimeoutException, IOException {
        TilemanPacket packet = inputThread.waitForNextPacket(this);
        if (packet.packetType == TilemanPacketType.GROUP_PROFILE_REQUEST) {
            assertPacketType(inputThread.waitForNextPacket(this), TilemanPacketType.END_OF_DATA);
        } else if (packet.packetType == TilemanPacketType.GROUP_PROFILE_RESPONSE) {
            GroupTilemanProfile incomingGroupProfile = inputThread.waitForNextObject(this);
            assertPacketType(inputThread.waitForNextPacket(this), TilemanPacketType.END_OF_DATA);
            server.updateGroupProfileIfNewer(incomingGroupProfile, connectionProfile.getAccountHashLong());
        }
        sendGroupProfile(server.getGroupProfile());
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
                handlePacket(server.getGroupProfile(), packet, inputThread);
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
    protected boolean handlePacket(GroupTilemanProfile groupProfile, TilemanPacket packet, ObjectInputStreamBufferThread input) throws NetworkShutdownException, InterruptedException, UnexpectedPacketTypeException, NetworkTimeoutException {
        if (super.handlePacket(groupProfile, packet, input)) {
            return true;
        }

        switch (packet.packetType) {
            case TILE_SYNC_REQUEST:
                handleTileSyncRequest(groupProfile.getGroupTileData(), input);
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

        server.updateTile(accountHash, tile, state);
    }

    @Override
    protected void handleLeaveEvent(GroupTileData groupTileData, TilemanPacket leavePacket, ObjectInputStreamBufferThread input) throws InterruptedException, NetworkShutdownException, UnexpectedPacketTypeException, NetworkTimeoutException {
        assertPacketType(input.waitForNextPacket(this), TilemanPacketType.END_OF_DATA);
        Long accountHash = Long.parseLong(leavePacket.message);
        server.removeGroupMember(accountHash);
    }

    private void handleTileSyncRequest(GroupTileData groupTileData, ObjectInputStreamBufferThread input) throws NetworkShutdownException, UnexpectedPacketTypeException, InterruptedException, NetworkTimeoutException {
        assertPacketType(input.waitForNextPacket(this), TilemanPacketType.END_OF_DATA);

        Long myAccount = connectionProfile.getAccountHashLong();
        groupTileData.forEachProfile((accountHash, tileData) -> {
            if (!accountHash.equals(myAccount)) {
                try {
                    sendRegionHashReport(tileData, accountHash);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void queueData(Object... data) {
        outputQueue.queueData(data);
    }
}
