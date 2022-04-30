package com.tileman.multiplayer.client;

import com.tileman.TilemanModePlugin;
import com.tileman.TilemanProfileManager;
import com.tileman.Util;
import com.tileman.shared.TilemanModeTile;
import com.tileman.multiplayer.shared.*;
import com.tileman.shared.TilemanProfile;
import net.runelite.api.Client;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

public class TilemanClient extends TilemanMultiplayerThread {

    private final TilemanModePlugin plugin;

    private final String hostname;
    private final int portNumber;

    ObjectInputStreamBufferThread inputThread;
    Socket socket;

    private TilemanProfile profile;
    private String password;

    public TilemanClient(TilemanModePlugin plugin, TilemanProfileManager profileManager, String hostname, int portNumber, String password) {
        this.plugin = plugin;
        this.profile = profileManager.getActiveProfile();

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

            // Handle joining group case

            requestTileSync(groupTileData);
            return true;

        } catch (UnknownHostException unknownHostException) {
            unknownHostException.printStackTrace();
        } catch (UnexpectedPacketTypeException unexpectedPacketTypeException) {
            unexpectedPacketTypeException.printStackTrace();
        } catch (IOException ioException) {
            ioException.printStackTrace();
        } catch (InterruptedException interruptedException) {
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

    private boolean awaitAuthenticationResponse(ObjectInputStreamBufferThread inputThread) throws InterruptedException, UnexpectedPacketTypeException {
        while (!isShutdown()) {
            TilemanPacket packet = inputThread.tryGetNextPacket();
            if (packet != null) {
                assertPacketType(packet, TilemanPacketType.AUTHENTICATION_RESPONSE);
                return Boolean.parseBoolean(packet.message);
            }
            sleep(100);
        }
        return false;
    }

    private void requestTileSync(GroupTileData groupTileData) throws IOException {
        // Ask for tiles from server
        outputQueue.queueData(
                TilemanPacket.createTileSyncRequest(),
                TilemanPacket.createEndOfDataPacket()
        );

        // Report state of my tiles to server
        long accountHash = profile.getAccountHashLong();
        sendRegionHashReport(groupTileData.getAccountData(accountHash), accountHash);
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
    protected void onUpdate() throws NetworkShutdownException, NetworkTimeoutException{
        try {
            outputQueue.flush();

            TilemanPacket packet = inputThread.tryGetNextPacket();
            if (packet != null) {
                handlePacket(groupTileData, packet, inputThread);
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
    protected boolean handlePacket(GroupTileData groupTileData, TilemanPacket packet, ObjectInputStreamBufferThread input) throws InterruptedException, NetworkShutdownException, UnexpectedPacketTypeException, NetworkTimeoutException {
        if (super.handlePacket(groupTileData, packet, input)) {
            return true;
        }

        switch (packet.packetType) {
            default:
                //throw new UnexpectedPacketTypeException("Unexpected packet type in client: " + packet.packetType);
        }

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