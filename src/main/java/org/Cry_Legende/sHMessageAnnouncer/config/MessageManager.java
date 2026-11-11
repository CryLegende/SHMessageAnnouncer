package org.Cry_Legende.sHMessageAnnouncer.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.Cry_Legende.sHMessageAnnouncer.SHMessageAnnouncer;
import org.Cry_Legende.sHMessageAnnouncer.util.TextUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class MessageManager {

    private final SHMessageAnnouncer plugin;
    // Remplacée d'un bloc à chaque chargement : jamais lue à moitié remplie
    private volatile Map<String, String> messages = Map.of();
    private volatile String prefix = "";

    public MessageManager(SHMessageAnnouncer plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads all messages from the messages.yml file into memory.
     * Keys missing from the server's file (added by a newer version) are taken from the messages.yml of the jar.
     */
    public void load() {
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        apply(YamlConfiguration.loadConfiguration(messagesFile));
    }

    /** Applies an already parsed messages.yml (used by the reload, after validation). */
    public void apply(FileConfiguration config) {
        Map<String, String> loaded = new HashMap<>();
        InputStream defStream = plugin.getResource("messages.yml");
        if (defStream != null) {
            FileConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(defStream, StandardCharsets.UTF_8));
            for (String key : defaults.getKeys(false)) {
                loaded.put(key, defaults.getString(key));
            }
        }
        for (String key : config.getKeys(false)) {
            String value = config.getString(key);
            if (value != null) {
                loaded.put(key, value);
            }
        }
        messages = loaded;
        prefix = loaded.getOrDefault("prefix", "");
    }

    /**
     * Sends a formatted message from messages.yml to a CommandSender.
     * It translates legacy '&' codes and then parses MiniMessage tags.
     *
     * @param sender    The recipient of the message.
     * @param key       The key of the message in messages.yml.
     * @param resolvers Any MiniMessage TagResolvers to be used (e.g., for placeholders).
     */
    public void sendMessage(CommandSender sender, String key, TagResolver... resolvers) {
        String message = messages.get(key);
        if (message == null) {
            sender.sendMessage(Component.text("Missing message for key: " + key));
            return;
        }

        // Translate legacy '&' codes into MiniMessage tags before parsing.
        String processedMessage = TextUtil.translateLegacyCodes(prefix + message);

        Component component = MiniMessage.miniMessage().deserialize(processedMessage, resolvers);
        sender.sendMessage(component);
    }
}
