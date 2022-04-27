package com.tileman.multiplayer.shared;

import com.tileman.shared.TilemanProfile;

import java.io.Serializable;
import java.util.*;

public class GroupTilemanProfile implements Serializable {
    private final String groupName;
    private final String groupCreatorAccountHash;
    private final List<String> groupMemberAccountHashes = new ArrayList<>();
    private final Map<String, String> groupMemberNames = new HashMap<>();

    public GroupTilemanProfile(String groupName, long accountHash, String accountName) {
        this.groupName = groupName;
        this.groupCreatorAccountHash = String.valueOf(accountHash);
        addMember(accountHash, accountName);
    }

    public String getGroupCreatorAccountHash() {
        return groupCreatorAccountHash;
    }

    public boolean addMember(TilemanProfile profile) {
        return addMember(profile.getAccountHashLong(), profile.getProfileName());
    }
    public boolean addMember(long accountHash, String accountName) {
        String accountHashString = String.valueOf(accountHash);
        if (groupMemberAccountHashes.contains(accountHashString)) {
            return false;
        }

        groupMemberAccountHashes.add(accountHashString);
        groupMemberNames.put(accountHashString, accountName);
        return true;
    }

    public void removeMember(long accountHash) {
        String accountHashString = String.valueOf(accountHash);
        groupMemberAccountHashes.remove(accountHashString);
        groupMemberNames.remove(accountHashString);
    }

    public boolean isMember(long accountHash) {
        return isMember(String.valueOf(accountHash));
    }

    public boolean isMember(String accountHash) {
        return groupMemberAccountHashes.contains(accountHash);
    }

    public List<String> getGroupMemberAccountHashes() {
        return Collections.unmodifiableList(groupMemberAccountHashes);
    }
}
