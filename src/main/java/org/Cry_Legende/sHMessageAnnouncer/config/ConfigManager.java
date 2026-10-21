package org.Cry_Legende.sHMessageAnnouncer.config;

import org.Cry_Legende.sHMessageAnnouncer.SHMessageAnnouncer;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ConfigManager {

    private final SHMessageAnnouncer plugin;
    private boolean announcerEnabledOnStart;
    private boolean random;
    private int announceInterval;
    private boolean soundEnabled;
    private Sound soundName;
    private float soundVolume;
    private float soundPitch;
    private List<String> intervalList;
    private ConfigurationSection announcementsSection;

    public ConfigManager(SHMessageAnnouncer plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        this.announcerEnabledOnStart = config.getBoolean("announcer_enabled", true);
        this.random = config.getBoolean("announcer_random", false);
        this.announceInterval = config.getInt("announce_interval", 300);

        this.soundEnabled = config.getBoolean("sound.enabled", false);
        try {
            this.soundName = Sound.valueOf(config.getString("sound.sound_name", "BLOCK_NOTE_BLOCK_PLING").toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid sound name in config.yml: " + config.getString("sound.sound_name") + ". Using default.");
            this.soundName = Sound.BLOCK_NOTE_BLOCK_PLING;
        }
        this.soundVolume = (float) config.getDouble("sound.volume", 1.0);
        this.soundPitch = (float) config.getDouble("sound.pitch", 1.0);

        this.intervalList = config.getStringList("interval_announcement_list");
        this.announcementsSection = config.getConfigurationSection("announcements");

        if (this.announcementsSection == null) {
            plugin.getLogger().severe("The 'announcements' section is missing from your config.yml! Creating a new one.");
            this.announcementsSection = plugin.getConfig().createSection("announcements");
        }
    }

    /**
     * Saves a new set of announcements to the config.yml file.
     * This is used by the converter to write the modern MiniMessage format.
     *
     * @param convertedAnnouncements A map where the key is the announcement ID and the value is a list of MiniMessage strings.
     */
    public void saveConvertedConfig(Map<String, List<String>> convertedAnnouncements) {
        FileConfiguration config = plugin.getConfig();

        // Overwrite the announcements section with the new, converted data
        // Using `createSection` effectively clears the old one and ensures the path exists
        ConfigurationSection newAnnouncementsSection = config.createSection("announcements");

        for (Map.Entry<String, List<String>> entry : convertedAnnouncements.entrySet()) {
            newAnnouncementsSection.set(entry.getKey(), entry.getValue());
        }

        // Save the changes to the config.yml file on disk
        plugin.saveConfig();

        // Reload all config values into this manager to reflect the changes immediately
        this.load();
    }

    // --- Getters for all loaded values ---

    public boolean isAnnouncerEnabledOnStart() {
        return announcerEnabledOnStart;
    }

    public boolean isRandom() {
        return random;
    }

    public int getAnnounceInterval() {
        return announceInterval;
    }

    public boolean isSoundEnabled() {
        return soundEnabled;
    }

    public Sound getSoundName() {
        return soundName;
    }

    public float getSoundVolume() {
        return soundVolume;
    }

    public float getSoundPitch() {
        return soundPitch;
    }

    public List<String> getIntervalList() {
        return Collections.unmodifiableList(intervalList);
    }

    public ConfigurationSection getAnnouncementsSection() {
        return announcementsSection;
    }
}