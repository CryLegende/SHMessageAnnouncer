package org.Cry_Legende.sHMessageAnnouncer.announcement;

import net.kyori.adventure.text.Component;

import java.util.List;

public record Announcement(String id, List<Component> lines) {
    // A record is a modern, concise way to create an immutable data holder class.
}
