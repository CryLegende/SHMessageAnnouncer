package org.Cry_Legende.sHMessageAnnouncer.command;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.Cry_Legende.sHMessageAnnouncer.SHMessageAnnouncer;
import org.Cry_Legende.sHMessageAnnouncer.announcer.AnnouncementManager;
import org.Cry_Legende.sHMessageAnnouncer.config.MessageManager;
import org.Cry_Legende.sHMessageAnnouncer.network.NetworkAnnouncer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * /shmessageannouncer (alias msa) : start, stop, reload, list, announce et send, chacune avec sa permission
 * shmessageannouncer.admin.&lt;sous-commande&gt;. Tout marche depuis la console.
 */
public class AnnouncerCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("start", "stop", "reload", "list", "announce", "send");
    private static final String PERMISSION_PREFIX = "shmessageannouncer.admin.";

    private final SHMessageAnnouncer plugin;
    private final MessageManager msg;
    private final AnnouncementManager announcementManager;

    public AnnouncerCommand(SHMessageAnnouncer plugin) {
        this.plugin = plugin;
        this.msg = plugin.getMessageManager();
        this.announcementManager = plugin.getAnnouncementManager();
    }

    private static boolean can(CommandSender sender, String subCommand) {
        return sender.hasPermission(PERMISSION_PREFIX + subCommand);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String subCommand = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        if (!SUBCOMMANDS.contains(subCommand)) {
            sendUsage(sender);
            return true;
        }
        if (!can(sender, subCommand)) {
            msg.sendMessage(sender, "no_permission");
            return true;
        }

        switch (subCommand) {
            case "start" -> handleStart(sender);
            case "stop" -> handleStop(sender);
            case "reload" -> handleReload(sender);
            case "list" -> handleList(sender, args);
            case "announce" -> handleAnnounce(sender, args);
            case "send" -> handleSend(sender, args);
            default -> sendUsage(sender);
        }
        return true;
    }

    /** Une seule ligne d'usage, limitée aux sous-commandes que l'expéditeur peut utiliser. */
    private void sendUsage(CommandSender sender) {
        List<String> allowed = SUBCOMMANDS.stream().filter(sub -> can(sender, sub)).collect(Collectors.toList());
        if (allowed.isEmpty()) {
            msg.sendMessage(sender, "no_permission");
            return;
        }
        msg.sendMessage(sender, "usage", Placeholder.unparsed("subcommands", String.join("|", allowed)));
    }

    private void handleStart(CommandSender sender) {
        if (plugin.isAnnouncerRunning()) {
            msg.sendMessage(sender, "announcer_already_running");
            return;
        }
        plugin.startAnnouncer();
        msg.sendMessage(sender, "announcer_started");
    }

    private void handleStop(CommandSender sender) {
        if (!plugin.isAnnouncerRunning()) {
            msg.sendMessage(sender, "announcer_not_running");
            return;
        }
        plugin.stopAnnouncer();
        msg.sendMessage(sender, "announcer_stopped");
    }

    private void handleReload(CommandSender sender) {
        if (plugin.reload(sender)) {
            msg.sendMessage(sender, "reload_success");
            msg.sendMessage(sender, "reload_note");
        }
    }

    private void handleList(CommandSender sender, String[] args) {
        if (args.length < 2) {
            msg.sendMessage(sender, "invalid_usage", Placeholder.unparsed("arg", "list <active|all>"));
            return;
        }
        String type = args[1].toLowerCase(Locale.ROOT);
        List<String> ids;
        if (type.equals("active")) {
            ids = plugin.getConfigManager().getIntervalList();
            msg.sendMessage(sender, "list_header_active");
        } else if (type.equals("all")) {
            ids = new ArrayList<>(announcementManager.getAnnouncementIds());
            Collections.sort(ids);
            msg.sendMessage(sender, "list_header_all");
        } else {
            msg.sendMessage(sender, "invalid_usage", Placeholder.unparsed("arg", "list <active|all>"));
            return;
        }

        if (ids.isEmpty()) {
            msg.sendMessage(sender, "list_empty");
        } else {
            ids.forEach(id -> msg.sendMessage(sender, "list_entry", Placeholder.unparsed("arg", id)));
        }
    }

    private void handleAnnounce(CommandSender sender, String[] args) {
        if (args.length < 2) {
            msg.sendMessage(sender, "invalid_usage", Placeholder.unparsed("arg", "announce <id> [joueur]"));
            return;
        }
        String id = args[1];
        if (announcementManager.getAnnouncement(id) == null) {
            msg.sendMessage(sender, "announcement_not_found", Placeholder.unparsed("arg", id));
            return;
        }

        // Avec SHNetwork, le joueur dont on emprunte les placeholders peut être sur un autre serveur
        NetworkAnnouncer network = plugin.getNetwork();
        Player papiParser = null;
        UUID papiElsewhere = null;
        if (args.length > 2) {
            papiParser = Bukkit.getPlayer(args[2]);
            if (papiParser == null && network != null) {
                papiElsewhere = network.find(args[2]);
            }
            if (papiParser == null && papiElsewhere == null) {
                msg.sendMessage(sender, "player_not_found", Placeholder.unparsed("arg", args[2]));
                return;
            }
        }

        if (network != null) {
            // Tous les joueurs de tous les serveurs, l'ordre des annonces automatiques n'est pas touché
            if (papiElsewhere != null) {
                network.announceFor(id, papiElsewhere);
            } else {
                network.announce(id, papiParser);
            }
            return;
        }
        announcementManager.broadcast(id, papiParser);
    }

    private void handleSend(CommandSender sender, String[] args) {
        if (args.length < 3) {
            msg.sendMessage(sender, "invalid_usage", Placeholder.unparsed("arg", "send <joueur> <id>"));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        // Avec SHNetwork, le joueur visé peut être sur un autre serveur
        NetworkAnnouncer network = plugin.getNetwork();
        UUID remote = target == null && network != null ? network.find(args[1]) : null;
        if (target == null && remote == null) {
            msg.sendMessage(sender, "player_not_found", Placeholder.unparsed("arg", args[1]));
            return;
        }
        String id = args[2];
        if (announcementManager.getAnnouncement(id) == null) {
            msg.sendMessage(sender, "announcement_not_found", Placeholder.unparsed("arg", id));
            return;
        }
        if (target != null) {
            announcementManager.sendAnnouncement(target, id, target);
        } else {
            network.send(remote, id);
        }
    }

    /** Pseudos proposés à la complétion : les joueurs de tout le réseau avec SHNetwork, de ce serveur sinon. */
    private List<String> playerNames() {
        NetworkAnnouncer network = plugin.getNetwork();
        if (network != null) {
            return network.playerNames();
        }
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            SUBCOMMANDS.stream().filter(sub -> can(sender, sub)).forEach(completions::add);
            return StringUtil.copyPartialMatches(args[0], completions, new ArrayList<>());
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!SUBCOMMANDS.contains(sub) || !can(sender, sub)) {
            return Collections.emptyList();
        }
        if (args.length == 2) {
            switch (sub) {
                case "list":
                    completions.addAll(List.of("active", "all"));
                    break;
                case "send":
                    completions.addAll(playerNames());
                    break;
                case "announce":
                    completions.addAll(announcementManager.getAnnouncementIds());
                    break;
            }
        } else if (args.length == 3) {
            switch (sub) {
                case "send":
                    completions.addAll(announcementManager.getAnnouncementIds());
                    break;
                case "announce":
                    completions.addAll(playerNames());
                    break;
            }
        }
        return StringUtil.copyPartialMatches(args[args.length - 1], completions, new ArrayList<>());
    }
}
