package com.tileman.multiplayer.model;

import com.tileman.TilemanProfile;
import lombok.Value;

import java.awt.*;
import java.io.Serializable;

@Value
public class ProfileUpdateResponse implements Serializable {
    private final TilemanProfile profile;
    private final String name;
    private final Color color;

    public ProfileUpdateResponse(ProfileUpdateRequest request) {
        profile = request.getProfile();
        name = request.getName();
        color = request.getColor();
    }

    public TilemanProfile getUpdatedProfile() {
        profile.setColor(color);
        profile.setProfileName(name);
        return profile;
    }
}
