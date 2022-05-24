package com.tileman.multiplayer.server;

import com.tileman.GroupTileData;
import com.tileman.multiplayer.*;
import com.tileman.TilemanProfile;
import com.tileman.multiplayer.model.*;

import java.io.IOException;
import java.net.Socket;

public class TilemanServerConnectionHandler extends TilemanMultiplayerThread {

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
        } catch (UnexpectedNetworkObjectException e) {
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

    private boolean awaitAuthentication(ObjectInputStreamBufferThread inputThread) throws InterruptedException, UnexpectedNetworkObjectException, NetworkShutdownException, NetworkTimeoutException {
        AuthenticationRequest authenticationData = inputThread.waitForNextObject(this);

        this.connectionProfile = authenticationData.profile;

        if (server.isRequiresPassword()) {
            if (!server.authenticatePassword(authenticationData.getHashedPassword())) {
                return false;
            }
        }

        GroupTilemanProfile group = server.getGroupProfile();
        if (group.isMember(connectionProfile.getAccountHashLong())) {
            return true;
        }

        // if server is not accepting new members and not on preapproval list
        //return false

        server.addGroupMember(connectionProfile);
        return true;
    }

    private void sendAuthenticationResponse(boolean authenticated) throws IOException {
        outputQueue.queueData(new AuthenticationResponse(authenticated));
        outputQueue.flush();
    }

    private void handleGroupProfileSync(ObjectInputStreamBufferThread inputThread) throws NetworkShutdownException, UnexpectedNetworkObjectException, InterruptedException, NetworkTimeoutException, IOException {
        Object networkObject = inputThread.waitForNextObject(this);
        if (networkObject instanceof GroupProfileResponse) {
            GroupProfileResponse groupProfileResponse = (GroupProfileResponse)networkObject;
            server.updateGroupProfileIfNewer(groupProfileResponse.getGroupProfile(), connectionProfile.getAccountHashLong());
        } else if (!(networkObject instanceof GroupProfileRequest)) {
            throw new UnexpectedNetworkObjectException(networkObject);
        }
        sendGroupProfileResponse(server.getGroupProfile());
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
    protected void onUpdate() {
        try {
            outputQueue.flush();

            Object networkObject = inputThread.tryGetNextObject();
            if (networkObject != null) {
                handleNetworkObject(server.getGroupProfile(), networkObject);
            }
        } catch (UnexpectedNetworkObjectException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected boolean handleNetworkObject(GroupTilemanProfile groupProfile, Object networkObject) throws UnexpectedNetworkObjectException {
        if (super.handleNetworkObject(groupProfile, networkObject)) {
            return true;
        }

        if (networkObject instanceof TileSyncRequest) {
            handleTileSyncRequest((TileSyncRequest) networkObject, groupProfile.getGroupTileData());
        } else if (networkObject instanceof TileUpdateRequest) {
            handleTileUpdateRequest((TileUpdateRequest) networkObject);
        } else if (networkObject instanceof LeaveRequest) {
            handleLeaveRequest((LeaveRequest) networkObject);
        } else {
            throw new UnexpectedNetworkObjectException(networkObject);
        }

        return true;
    }

    protected void handleTileUpdateRequest(TileUpdateRequest tileUpdateRequest) {
        server.updateTile(tileUpdateRequest, connectionProfile.getAccountHashLong());
    }

    protected void handleLeaveRequest(LeaveRequest leaveRequest) {
        server.removeGroupMember(connectionProfile.getAccountHashLong());
    }

    private void handleTileSyncRequest(TileSyncRequest tileSyncRequest, GroupTileData groupTileData) {
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
