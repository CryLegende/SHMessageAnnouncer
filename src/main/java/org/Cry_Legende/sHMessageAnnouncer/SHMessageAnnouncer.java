package org.Cry_Legende.sHMessageAnnouncer;

import org.Cry_Legende.sHMessageAnnouncer.announcer.AnnouncerTask;
import org.Cry_Legende.sHMessageAnnouncer.announcer.AnnouncementManager;
import org.Cry_Legende.sHMessageAnnouncer.command.AnnouncerCommand;
import org.Cry_Legende.sHMessageAnnouncer.config.ConfigManager;
import org.Cry_Legende.sHMessageAnnouncer.config.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class SHMessageAnnouncer extends JavaPlugin {

    private ConfigManager configManager;
    private MessageManager messageManager;
    private AnnouncementManager announcementManager;
    private AnnouncerTask announcerTask;

    @Override
    public void onEnable() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            getLogger().severe("PlaceholderAPI not found! This plugin is required.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        this.configManager = new ConfigManager(this);
        this.messageManager = new MessageManager(this);
        this.announcementManager = new AnnouncementManager(this);

        loadPlugin(false);

        Objects.requireNonNull(getCommand("msa")).setExecutor(new AnnouncerCommand(this));
        getLogger().info("SHMessageAnnouncer has been enabled!");
    }

    @Override
    public void onDisable() {
        stopAnnouncer();
        getLogger().info("SHMessageAnnouncer has been disabled!");
    }

    public void loadPlugin(boolean isReload) {
        if (isReload) {
            stopAnnouncer();
        }
        configManager.load();
        messageManager.load();
        announcementManager.loadAnnouncements();

        if (configManager.isAnnouncerEnabledOnStart()) {
            startAnnouncer();
        }
    }

    public void startAnnouncer() {
        if (announcerTask != null && !announcerTask.isCancelled()) {
            return; // Déjà en cours
        }
        if (configManager.getIntervalList().isEmpty()) {
            getLogger().warning("Announcer interval list is empty. Auto-announcer will not start.");
            return;
        }

        long intervalTicks = Math.max(1, configManager.getAnnounceInterval()) * 20L;
        announcerTask = new AnnouncerTask(this);
        announcerTask.runTaskTimer(this, intervalTicks, intervalTicks);
    }

    public void stopAnnouncer() {
        if (announcerTask != null) {
            announcerTask.cancel();
            announcerTask = null;
        }
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public AnnouncementManager getAnnouncementManager() {
        return announcementManager;
    }

    public AnnouncerTask getAnnouncerTask() {
        return announcerTask;
    }
}