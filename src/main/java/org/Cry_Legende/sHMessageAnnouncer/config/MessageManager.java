package org.Cry_Legende.sHMessageAnnouncer.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
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
import java.util.Objects;

public class MessageManager {

    private final SHMessageAnnouncer plugin;
    private final Map<String, String> messages = new HashMap<>();
    private String prefix;

    public MessageManager(SHMessageAnnouncer plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads all messages from the messages.yml file into memory.
     * It also sets up a fallback to the internal messages.yml if the external file is broken.
     */
    public void load() {
        messages.clear();
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(messagesFile);

        // Fallback to internal messages.yml if the external one is missing keys
        InputStream defStream = plugin.getResource("messages.yml");
        if (defStream != null) {
            config.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defStream, StandardCharsets.UTF_8)));
        }

        for (String key : Objects.requireNonNull(config.getKeys(false))) {
            messages.put(key, config.getString(key));
        }
        prefix = messages.getOrDefault("prefix", "");
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

    /**
     * Sends the formatted help message to a CommandSender.
     */
    public void sendHelp(CommandSender sender) {
        String[] helpKeys = {"help_header", "help_start", "help_stop", "help_reload", "help_list", "help_send", "help_announce"};
        for (String key : helpKeys) {
            String message = messages.get(key);
            if (message != null) {
                // Apply the translation to each help line as well.
                String processedHelpLine = TextUtil.translateLegacyCodes(message);
                sender.sendMessage(MiniMessage.miniMessage().deserialize(processedHelpLine));
            }
        }
    }
}
