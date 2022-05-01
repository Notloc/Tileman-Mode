package com.tileman.multiplayer.server;

import com.tileman.GroupTileData;
import com.tileman.ProfileTileData;
import com.tileman.TilemanModeTile;
import com.tileman.TilemanProfile;
import com.tileman.managers.PersistenceManager;
import com.tileman.managers.ProfileTileDataUtil;
import com.tileman.multiplayer.GroupTilemanProfile;
import com.tileman.multiplayer.MpUtil;
import com.tileman.multiplayer.TilemanMultiplayerService;
import com.tileman.multiplayer.TilemanPacket;
import com.tileman.runelite.TilemanModeConfig;
import lombok.Getter;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


//TODO: make threadsafe
public class TilemanServer extends Thread {

    @Getter
    private GroupTilemanProfile groupProfile;
    private final PersistenceManager persistenceManager;

    @Getter
    private final int portNumber;
    private ServerSocket serverSocket;
    private final String hashedPassword;

    private final Set<TilemanServerConnectionHandler> activeConnections = ConcurrentHashMap.newKeySet();

    private boolean isShutdown;

    public TilemanServer(GroupTilemanProfile groupProfile, PersistenceManager persistenceManager, int portNumber, String password) {
        this.groupProfile = groupProfile;
        this.persistenceManager = persistenceManager;
        this.portNumber = portNumber;
        this.hashedPassword = MpUtil.sha512(password);
    }

    public void addGroupMember(TilemanProfile profile) {
        groupProfile.addMember(profile);
        persistenceManager.saveToJson(TilemanModeConfig.CONFIG_GROUP, groupProfile.getGroupTilemanProfileKey(), groupProfile);
    }



    public void updateGroupProfileIfNewer(GroupTilemanProfile incomingGroupProfile, long sender) {
        boolean incomingIsNewer = incomingGroupProfile.getLastUpdated().isAfter(groupProfile.getLastUpdated());
        if (incomingIsNewer && validateGroupChanges(groupProfile, incomingGroupProfile, sender)) {
            GroupTileData groupTileData = this.groupProfile.getGroupTileData();
            this.groupProfile = incomingGroupProfile;
            this.groupProfile.setGroupTileData(groupTileData);
            persistenceManager.saveToJson(TilemanModeConfig.CONFIG_GROUP, groupProfile.getGroupTilemanProfileKey(), groupProfile);

            //purgeInvalidMemberData();
            //reauthenticateConnections();
            //forwardGroupProfile()
        }
    }

    private boolean validateGroupChanges(GroupTilemanProfile current, GroupTilemanProfile incoming, long sender) {
        if (!incoming.getMultiplayerGroupId().equals(current.getMultiplayerGroupId())) {
            return false; // Different group?
        }

        if (incoming.getGroupCreatorAccountHash() == null || incoming.getGroupCreatorAccountHash().isEmpty()) {
            return false;
        }

        if (!current.getGroupCreatorAccountHash().equals(incoming.getGroupCreatorAccountHash()) && sender != current.getGroupCreatorAccountHashLong()) {
            return false; // Only allow the leader to relinquish group ownership themselves.
        }

        if (incoming.getGroupMemberAccountHashes() == null || incoming.getGroupMemberAccountHashes().isEmpty()) {
            return false;
        }

        return true;
    }


    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(portNumber);
            TilemanMultiplayerService.invokeMultiplayerStateChanged();
            handleNewConnections(serverSocket);
            if (!serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void shutdown() {
        isShutdown = true;
        try {
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        TilemanMultiplayerService.invokeMultiplayerStateChanged();
    }

    private void handleNewConnections(ServerSocket serverSocket) {
        while (!isShutdown()) {
            try {
                Socket connection = serverSocket.accept();
                startConnectionThread(connection);
            } catch (IOException e){
                e.printStackTrace();
            }
        }
    }

    private void startConnectionThread(Socket connection) {
        try {
            TilemanServerConnectionHandler connectionHandler = new TilemanServerConnectionHandler(this, connection);
            connectionHandler.start();
            activeConnections.add(connectionHandler);
        } catch (IOException e) {}
    }

    void updateTile(long accountHash, TilemanModeTile tile, boolean state) {
        ProfileTileData tileData = groupProfile.getGroupTileData().getProfileTileData(accountHash);
        if (state) {
            tileData.addTile(tile.getRegionId(), tile);
        } else {
            tileData.removeTile(tile.getRegionId(), tile);
        }

        forwardTileUpdate(accountHash, tile, state);
        ProfileTileDataUtil.saveRegion(accountHash, tile.getRegionId(), tileData.getRegion(tile.getRegionId()), persistenceManager);
    }

    private void forwardTileUpdate(long sender, TilemanModeTile tile, boolean state) {
        // Send the update to all connected players
        queueOutputForAllConnections(
                TilemanPacket.createTileUpdatePacket(state),
                sender,
                tile,
                TilemanPacket.createEndOfDataPacket()
        );
    }

    void queueOutputForAllConnections(Object... objects) {
        for (TilemanServerConnectionHandler connection : activeConnections) {
            queueOutputForConnection(connection, objects);
        }
    }

    private void queueOutputForConnection(TilemanServerConnectionHandler connection, Object... data) {
        // if connection is ready
            connection.queueData(data);
    }

    public boolean isRequiresPassword() {
        return hashedPassword != null && !hashedPassword.isEmpty();
    }

    public boolean authenticatePassword(String hashedPassword) {
        if (this.hashedPassword == null || this.hashedPassword.isEmpty()) {
            return true;
        }
        return this.hashedPassword.equals(hashedPassword);
    }

    public boolean isShutdown() {
        return isShutdown;
    }
}
