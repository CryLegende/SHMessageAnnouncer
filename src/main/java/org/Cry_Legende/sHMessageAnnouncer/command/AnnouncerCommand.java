package org.Cry_Legende.sHMessageAnnouncer.command;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.Cry_Legende.sHMessageAnnouncer.SHMessageAnnouncer;
import org.Cry_Legende.sHMessageAnnouncer.announcer.AnnouncementManager;
import org.Cry_Legende.sHMessageAnnouncer.config.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class AnnouncerCommand implements CommandExecutor, TabCompleter {
    private final SHMessageAnnouncer plugin;
    private final MessageManager msg;
    private final AnnouncementManager announcementManager;

    public AnnouncerCommand(SHMessageAnnouncer plugin) {
        this.plugin = plugin;
        this.msg = plugin.getMessageManager();
        this.announcementManager = plugin.getAnnouncementManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("shmessageannouncer.admin")) {
            msg.sendMessage(sender, "no_permission");
            return true;
        }

        if (args.length == 0) {
            msg.sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "start" -> handleStart(sender);
            case "stop" -> handleStop(sender);
            case "reload" -> handleReload(sender);
            case "list" -> handleList(sender, args);
            case "announce" -> handleAnnounce(sender, args);
            case "send" -> handleSend(sender, args);
            default -> msg.sendHelp(sender);
        }
        return true;
    }

    private void handleStart(CommandSender sender) {
        if (plugin.getAnnouncerTask() != null && !plugin.getAnnouncerTask().isCancelled()) {
            msg.sendMessage(sender, "announcer_already_running");
            return;
        }
        plugin.startAnnouncer();
        msg.sendMessage(sender, "announcer_started");
    }

    private void handleStop(CommandSender sender) {
        if (plugin.getAnnouncerTask() == null || plugin.getAnnouncerTask().isCancelled()) {
            msg.sendMessage(sender, "announcer_not_running");
            return;
        }
        plugin.stopAnnouncer();
        msg.sendMessage(sender, "announcer_stopped");
    }

    private void handleReload(CommandSender sender) {
        plugin.loadPlugin(true);
        msg.sendMessage(sender, "reload_success");
    }

    private void handleList(CommandSender sender, String[] args) {
        if (args.length < 2) {
            msg.sendMessage(sender, "invalid_usage", Placeholder.unparsed("arg", "/msa list <active/all>"));
            return;
        }
        String type = args[1].toLowerCase();
        List<String> ids;
        if (type.equals("active")) {
            ids = plugin.getConfigManager().getIntervalList();
            msg.sendMessage(sender, "list_header_active");
        } else if (type.equals("all")) {
            ids = new ArrayList<>(announcementManager.getAnnouncementIds());
            Collections.sort(ids);
            msg.sendMessage(sender, "list_header_all");
        } else {
            msg.sendMessage(sender, "invalid_usage", Placeholder.unparsed("arg", "/msa list <active/all>"));
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
            msg.sendMessage(sender, "invalid_usage", Placeholder.unparsed("arg", "/msa announce <id> [papi_player]"));
            return;
        }
        String id = args[1];
        if (announcementManager.getAnnouncement(id) == null) {
            msg.sendMessage(sender, "announcement_not_found", Placeholder.unparsed("arg", id));
            return;
        }

        Player papiParser = null;
        if (args.length > 2) {
            papiParser = Bukkit.getPlayer(args[2]);
            if (papiParser == null) {
                msg.sendMessage(sender, "player_not_found", Placeholder.unparsed("arg", args[2]));
                return;
            }
        }

        final Player finalPapiParser = papiParser;
        Bukkit.getOnlinePlayers().forEach(player -> announcementManager.sendAnnouncement(player, id, finalPapiParser != null ? finalPapiParser : player));
    }

    private void handleSend(CommandSender sender, String[] args) {
        if (args.length < 3) {
            msg.sendMessage(sender, "invalid_usage", Placeholder.unparsed("arg", "/msa send <player> <id>"));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            msg.sendMessage(sender, "player_not_found", Placeholder.unparsed("arg", args[1]));
            return;
        }
        String id = args[2];
        if (announcementManager.getAnnouncement(id) == null) {
            msg.sendMessage(sender, "announcement_not_found", Placeholder.unparsed("arg", id));
            return;
        }
        announcementManager.sendAnnouncement(target, id, target);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("shmessageannouncer.admin")) {
            return Collections.emptyList();
        }

        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.addAll(Arrays.asList("start", "stop", "reload", "list", "announce", "send"));
            return StringUtil.copyPartialMatches(args[0], completions, new ArrayList<>());
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "list":
                    completions.addAll(Arrays.asList("active", "all"));
                    break;
                case "send":
                    completions.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()));
                    break;
                case "announce":
                    completions.addAll(announcementManager.getAnnouncementIds());
                    break;
            }
        } else if (args.length == 3) {
            switch (args[0].toLowerCase()) {
                case "send":
                    completions.addAll(announcementManager.getAnnouncementIds());
                    break;
                case "announce":
                    completions.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()));
                    break;
            }
        }
        return StringUtil.copyPartialMatches(args[args.length - 1], completions, new ArrayList<>());
    }
}