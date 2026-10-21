package org.Cry_Legende.sHMessageAnnouncer.announcer;

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.Cry_Legende.sHMessageAnnouncer.SHMessageAnnouncer;
import org.Cry_Legende.sHMessageAnnouncer.announcement.Announcement;
import org.Cry_Legende.sHMessageAnnouncer.config.ConfigManager;
import org.Cry_Legende.sHMessageAnnouncer.util.TextUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.*;

public class AnnouncementManager {
    private final SHMessageAnnouncer plugin;
    private final Map<String, Announcement> announcements = new HashMap<>();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public AnnouncementManager(SHMessageAnnouncer plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads announcements from the configuration.
     * It first checks if the config needs to be converted from the old JSON format.
     * If so, it performs a one-time conversion.
     * Afterwards, it loads the announcements, translating legacy '&' codes before parsing.
     */
    public void loadAnnouncements() {
        announcements.clear();

        if (needsConversion()) {
            plugin.getLogger().info("Old configuration format detected. Starting automatic conversion...");
            convertAndSaveConfig();
            plugin.getLogger().info("Conversion complete! Your old config was backed up as config.yml.old");
            plugin.getLogger().info("The plugin will now use the new MiniMessage format.");
        }

        ConfigurationSection section = plugin.getConfigManager().getAnnouncementsSection();
        if (section == null) return;

        for (String id : section.getKeys(false)) {
            List<String> rawLines = section.getStringList(id);
            List<Component> processedLines = new ArrayList<>();

            for (String rawLine : rawLines) {
                // Translate legacy '&' codes into MiniMessage tags before parsing.
                String translatedLine = TextUtil.translateLegacyCodes(rawLine);
                processedLines.add(miniMessage.deserialize(translatedLine));
            }
            announcements.put(id, new Announcement(id, processedLines));
        }
        plugin.getLogger().info("Loaded " + announcements.size() + " announcements.");
    }

    /**
     * Checks if the config file is using the old JSON format.
     * It does this by inspecting the first line of the first announcement.
     *
     * @return true if conversion is needed, false otherwise.
     */
    private boolean needsConversion() {
        ConfigurationSection section = plugin.getConfigManager().getAnnouncementsSection();
        if (section == null || section.getKeys(false).isEmpty()) {
            return false;
        }

        String firstId = section.getKeys(false).iterator().next();
        List<String> lines = section.getStringList(firstId);
        if (lines.isEmpty()) {
            return false;
        }

        String firstLine = lines.get(0).trim();
        return firstLine.startsWith("{") && firstLine.endsWith("}");
    }

    /**
     * Performs the one-time conversion process from legacy JSON to modern MiniMessage.
     */
    private void convertAndSaveConfig() {
        ConfigManager configManager = plugin.getConfigManager();
        ConfigurationSection section = configManager.getAnnouncementsSection();
        Map<String, List<String>> convertedAnnouncements = new LinkedHashMap<>();

        for (String id : section.getKeys(false)) {
            List<String> oldLines = section.getStringList(id);
            List<String> newLines = new ArrayList<>();
            for (String oldLine : oldLines) {
                newLines.add(convertLegacyJsonToMiniMessageString(oldLine));
            }
            convertedAnnouncements.put(id, newLines);
        }

        File configFile = new File(plugin.getDataFolder(), "config.yml");
        File oldConfigFile = new File(plugin.getDataFolder(), "config.yml.old");
        if (oldConfigFile.exists()) {
            oldConfigFile.delete();
        }
        if (!configFile.renameTo(oldConfigFile)) {
            plugin.getLogger().severe("Could not back up old config.yml! Aborting conversion.");
            return;
        }

        plugin.reloadConfig();
        configManager.saveConvertedConfig(convertedAnnouncements);
    }

    /**
     * Converts a single line of legacy JSON text into a MiniMessage string.
     * This method also handles the '&' codes within the JSON text.
     *
     * @param jsonString The old JSON string.
     * @return A modern MiniMessage string.
     */
    private String convertLegacyJsonToMiniMessageString(String jsonString) {
        try {
            JSONObject json = new JSONObject(jsonString);
            String text = json.optString("text", "");

            // Use the utility to translate '&' codes found inside the JSON text.
            String miniMessageText = TextUtil.translateLegacyCodes(text);

            if (json.has("clickEvent")) {
                JSONObject clickEventJson = json.getJSONObject("clickEvent");
                String action = clickEventJson.optString("action", "").toLowerCase();
                String value = clickEventJson.optString("value", "");
                value = value.replace("'", "\\'");

                switch (action) {
                    case "run_command":
                        return "<click:run_command:'" + value + "'>" + miniMessageText + "</click>";
                    case "suggest_command":
                        return "<click:suggest_command:'" + value + "'>" + miniMessageText + "</click>";
                    case "open_url":
                        return "<click:open_url:'" + value + "'>" + miniMessageText + "</click>";
                }
            }
            return miniMessageText;

        } catch (JSONException e) {
            return jsonString;
        }
    }

    /**
     * Sends an announcement to a specific audience.
     * It handles PlaceholderAPI and also translates any legacy '&' codes
     * that might come from the placeholders themselves.
     *
     * @param audience   The audience to send the message to.
     * @param id         The ID of the announcement to send.
     * @param papiParser The player to use for parsing PlaceholderAPI placeholders.
     */
    public void sendAnnouncement(Audience audience, String id, Player papiParser) {
        Announcement announcement = announcements.get(id);
        if (announcement == null) return;

        for (Component line : announcement.lines()) {
            // Serialize component to string to parse PAPI
            String serializedLine = miniMessage.serialize(line);
            String papiParsedLine = PlaceholderAPI.setPlaceholders(papiParser, serializedLine);

            // Translate legacy '&' codes that might be returned by PlaceholderAPI
            String finalLine = TextUtil.translateLegacyCodes(papiParsedLine);

            // Deserialize the final, fully processed string and send it
            audience.sendMessage(miniMessage.deserialize(finalLine));
        }

        playSound(audience);
    }

    /**
     * Plays the configured sound to a given audience.
     *
     * @param audience The audience to play the sound to.
     */
    public void playSound(Audience audience) {
        ConfigManager config = plugin.getConfigManager();
        if (config.isSoundEnabled()) {
            Sound sound = Sound.sound(config.getSoundName().getKey(), Sound.Source.MASTER, config.getSoundVolume(), config.getSoundPitch());
            audience.playSound(sound);
        }
    }

    public Announcement getAnnouncement(String id) {
        return announcements.get(id);
    }

    public Set<String> getAnnouncementIds() {
        return Collections.unmodifiableSet(announcements.keySet());
    }
}
