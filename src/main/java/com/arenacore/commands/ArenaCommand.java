package com.arenacore.commands;

import com.arenacore.ArenaCore;
import com.arenacore.arena.Arena;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.SessionManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Main FFA arena command handler.
 */
public class ArenaCommand implements CommandExecutor, TabCompleter {

    private final ArenaCore plugin;

    public ArenaCommand(ArenaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create":
                return handleCreate(sender, args);
            case "delete":
                return handleDelete(sender, args);
            case "send":
                return handleSend(sender, args);
            case "setspawn":
                return handleSetSpawn(sender, args);
            case "addspawn":
                return handleAddSpawn(sender, args);
            case "removespawn":
                return handleRemoveSpawn(sender, args);
            case "setlobby":
                return handleSetLobby(sender);
            case "regen":
                return handleRegen(sender, args);
            case "enable":
                return handleEnable(sender, args);
            case "disable":
                return handleDisable(sender, args);
            case "list":
                return handleList(sender);
            case "info":
                return handleInfo(sender, args);
            case "save":
                return handleSave(sender);
            default:
                sender.sendMessage("§cUnknown subcommand. Use /arena for help.");
                return true;
        }
    }

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can create arenas.");
            return true;
        }

        if (!sender.hasPermission("arena.create")) {
            sender.sendMessage("§cYou don't have permission.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /arena create <n>");
            return true;
        }

        String arenaName = args[1];

        if (plugin.getArenaManager().getArena(arenaName) != null) {
            sender.sendMessage("§cArena already exists!");
            return true;
        }

        try {
            SessionManager sessionManager = WorldEdit.getInstance().getSessionManager();
            LocalSession session = sessionManager.get(BukkitAdapter.adapt(player));
            Region selection = session.getSelection(BukkitAdapter.adapt(player.getWorld()));

            if (selection == null) {
                sender.sendMessage("§cMake a WorldEdit selection first!");
                return true;
            }

            Arena arena = plugin.getArenaManager().createArena(arenaName, player.getWorld());
            arena.setRegion(selection);

            String regionType = selection.getClass().getSimpleName();
            Arena.RegionShape shape = Arena.RegionShape.CUBOID;
            if (regionType.contains("Polygon")) {
                shape = Arena.RegionShape.POLYGONAL;
            } else if (regionType.contains("Ellipsoid")) {
                shape = Arena.RegionShape.ELLIPSOID;
            }
            arena.setShape(shape);

            plugin.getRegenerationManager().saveArenaSnapshot(arena)
                    .thenAccept(success -> {
                        if (success) {
                            player.sendMessage("§aArena '" + arenaName + "' created!");
                            player.sendMessage("§7Next: §e/arena setspawn " + arenaName + " center");
                        } else {
                            player.sendMessage("§cFailed to save snapshot!");
                        }
                    });

            plugin.getArenaManager().saveArena(arena);

        } catch (IncompleteRegionException e) {
            sender.sendMessage("§cMake a WorldEdit selection first!");
        }

        return true;
    }

    private boolean handleDelete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("arena.delete")) {
            sender.sendMessage("§cYou don't have permission.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /arena delete <n>");
            return true;
        }

        if (plugin.getArenaManager().deleteArena(args[1])) {
            sender.sendMessage("§aArena '" + args[1] + "' deleted!");
        } else {
            sender.sendMessage("§cArena not found!");
        }

        return true;
    }

    private boolean handleSend(CommandSender sender, String[] args) {
        if (!sender.hasPermission("arena.send")) {
            sender.sendMessage("§cYou don't have permission.");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage("§cUsage: /arena send <player> <arena>");
            return true;
        }

        Player target = plugin.getServer().getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found!");
            return true;
        }

        Arena arena = plugin.getArenaManager().getArena(args[2]);
        if (arena == null) {
            sender.sendMessage("§cArena not found!");
            return true;
        }

        if (!arena.isComplete()) {
            sender.sendMessage("§cArena not fully configured!");
            return true;
        }

        if (!arena.hasMultipleSpawns()) {
            // Instant teleport to center
            Location center = arena.getCenterSpawn();
            target.teleport(center);
            plugin.getArenaManager().addPlayerToArena(target, arena, "center");
            sender.sendMessage("§aSent " + target.getName() + " to " + arena.getName());
        } else {
            // Open GUI
            plugin.getGuiManager().openSpawnGUI(target, arena);
            sender.sendMessage("§7Opened spawn selection for " + target.getName());
        }

        return true;
    }

    private boolean handleSetSpawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can set spawns.");
            return true;
        }

        if (!sender.hasPermission("arena.admin")) {
            sender.sendMessage("§cYou don't have permission.");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage("§cUsage: /arena setspawn <arena> center");
            return true;
        }

        Arena arena = plugin.getArenaManager().getArena(args[1]);
        if (arena == null) {
            sender.sendMessage("§cArena not found!");
            return true;
        }

        if (!args[2].equalsIgnoreCase("center")) {
            sender.sendMessage("§cUse 'center' for main spawn.");
            sender.sendMessage("§7Use /arena addspawn for additional spawns.");
            return true;
        }

        Location loc = player.getLocation();

        if (!plugin.getArenaManager().validateSpawnPlacement(arena, loc)) {
            sender.sendMessage("§cSpawn must be inside arena!");
            return true;
        }

        arena.setCenterSpawn(loc);
        plugin.getArenaManager().saveArena(arena);
        sender.sendMessage("§aCenter spawn set for '" + arena.getName() + "'");

        return true;
    }

    private boolean handleAddSpawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can add spawns.");
            return true;
        }

        if (!sender.hasPermission("arena.admin")) {
            sender.sendMessage("§cYou don't have permission.");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage("§cUsage: /arena addspawn <arena> <n>");
            return true;
        }

        Arena arena = plugin.getArenaManager().getArena(args[1]);
        if (arena == null) {
            sender.sendMessage("§cArena not found!");
            return true;
        }

        String spawnName = args[2];
        Location loc = player.getLocation();

        if (!plugin.getArenaManager().validateSpawnPlacement(arena, loc)) {
            sender.sendMessage("§cSpawn must be inside arena!");
            return true;
        }

        arena.addNamedSpawn(spawnName, loc);
        plugin.getArenaManager().saveArena(arena);
        plugin.getGuiManager().invalidateGUI(arena.getName());

        sender.sendMessage("§aAdded spawn '" + spawnName + "' to " + arena.getName());
        sender.sendMessage("§7Total spawns: " + arena.getTotalSpawns());

        return true;
    }

    private boolean handleRemoveSpawn(CommandSender sender, String[] args) {
        if (!sender.hasPermission("arena.admin")) {
            sender.sendMessage("§cYou don't have permission.");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage("§cUsage: /arena removespawn <arena> <n>");
            return true;
        }

        Arena arena = plugin.getArenaManager().getArena(args[1]);
        if (arena == null) {
            sender.sendMessage("§cArena not found!");
            return true;
        }

        String spawnName = args[2];

        if (spawnName.equalsIgnoreCase("center")) {
            sender.sendMessage("§cCannot remove center spawn!");
            return true;
        }

        if (arena.getNamedSpawn(spawnName) == null) {
            sender.sendMessage("§cSpawn not found!");
            return true;
        }

        arena.removeNamedSpawn(spawnName);
        plugin.getArenaManager().saveArena(arena);
        plugin.getGuiManager().invalidateGUI(arena.getName());

        sender.sendMessage("§aRemoved spawn '" + spawnName + "' from " + arena.getName());

        return true;
    }

    private boolean handleSetLobby(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can set lobby.");
            return true;
        }

        if (!sender.hasPermission("arena.admin")) {
            sender.sendMessage("§cYou don't have permission.");
            return true;
        }

        plugin.getArenaManager().setLobbySpawn(player.getLocation());
        sender.sendMessage("§aLobby spawn set!");

        return true;
    }

    private boolean handleRegen(CommandSender sender, String[] args) {
        if (!sender.hasPermission("arena.regen")) {
            sender.sendMessage("§cYou don't have permission.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /arena regen <arena>");
            return true;
        }

        Arena arena = plugin.getArenaManager().getArena(args[1]);
        if (arena == null) {
            sender.sendMessage("§cArena not found!");
            return true;
        }

        sender.sendMessage("§7Regenerating arena...");
        plugin.getRegenerationManager().regenerateArena(arena)
                .thenAccept(success -> {
                    sender.sendMessage(success ? "§aRegenerated!" : "§cFailed!");
                });

        return true;
    }

    private boolean handleEnable(CommandSender sender, String[] args) {
        return toggleArena(sender, args, true);
    }

    private boolean handleDisable(CommandSender sender, String[] args) {
        return toggleArena(sender, args, false);
    }

    private boolean toggleArena(CommandSender sender, String[] args, boolean enable) {
        if (!sender.hasPermission("arena.admin")) {
            sender.sendMessage("§cYou don't have permission.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /arena " + (enable ? "enable" : "disable") + " <arena>");
            return true;
        }

        Arena arena = plugin.getArenaManager().getArena(args[1]);
        if (arena == null) {
            sender.sendMessage("§cArena not found!");
            return true;
        }

        arena.setEnabled(enable);
        plugin.getArenaManager().saveArena(arena);
        sender.sendMessage("§aArena " + (enable ? "enabled" : "disabled") + "!");

        return true;
    }

    private boolean handleList(CommandSender sender) {
        var arenas = plugin.getArenaManager().getArenas();

        if (arenas.isEmpty()) {
            sender.sendMessage("§7No arenas found.");
            return true;
        }

        sender.sendMessage("§e§l=== FFA Arenas (" + arenas.size() + ") ===");

        for (Arena arena : arenas) {
            String status = arena.isEnabled() ? "§a✓" : "§c✗";
            String complete = arena.isComplete() ? "§a[Ready]" : "§7[Setup]";
            int players = arena.getPlayerCount();
            int spawns = arena.getTotalSpawns();

            sender.sendMessage(status + " §f" + arena.getName() + " " + complete +
                    " §7(" + players + " players, " + spawns + " spawns)");
        }

        return true;
    }

    private boolean handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /arena info <arena>");
            return true;
        }

        Arena arena = plugin.getArenaManager().getArena(args[1]);
        if (arena == null) {
            sender.sendMessage("§cArena not found!");
            return true;
        }

        sender.sendMessage("§e§l=== " + arena.getName() + " ===");
        sender.sendMessage("§7Status: " + (arena.isEnabled() ? "§aEnabled" : "§cDisabled"));
        sender.sendMessage("§7Players: §f" + arena.getPlayerCount());
        sender.sendMessage("§7Center Spawn: " + (arena.getCenterSpawn() != null ? "§a✓" : "§c✗"));
        sender.sendMessage("§7Named Spawns: §f" + arena.getNamedSpawns().size());
        sender.sendMessage("§7Total Spawns: §f" + arena.getTotalSpawns());
        sender.sendMessage("§7Regen Mode: §f" + arena.getRegenMode());
        sender.sendMessage("§7Rekit on Kill: " + (arena.isRekitOnKill() ? "§aYes" : "§cNo"));

        if (arena.getPermission() != null) {
            sender.sendMessage("§7Permission: §farena.join." + arena.getPermission());
        }

        return true;
    }

    private boolean handleSave(CommandSender sender) {
        if (!sender.hasPermission("arena.admin")) {
            sender.sendMessage("§cYou don't have permission.");
            return true;
        }

        sender.sendMessage("§7Saving all arenas...");
        plugin.getArenaManager().saveAll();
        sender.sendMessage("§aAll arenas saved!");

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§e§l=== ArenaCore FFA Commands ===");
        sender.sendMessage("§e/arena create <n> §7- Create arena");
        sender.sendMessage("§e/arena delete <n> §7- Delete arena");
        sender.sendMessage("§e/arena send <player> <arena> §7- Send player");
        sender.sendMessage("§e/arena setspawn <arena> center §7- Set center spawn");
        sender.sendMessage("§e/arena addspawn <arena> <n> §7- Add named spawn");
        sender.sendMessage("§e/arena removespawn <arena> <n> §7- Remove spawn");
        sender.sendMessage("§e/arena setlobby §7- Set lobby spawn");
        sender.sendMessage("§e/arena regen <arena> §7- Regenerate arena");
        sender.sendMessage("§e/arena enable/disable <arena> §7- Toggle arena");
        sender.sendMessage("§e/arena list §7- List arenas");
        sender.sendMessage("§e/arena info <arena> §7- View info");
        sender.sendMessage("§7Also: §e/spawn §7and §e/back");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("create", "delete", "send", "setspawn",
                    "addspawn", "removespawn", "setlobby", "regen", "enable", "disable",
                    "list", "info", "save"));
        } else if (args.length == 2) {
            String subCmd = args[0].toLowerCase();
            if (Arrays.asList("delete", "send", "setspawn", "addspawn", "removespawn",
                    "regen", "enable", "disable", "info").contains(subCmd)) {
                completions.addAll(plugin.getArenaManager().getArenaNames());
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("setspawn")) {
                completions.add("center");
            }
        }

        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .collect(Collectors.toList());
    }
}