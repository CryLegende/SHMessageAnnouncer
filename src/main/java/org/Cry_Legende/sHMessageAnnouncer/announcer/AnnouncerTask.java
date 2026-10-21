package org.Cry_Legende.sHMessageAnnouncer.announcer;

import org.Cry_Legende.sHMessageAnnouncer.SHMessageAnnouncer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class AnnouncerTask extends BukkitRunnable {

    private final SHMessageAnnouncer plugin;
    private final AnnouncementManager announcementManager;
    private int currentIndex = 0;

    public AnnouncerTask(SHMessageAnnouncer plugin) {
        this.plugin = plugin;
        this.announcementManager = plugin.getAnnouncementManager();
    }

    @Override
    public void run() {
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            return; // Don't announce to an empty server
        }

        List<String> activeAnnouncements = plugin.getConfigManager().getIntervalList();
        if (activeAnnouncements.isEmpty()) {
            return;
        }

        String announcementId;
        if (plugin.getConfigManager().isRandom()) {
            announcementId = activeAnnouncements.get(ThreadLocalRandom.current().nextInt(activeAnnouncements.size()));
        } else {
            if (currentIndex >= activeAnnouncements.size()) {
                currentIndex = 0;
            }
            announcementId = activeAnnouncements.get(currentIndex);
            currentIndex++;
        }

        // Send to all online players, parsing PAPI for each one individually
        for (Player player : Bukkit.getOnlinePlayers()) {
            announcementManager.sendAnnouncement(player, announcementId, player);
        }
    }
}
