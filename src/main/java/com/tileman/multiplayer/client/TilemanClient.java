package com.tileman.multiplayer.client;

import com.tileman.GroupTileData;
import com.tileman.managers.TilemanStateManager;
import com.tileman.multiplayer.*;
import com.tileman.Util;
import com.tileman.TilemanModeTile;
import com.tileman.TilemanProfile;
import com.tileman.multiplayer.model.*;
import com.tileman.runelite.TilemanModePlugin;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

public class TilemanClient extends TilemanMultiplayerThread {

    private final TilemanModePlugin plugin;
    private final TilemanStateManager stateManager;
    private final TilemanProfile profile;
    private /*final*/ GroupTilemanProfile groupProfile;

    private final String hostname;
    private final int portNumber;

    ObjectInputStreamBufferThread inputThread;
    Socket socket;

    private String password;

    public TilemanClient(TilemanModePlugin plugin, TilemanStateManager stateManager, String hostname, int portNumber, String password) {
        this.plugin = plugin;
        this.stateManager = stateManager;
        this.profile = stateManager.getActiveProfile();

        this.hostname = hostname;
        this.portNumber = portNumber;
        this.password = password;
    }

    @Override
    protected boolean onStart() {
        System.out.println("Launching MP client");
        if (Util.isEmpty(hostname) || profile.equals(TilemanProfile.NONE)) {
            return false;
        }

        TilemanMultiplayerService.invokeMultiplayerStateChanged();
        try {
            socket = new Socket(hostname, portNumber);
            outputQueue = new ConcurrentOutputQueue<>(socket.getOutputStream());
            inputThread = new ObjectInputStreamBufferThread(socket.getInputStream());
            inputThread.start();

            sendAuthenticationRequest();
            if (!awaitAuthenticationResponse(inputThread)) {
                return false;
            }

            syncGroupProfile();
            if (this.groupProfile.equals(GroupTilemanProfile.NONE)) {
                return false;
            }

            sendTileSyncRequest(groupProfile.getGroupTileData());
            return true;

        } catch (UnknownHostException unknownHostException) {
            unknownHostException.printStackTrace();
        } catch (UnexpectedNetworkObjectException unexpectedPacketTypeException) {
            unexpectedPacketTypeException.printStackTrace();
        } catch (IOException ioException) {
            ioException.printStackTrace();
        } catch (InterruptedException | NetworkShutdownException | NetworkTimeoutException interruptedException) {
            interruptedException.printStackTrace();
        }

        return false;
    }

    private void sendAuthenticationRequest() throws IOException {
        String hashedPassword = MpUtil.sha512(this.password);
        this.password = null;

        outputQueue.queueData(new AuthenticationRequest(profile, hashedPassword));
        outputQueue.flush();
    }

    private boolean awaitAuthenticationResponse(ObjectInputStreamBufferThread inputThread) throws InterruptedException, UnexpectedNetworkObjectException, NetworkShutdownException, NetworkTimeoutException {
        AuthenticationResponse authenticationResponse = inputThread.waitForNextObject(this);
        return authenticationResponse.authenticationSuccessful;
    }

    // Get the latest group profile data from the server. In certain cases the leader will be uploading a newer version to the server/others.
    private void syncGroupProfile() throws NetworkShutdownException, UnexpectedNetworkObjectException, InterruptedException, NetworkTimeoutException, IOException {
        if (profile.isGroupTileman()) {
            sendGroupProfileResponse(stateManager.getActiveGroupProfile());
        } else {
            sendGroupProfileRequest();
        }

        GroupTilemanProfile groupProfile = waitForGroupProfileResponse(inputThread);
        stateManager.assignGroupProfile(profile, groupProfile);
        this.groupProfile = groupProfile;
    }

    private void sendTileSyncRequest(GroupTileData groupTileData) throws IOException {
        // Ask for tiles from server
        outputQueue.queueData(new TileSyncRequest());
        outputQueue.flush();

        // Report state of my tiles to server
        long accountHash = profile.getAccountHashLong();
        sendRegionHashReport(groupTileData.getProfileTileData(accountHash), accountHash);
    }

    public void sendTileUpdateRequest(TilemanModeTile tile, boolean state) {
        outputQueue.queueData(new TileUpdateRequest(tile, state));
    }

    @Override
    protected void onShutdown() {
        System.out.println("Closing multiplayer client!");
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {}
        TilemanMultiplayerService.invokeMultiplayerStateChanged();
    }

    @Override
    protected void onUpdate() {
        try {
            outputQueue.flush();

            Object networkObject = inputThread.tryGetNextObject();
            if (networkObject != null) {
                handleNetworkObject(groupProfile, networkObject);
            }
        } catch (UnexpectedNetworkObjectException unexpectedPacketTypeException) {
            unexpectedPacketTypeException.printStackTrace();
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    @Override
    protected boolean handleNetworkObject(GroupTilemanProfile groupProfile, Object networkObject) throws UnexpectedNetworkObjectException {
        if (super.handleNetworkObject(groupProfile, networkObject)) {
            return true;
        }

        if (networkObject instanceof JoinResponse) {
            handleJoinResponse((JoinResponse) networkObject);
        } else if (networkObject instanceof TileUpdateResponse) {
            handleTileUpdateResponse((TileUpdateResponse) networkObject, groupProfile.getGroupTileData());
        } else if (networkObject instanceof LeaveResponse) {
            handleLeaveResponse((LeaveResponse) networkObject, groupProfile.getGroupTileData());
        } else {
            throw new UnexpectedNetworkObjectException(networkObject);
        }

        return true;
    }

    private void handleTileUpdateResponse(TileUpdateResponse tileUpdateResponse, GroupTileData groupTileData) {
        groupTileData.setTile(tileUpdateResponse.getAccount(), tileUpdateResponse.getTile(), tileUpdateResponse.isTileState());
        TilemanMultiplayerService.updatedRegionIds.add(tileUpdateResponse.getTile().getRegionId());
    }

    protected void handleLeaveResponse(LeaveResponse leaveResponse, GroupTileData groupTileData) {
        groupTileData.removeProfile(leaveResponse.getAccountHash());
        plugin.requestUpdateAllVisiblePoints();
    }

    private void handleJoinResponse(JoinResponse joinResponse) {
        plugin.requestUpdateAllVisiblePoints();
    }

    public void leaveGroup() {
        outputQueue.queueData(new LeaveRequest());
        try {
            outputQueue.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        disconnect();
    }

    public void disconnect() {
        isShuttingDown = true;
    }
}