package com.tileman.multiplayer.shared;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TilemanGroupProfile implements Serializable {
    private String groupName;
    private String groupCreatorAccountHash;
    private List<String> groupMemberAccountHashes = new ArrayList<>();
    private Map<String, String> groupMemberNames = new HashMap<>();

    public TilemanGroupProfile(String groupName, long accountHash, String accountName) {
        this.groupName = groupName;
        this.groupCreatorAccountHash = String.valueOf(accountHash);
        addMember(accountHash, accountName);
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
        return groupMemberAccountHashes.contains(String.valueOf(accountHash));
    }
}

