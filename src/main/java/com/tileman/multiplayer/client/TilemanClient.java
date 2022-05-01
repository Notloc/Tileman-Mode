package com.tileman.multiplayer.client;

import com.tileman.GroupTileData;
import com.tileman.managers.TilemanStateManager;
import com.tileman.multiplayer.*;
import com.tileman.Util;
import com.tileman.TilemanModeTile;
import com.tileman.TilemanProfile;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

public class TilemanClient extends TilemanMultiplayerThread {

    private final TilemanStateManager stateManager;
    private final TilemanProfile profile;
    private /*final*/ GroupTilemanProfile groupProfile;

    private final String hostname;
    private final int portNumber;

    ObjectInputStreamBufferThread inputThread;
    Socket socket;



    private String password;

    public TilemanClient(TilemanStateManager stateManager, String hostname, int portNumber, String password) {
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

            sendAuthentication();
            if (!awaitAuthenticationResponse(inputThread)) {
                return false;
            }

            syncGroupProfile();
            if (this.groupProfile.equals(GroupTilemanProfile.NONE)) {
                return false;
            }

            requestTileSync(groupProfile.getGroupTileData());
            return true;

        } catch (UnknownHostException unknownHostException) {
            unknownHostException.printStackTrace();
        } catch (UnexpectedPacketTypeException unexpectedPacketTypeException) {
            unexpectedPacketTypeException.printStackTrace();
        } catch (IOException ioException) {
            ioException.printStackTrace();
        } catch (InterruptedException | NetworkShutdownException | NetworkTimeoutException interruptedException) {
            interruptedException.printStackTrace();
        }

        return false;
    }

    private void sendAuthentication() throws IOException {
        String hashedPassword = MpUtil.sha512(this.password);
        this.password = null;

        outputQueue.queueData(
                TilemanPacket.createAuthenticationPacket(hashedPassword),
                profile,
                TilemanPacket.createEndOfDataPacket()
        );
        outputQueue.flush();
    }

    private boolean awaitAuthenticationResponse(ObjectInputStreamBufferThread inputThread) throws InterruptedException, UnexpectedPacketTypeException, NetworkShutdownException, NetworkTimeoutException {
        TilemanPacket packet = inputThread.waitForNextPacket(this);
        assertPacketType(packet, TilemanPacketType.AUTHENTICATION_RESPONSE);
        assertPacketType(inputThread.waitForNextPacket(this), TilemanPacketType.END_OF_DATA);
        return Boolean.parseBoolean(packet.message);
    }

    // Get the latest group profile data from the server. In certain cases the leader will be uploading a newer version to the server/others.
    private void syncGroupProfile() throws NetworkShutdownException, UnexpectedPacketTypeException, InterruptedException, NetworkTimeoutException {
        if (!profile.isGroupTileman()) {
            requestGroupProfile();
        } else {
            sendGroupProfile(stateManager.getActiveGroupProfile());
        }

        GroupTilemanProfile groupProfile = handleGroupProfileResponse(inputThread);
        stateManager.assignGroupProfile(profile, groupProfile);
        this.groupProfile = groupProfile;
    }

    private void requestTileSync(GroupTileData groupTileData) throws IOException {
        // Ask for tiles from server
        outputQueue.queueData(
                TilemanPacket.createTileSyncRequest(),
                TilemanPacket.createEndOfDataPacket()
        );

        // Report state of my tiles to server
        long accountHash = profile.getAccountHashLong();
        sendRegionHashReport(groupTileData.getProfileTileData(accountHash), accountHash);
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
    protected void onUpdate() throws NetworkShutdownException, NetworkTimeoutException {
        try {
            outputQueue.flush();

            TilemanPacket packet = inputThread.tryGetNextPacket();
            if (packet != null) {
                handlePacket(groupProfile, packet, inputThread);
            }
        } catch (UnexpectedPacketTypeException unexpectedPacketTypeException) {
            unexpectedPacketTypeException.printStackTrace();
        } catch (IOException ioException) {
            ioException.printStackTrace();
        } catch (InterruptedException interruptedException) {
            interruptedException.printStackTrace();
        }
    }

    @Override
    protected boolean handlePacket(GroupTilemanProfile groupProfile, TilemanPacket packet, ObjectInputStreamBufferThread input) throws InterruptedException, NetworkShutdownException, UnexpectedPacketTypeException, NetworkTimeoutException {
        if (super.handlePacket(groupProfile, packet, input)) {
            return true;
        }

        //switch (packet.packetType) {
        //    default:
                //throw new UnexpectedPacketTypeException("Unexpected packet type in client: " + packet.packetType);
        //}

        return true;
    }

    public void sendTileUpdate(TilemanModeTile tile, boolean state) {
        outputQueue.queueData(
                TilemanPacket.createTileUpdatePacket(state),
                tile,
                TilemanPacket.createEndOfDataPacket()
        );
    }

    @Override
    protected void handleTileUpdate(GroupTileData groupTileData, TilemanPacket packet, ObjectInputStreamBufferThread input) throws InterruptedException, NetworkShutdownException, UnexpectedPacketTypeException, NetworkTimeoutException {
        boolean tileState = Boolean.parseBoolean(packet.message);
        long accountHash = input.waitForNextObject(this);
        TilemanModeTile tile = input.waitForNextObject(this);
        assertPacketType(input.waitForNextPacket(this), TilemanPacketType.END_OF_DATA);

        groupTileData.setTile(accountHash, tile, tileState);
    }

    public void disconnect() {
        isShuttingDown = true;
    }
}