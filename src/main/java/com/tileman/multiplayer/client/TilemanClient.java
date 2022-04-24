package com.tileman.multiplayer.client;

import com.tileman.TilemanModePlugin;
import com.tileman.TilemanProfileManager;
import com.tileman.Util;
import com.tileman.shared.TilemanModeTile;
import com.tileman.multiplayer.shared.*;
import com.tileman.shared.TilemanProfile;
import lombok.Getter;
import net.runelite.api.Client;

import java.io.IOException;
import java.net.Socket;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class TilemanClient extends NetworkedThread {

    private final Client client;
    private final TilemanModePlugin plugin;

    private final String hostname;
    private final int portNumber;

    @Getter
    private final ConcurrentSetMap<Integer, TilemanModeTile> multiplayerTileData = new ConcurrentSetMap<>();
    private ConcurrentOutputQueue<Object> outputQueue;
    private TilemanClientState clientState;

    private TilemanProfile profile;
    private String password;

    public TilemanClient(Client client, TilemanModePlugin plugin, TilemanProfileManager profileManager, String hostname, int portNumber, String password) {
        this.client = client;
        this.plugin = plugin;
        this.profile = profileManager.getActiveProfile();

        this.hostname = hostname;
        this.portNumber = portNumber;
        this.clientState = TilemanClientState.CONNECTING;
        this.password = password;
    }

    public TilemanClientState getClientState() { return clientState; }

    @Override
    public void run() {
        System.out.println("Launching MP client");
        if (Util.isEmpty(hostname)) {
            return;
        }


        if (profile.equals(TilemanProfile.NONE)) {
            return;
        }

        try {
            Socket socket = new Socket(hostname, portNumber);

            outputQueue = new ConcurrentOutputQueue<>(socket.getOutputStream());
            ObjectInputStreamBufferThread inputThread = new ObjectInputStreamBufferThread(socket.getInputStream());
            inputThread.start();

            sendAuthentication();
            clientState = TilemanClientState.AUTHENTICATING;
            if (!awaitAuthenticationResponse(inputThread)) {
                return;
            }

            clientState = TilemanClientState.SYNCING;
            TilemanMultiplayerService.invokeMultiplayerStateChanged();

            uploadTileDataToServer(plugin.getTilesByRegion());

            clientState = TilemanClientState.CONNECTED;
            TilemanMultiplayerService.invokeMultiplayerStateChanged();

            requestRegionData(client.getMapRegions());
            while (!isShutdown()) {
                outputQueue.flush();

                TilemanPacket packet = inputThread.tryGetNextPacket();
                if (packet != null) {
                    handlePacket(packet, inputThread);
                }

                sleep(50);
            }
            System.out.println("Closing!");
            socket.close();
        } catch (NetworkShutdownException e) {
            // Do nothing
        }
        catch (IOException | ClassNotFoundException | InterruptedException | UnexpectedPacketTypeException e) {
            e.printStackTrace();
        } finally {
            clientState = TilemanClientState.DISCONNECTED;
            TilemanMultiplayerService.invokeMultiplayerStateChanged();
        }
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

    private void uploadTileDataToServer(Map<Integer, List<TilemanModeTile>> tileData) throws IOException {
         for (Integer regionId : tileData.keySet()) {
             outputQueue.queueData(
                 TilemanPacket.createRegionDataResponse(regionId),
                 tileData.get(regionId),
                 TilemanPacket.createEndOfDataPacket()
             );
         }
        outputQueue.flush();
    }

    private void handlePacket(TilemanPacket packet, ObjectInputStreamBufferThread input) throws IOException, ClassNotFoundException, InterruptedException, NetworkShutdownException, UnexpectedPacketTypeException {
        switch (packet.packetType) {
            case REGION_DATA_RESPONSE:
                handleIncomingRegionData(packet, input);
                break;
            case TILE_UPDATE:
                handleTileUpdate(packet, input);
                break;
            default:
                throw new IOException("Unexpected packet type in client: " + packet.packetType);
        }
    }

    private void handleIncomingRegionData(TilemanPacket packet, ObjectInputStreamBufferThread input) throws InterruptedException, NetworkShutdownException, UnexpectedPacketTypeException {
        int regionId = Integer.parseInt(packet.message);
        while (!isShutdown()) {
            Object object = input.waitForData(this);
            if (object instanceof List) {
                List<TilemanModeTile> tiles = (List<TilemanModeTile>) object;
                multiplayerTileData.addAll(regionId, tiles);
            } else {
                validateEndOfDataPacket(object);
                break;
            }
        }
    }

    private void handleTileUpdate(TilemanPacket packet, ObjectInputStreamBufferThread input) throws InterruptedException, NetworkShutdownException, UnexpectedPacketTypeException {
        Object object = input.waitForData(this);
        TilemanModeTile tile = (TilemanModeTile)object;
        validateEndOfDataPacket(input.waitForData(this));

        if (multiplayerTileData.containsKey(tile.getRegionId())) {
            boolean tileState = Boolean.parseBoolean(packet.message);
            if (tileState) {
                multiplayerTileData.add(tile.getRegionId(), tile);
            } else {
                multiplayerTileData.remove(tile.getRegionId(), tile);
            }
        }
    }

    public void requestRegionData(Collection<Integer> regionIds) {
        for (int regionId : regionIds) {
            outputQueue.queueData(
                TilemanPacket.createRegionDataRequest(regionId),
                TilemanPacket.createEndOfDataPacket()
            );
        }
    }

    public void requestRegionData(int[] regionIds) {
        for (int regionId : regionIds) {
            outputQueue.queueData(
                    TilemanPacket.createRegionDataRequest(regionId),
                    TilemanPacket.createEndOfDataPacket()
            );
        }
    }

    public void sendTileUpdate(TilemanModeTile tile, boolean state) {
        outputQueue.queueData(
            TilemanPacket.createTileUpdatePacket(state),
            tile,
            TilemanPacket.createEndOfDataPacket()
        );
    }

    public void disconnect() {
        isShuttingDown = true;
    }
}