package org.Cry_Legende.sHMessageAnnouncer;

import org.Cry_Legende.sHMessageAnnouncer.announcer.AnnouncerTask;
import org.Cry_Legende.sHMessageAnnouncer.announcer.AnnouncementManager;
import org.Cry_Legende.sHMessageAnnouncer.command.AnnouncerCommand;
import org.Cry_Legende.sHMessageAnnouncer.config.ConfigManager;
import org.Cry_Legende.sHMessageAnnouncer.config.MessageManager;
import org.Cry_Legende.sHMessageAnnouncer.network.NetBus;
import org.Cry_Legende.sHMessageAnnouncer.network.NetworkAnnouncer;
import org.Cry_Legende.sHMessageAnnouncer.network.ShNetworkBus;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.logging.Level;

public final class SHMessageAnnouncer extends JavaPlugin {

    private ConfigManager configManager;
    private MessageManager messageManager;
    private AnnouncementManager announcementManager;
    private AnnouncerTask announcerTask;
    // Non nul seulement avec SHNetwork : une seule rotation d'annonces pour tout le réseau
    private NetworkAnnouncer network;

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

        // Avant le premier chargement : avec SHNetwork, seul le meneur lance le minuteur
        saveDefaultConfig();
        enableNetwork();
        loadPlugin(false);

        AnnouncerCommand command = new AnnouncerCommand(this);
        PluginCommand announcer = Objects.requireNonNull(getCommand("shmessageannouncer"));
        announcer.setExecutor(command);
        announcer.setTabCompleter(command);
    }

    @Override
    public void onDisable() {
        if (network != null) {
            network.disable();
        }
        stopTask();
    }

    /**
     * Passe en mode réseau si SHNetwork est installé et 'reseau.actif' est vrai. Les classes qui touchent à SHNetwork
     * ne sont résolues qu'ici : sans lui, rien n'est chargé et le plugin garde son fonctionnement d'origine.
     */
    private void enableNetwork() {
        if (!getConfig().getBoolean("reseau.actif", true)) return;
        if (!Bukkit.getPluginManager().isPluginEnabled("SHNetwork")) return;
        try {
            NetBus bus = ShNetworkBus.open();
            NetworkAnnouncer announcer = new NetworkAnnouncer(this, bus, getConfig().getString("reseau.canal", "messageannouncer"));
            announcer.enable();
            network = announcer;
            getLogger().info("SHNetwork détecté : une seule rotation d'annonces pour tout le réseau.");
        } catch (RuntimeException | LinkageError e) {
            network = null;
            getLogger().log(Level.SEVERE, "SHNetwork présent mais inutilisable, annonces locales à ce serveur : " + e);
        }
    }

    /**
     * {@code /shmessageannouncer reload} : config.yml et messages.yml sont lus et validés avant toute application. Si
     * l'un est invalide, rien ne change (minuteur compris) et l'expéditeur reçoit le fichier et l'erreur. Ne vaut que
     * pour ce serveur ; reseau.actif et reseau.canal demandent un redémarrage.
     *
     * @return vrai si tout a été rechargé
     */
    public boolean reload(CommandSender sender) {
        saveDefaultConfig();
        for (String name : new String[]{"config.yml", "messages.yml"}) {
            try {
                new YamlConfiguration().load(new File(getDataFolder(), name));
            } catch (IOException | InvalidConfigurationException e) {
                getLogger().severe("Reload cancelled, " + name + " is invalid: " + e.getMessage());
                String text = String.valueOf(e.getMessage()).strip();
                int end = text.indexOf('\n');
                String error = end < 0 ? text : text.substring(0, end).strip();
                messageManager.sendMessage(sender, "reload_failed", Placeholder.unparsed("file", name),
                    Placeholder.unparsed("error", error.length() > 160 ? error.substring(0, 160) + "..." : error));
                return false;
            }
        }
        loadPlugin(true);
        return true;
    }

    public void loadPlugin(boolean isReload) {
        if (isReload) {
            stopTask();
        }
        configManager.load();
        messageManager.load();
        announcementManager.loadAnnouncements();

        if (network != null) {
            network.configLoaded(configManager.isAnnouncerEnabledOnStart());
        } else if (configManager.isAnnouncerEnabledOnStart()) {
            startTask(0);
        }
    }

    /** {@code /shmessageannouncer start} : avec SHNetwork, les annonces démarrent sur tout le réseau. */
    public void startAnnouncer() {
        if (network != null) {
            network.setRunning(true);
            return;
        }
        startTask(0);
    }

    /** {@code /shmessageannouncer stop} : avec SHNetwork, les annonces s'arrêtent sur tout le réseau. */
    public void stopAnnouncer() {
        if (network != null) {
            network.setRunning(false);
            return;
        }
        stopTask();
    }

    public boolean isAnnouncerRunning() {
        if (network != null) {
            return network.isRunning();
        }
        return announcerTask != null && !announcerTask.isCancelled();
    }

    /**
     * Lance le minuteur de ce serveur. Seul serveur concerné avec SHNetwork : le meneur.
     *
     * @param startIndex rang de la première annonce de la liste
     */
    public void startTask(int startIndex) {
        if (announcerTask != null && !announcerTask.isCancelled()) {
            return; // Déjà en cours
        }
        if (configManager.getIntervalList().isEmpty()) {
            getLogger().warning("Announcer interval list is empty. Auto-announcer will not start.");
            return;
        }

        long intervalTicks = Math.max(1, configManager.getAnnounceInterval()) * 20L;
        announcerTask = new AnnouncerTask(this, startIndex);
        announcerTask.runTaskTimer(this, intervalTicks, intervalTicks);
    }

    /** Arrête le minuteur de ce serveur, sans rien dire au réseau. */
    public void stopTask() {
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

    /** Annonces en réseau, ou null sans SHNetwork. */
    public NetworkAnnouncer getNetwork() {
        return network;
    }
}
