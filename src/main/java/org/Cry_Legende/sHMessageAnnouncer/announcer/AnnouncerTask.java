package org.Cry_Legende.sHMessageAnnouncer.announcer;

import org.Cry_Legende.sHMessageAnnouncer.SHMessageAnnouncer;
import org.Cry_Legende.sHMessageAnnouncer.network.NetworkAnnouncer;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class AnnouncerTask extends BukkitRunnable {

    private final SHMessageAnnouncer plugin;
    private final AnnouncementManager announcementManager;
    private int currentIndex;

    public AnnouncerTask(SHMessageAnnouncer plugin) {
        this(plugin, 0);
    }

    /**
     * @param startIndex rang de la première annonce de la liste (en réseau, un nouveau meneur reprend
     *                   l'ordre là où le précédent s'est arrêté)
     */
    public AnnouncerTask(SHMessageAnnouncer plugin, int startIndex) {
        this.plugin = plugin;
        this.announcementManager = plugin.getAnnouncementManager();
        this.currentIndex = startIndex;
    }

    @Override
    public void run() {
        // Avec SHNetwork, ce minuteur ne tourne que sur le serveur meneur et compte les joueurs de tout le réseau
        NetworkAnnouncer network = plugin.getNetwork();
        if (network != null ? !network.anyoneOnline() : Bukkit.getOnlinePlayers().isEmpty()) {
            return; // Don't announce to an empty server
        }

        List<String> activeAnnouncements = plugin.getConfigManager().getIntervalList();
        if (activeAnnouncements.isEmpty()) {
            return;
        }

        String announcementId;
        boolean random = plugin.getConfigManager().isRandom();
        if (random) {
            announcementId = activeAnnouncements.get(ThreadLocalRandom.current().nextInt(activeAnnouncements.size()));
        } else {
            if (currentIndex >= activeAnnouncements.size()) {
                currentIndex = 0;
            }
            announcementId = activeAnnouncements.get(currentIndex);
            currentIndex++;
        }

        if (network != null) {
            // Le meneur l'affiche ici et la publie : chaque serveur l'envoie à ses joueurs
            network.publishTimer(announcementId, random ? -1 : currentIndex);
            return;
        }

        // Send to all online players, parsing PAPI for each one individually
        announcementManager.broadcast(announcementId, null);
    }
}
