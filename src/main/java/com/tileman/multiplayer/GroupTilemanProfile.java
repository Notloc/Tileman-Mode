package com.tileman.multiplayer;

import com.tileman.GroupTileData;
import com.tileman.TilemanProfile;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

public class GroupTilemanProfile implements Serializable {
    public static GroupTilemanProfile NONE = new GroupTilemanProfile("__NONE", TilemanProfile.NONE);

    @Getter
    private final String groupName;
    private final String groupCreatorAccountHash;

    @Getter
    private final String multiplayerGroupId;

    private final List<TilemanProfile> groupMembers = new ArrayList<>();

    @Getter
    private LocalDateTime lastUpdated;

    @Getter @Setter
    private transient GroupTileData groupTileData;

    public GroupTilemanProfile(String groupName, TilemanProfile ownerProfile) {
        this.groupName = groupName;
        this.groupCreatorAccountHash = ownerProfile.getAccountHash();
        this.multiplayerGroupId = groupCreatorAccountHash + "_" + groupName;
        addMember(ownerProfile);
        lastUpdated = LocalDateTime.now(ZoneOffset.UTC);
    }

    public String getGroupCreatorAccountHash() {
        return groupCreatorAccountHash;
    }

    public long getGroupCreatorAccountHashLong() {
        return Long.parseLong(groupCreatorAccountHash);
    }

    public boolean addMember(TilemanProfile profile) {
        if (groupMembers.contains(profile)) {
            return false;
        }

        groupMembers.add(profile);
        lastUpdated = LocalDateTime.now(ZoneOffset.UTC);
        return true;
    }

    public void removeMember(long accountHash) {
        TilemanProfile profileToRemove = TilemanProfile.NONE;
        for (TilemanProfile profile : groupMembers) {
            if (profile.getAccountHashLong() == accountHash) {
                profileToRemove = profile;
                break;
            }
        }

        if (groupMembers.remove(profileToRemove)) {
            lastUpdated = LocalDateTime.now(ZoneOffset.UTC);
        }
    }

    public boolean isMember(long accountHash) {
        for (TilemanProfile profile : groupMembers) {
            if (profile.getAccountHashLong() == accountHash) {
                return true;
            }
        }
        return false;
    }

    public List<String> getGroupMemberAccountHashes() {
        return groupMembers.stream().map(profile -> profile.getAccountHash()).collect(Collectors.toList());
    }

    public TilemanProfile getGroupMemberProfile(long accountHash) {
        for (TilemanProfile profile : groupMembers) {
            if (profile.getAccountHashLong() == accountHash) {
                return profile;
            }
        }
        return null;
    }

    public String getGroupTilemanProfileKey() { return multiplayerGroupId + "_group"; }

    public void updateProfile(TilemanProfile profile) {
        for (TilemanProfile member : groupMembers) {
            if (profile.equals(member)) {
                member.setColor(profile.getColor());
                member.setProfileName(profile.getProfileName());
                return;
            }
        }
    }

    public void setProfileInstance(TilemanProfile profile) {
        for (int i = 0; i < groupMembers.size(); i++) {
            if (groupMembers.get(i).equals(profile)) {
                groupMembers.set(i, profile);
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof GroupTilemanProfile)) {
            return false;
        }
        GroupTilemanProfile other = (GroupTilemanProfile)o;
        return multiplayerGroupId.equals(other.multiplayerGroupId);
    }

    @Override
    public int hashCode() {
        return multiplayerGroupId.hashCode();
    }
}
