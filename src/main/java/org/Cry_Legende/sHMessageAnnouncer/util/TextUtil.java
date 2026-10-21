package org.Cry_Legende.sHMessageAnnouncer.util;

/**
 * Utility class for text manipulation.
 */
public final class TextUtil {

    /**
     * Translates traditional ampersand (&) color codes into MiniMessage tags.
     * This allows using both formats in configuration files.
     *
     * @param text The string to translate.
     * @return The translated string, ready for MiniMessage parsing.
     */
    public static String translateLegacyCodes(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        // This is a direct mapping from legacy codes to MiniMessage tags.
        return text
            .replace("&k", "<obf>").replace("&l", "<b>").replace("&m", "<st>").replace("&n", "<u>").replace("&o", "<i>").replace("&r", "<reset>")
            .replace("&0", "<black>").replace("&1", "<dark_blue>").replace("&2", "<dark_green>").replace("&3", "<dark_aqua>").replace("&4", "<dark_red>").replace("&5", "<dark_purple>")
            .replace("&6", "<gold>").replace("&7", "<gray>").replace("&8", "<dark_gray>").replace("&9", "<blue>").replace("&a", "<green>").replace("&b", "<aqua>")
            .replace("&c", "<red>").replace("&d", "<light_purple>").replace("&e", "<yellow>").replace("&f", "<white>");
    }
}
