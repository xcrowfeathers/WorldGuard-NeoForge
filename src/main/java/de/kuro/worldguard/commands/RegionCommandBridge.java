package de.kuro.worldguard.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.sk89q.minecraft.util.commands.CommandException;
import com.sk89q.minecraft.util.commands.CommandPermissionsException;
import com.sk89q.minecraft.util.commands.CommandUsageException;
import com.sk89q.minecraft.util.commands.CommandsManager;
import com.sk89q.minecraft.util.commands.MissingNestedCommandException;
import com.sk89q.minecraft.util.commands.SimpleInjector;
import com.sk89q.minecraft.util.commands.WrappedCommandException;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.neoforge.NeoForgeAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.commands.ProtectionCommands;
import com.sk89q.worldguard.commands.GeneralCommands;
import com.sk89q.worldguard.commands.ToggleCommands;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.flags.RegionGroup;
import de.kuro.worldguard.platform.NeoForgeWorldGuardPlatform;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Brigadier frontend */
public final class RegionCommandBridge {
    private static final Set<String> REGION_ALIASES = Set.of("region", "regions", "rg");
    private static final Set<String> REGION_ARGUMENTS = Set.of("redefine", "update", "move", "remove", "delete",
            "del", "rem", "info", "i", "select", "sel", "s", "addowner", "ao", "removeowner",
            "ro", "remowner", "addmember", "addmem", "am", "removemember", "remmember",
            "removemem", "remmem", "rm", "setpriority", "priority", "pri", "setparent",
            "parent", "par", "flag", "f", "flags", "teleport", "tp");
    private static final Set<String> GENERAL_ALIASES = Set.of("god", "ungod", "heal", "slay", "locate", "stack");
    private static final Set<String> FIRE_ALIASES = Set.of("stopfire", "allowfire");
    private static final Set<String> PLAYER_ARGUMENTS = Set.of("addowner", "ao", "removeowner", "remowner", "ro",
            "addmember", "addmem", "am", "removemember", "remmember", "removemem", "remmem",
            "rm", "define", "def", "d", "create");

    private final NeoForgeWorldGuardPlatform platform;
    private final CommandsManager<Actor> commands = new CommandsManager<>() {
        @Override
        public boolean hasPermission(Actor actor, String permission) {
            return actor.hasPermission(permission);
        }
    };

    public RegionCommandBridge(NeoForgeWorldGuardPlatform platform) {
        this.platform = platform;
        commands.setInjector(new SimpleInjector(WorldGuard.getInstance()));
        commands.register(ProtectionCommands.class);
        commands.register(GeneralCommands.class);
        commands.register(ToggleCommands.class);
    }

    public void register(RegisterCommandsEvent event) {
        for (String alias : REGION_ALIASES) {
            event.getDispatcher().register(Commands.literal(alias)
                    .executes(context -> execute(context, alias, ""))
                    .then(Commands.argument("args", StringArgumentType.greedyString())
                            .suggests(this::suggest)
                            .executes(context -> execute(context, alias,
                                    StringArgumentType.getString(context, "args")))));
        }
        for (String alias : List.of("worldguard", "wg")) {
            event.getDispatcher().register(Commands.literal(alias)
                    .executes(context -> execute(context, alias, ""))
                    .then(Commands.argument("args", StringArgumentType.greedyString())
                            .suggests(this::suggest)
                            .executes(context -> execute(context, alias,
                                    StringArgumentType.getString(context, "args")))));
        }
        for (String alias : GENERAL_ALIASES) {
            event.getDispatcher().register(Commands.literal(alias)
                    .executes(context -> execute(context, alias, ""))
                    .then(Commands.argument("args", StringArgumentType.greedyString())
                            .suggests(this::suggest)
                            .executes(context -> execute(context, alias,
                                    StringArgumentType.getString(context, "args")))));
        }
        for (String alias : FIRE_ALIASES) {
            event.getDispatcher().register(Commands.literal(alias)
                    .executes(context -> execute(context, alias, ""))
                    .then(Commands.argument("args", StringArgumentType.greedyString())
                            .suggests(this::suggest)
                            .executes(context -> execute(context, alias,
                                    StringArgumentType.getString(context, "args")))));
        }
    }

    private int execute(CommandContext<CommandSourceStack> context, String alias, String raw) {
        CommandSourceStack source = context.getSource();
        Actor actor = source.getPlayer() != null ? platform.adapt(source.getPlayer())
                : NeoForgeAdapter.adaptCommandSource(source);
        String[] args = raw.isBlank() ? new String[0] : raw.split(" ", -1);
        try {
            if (Set.of("wg", "worldguard").contains(alias) && args.length > 0
                    && args[0].equalsIgnoreCase("version")) {
                source.sendSystemMessage(Component.literal("WorldGuard Core " + WorldGuard.getVersion() + " | NeoForge "
                        + modVersion("neoforge") + " | Minecraft " + source.getServer().getServerVersion()
                        + " | WorldEdit " + modVersion("worldedit")
                        + " | port " + modVersion("worldguard")));
                return 1;
            }
            if (REGION_ALIASES.contains(alias) && args.length > 0
                    && !Set.of("info", "i", "list", "l", "flags", "select", "sel", "s")
                    .contains(args[0].toLowerCase(Locale.ROOT))) {
                platform.regionCommandStarted();
            }
            commands.execute(alias, args, actor, actor);
            if (Set.of("wg", "worldguard").contains(alias) && args.length > 0
                    && args[0].equalsIgnoreCase("reload")) {
                platform.refreshPermissionResolver();
                platform.getSessionManager().resetAllStates();
            }
            return 1;
        } catch (CommandPermissionsException error) {
            source.sendFailure(Component.literal("You don't have permission."));
        } catch (MissingNestedCommandException error) {
            source.sendFailure(Component.literal(error.getUsage()));
        } catch (CommandUsageException error) {
            source.sendFailure(Component.literal(error.getMessage() + "\n" + error.getUsage()));
        } catch (WrappedCommandException error) {
            Throwable cause = error.getCause();
            source.sendFailure(Component.literal(cause == null ? error.toString() : cause.getMessage()));
        } catch (CommandException error) {
            source.sendFailure(Component.literal(error.getMessage()));
        } catch (RuntimeException error) {
            WorldGuard.logger.log(java.util.logging.Level.SEVERE, "WorldGuard region command failed", error);
            source.sendFailure(Component.literal("WorldGuard region command failed: " + error.getMessage()));
        }
        return 0;
    }

    private CompletableFuture<Suggestions> suggest(CommandContext<CommandSourceStack> context,
                                                   SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        String[] words = remaining.trim().isEmpty() ? new String[0] : remaining.trim().split("\\s+");
        boolean newWord = remaining.isEmpty() || Character.isWhitespace(remaining.charAt(remaining.length() - 1));
        String current = newWord || words.length == 0 ? "" : words[words.length - 1];
        String subcommand = words.length == 0 ? "" : words[0].toLowerCase(Locale.ROOT);
        String[] completed = newWord ? words : Arrays.copyOf(words, words.length - 1);
        List<String> positionals = new ArrayList<>();
        for (int i = 1; i < completed.length; i++) {
            if (Set.of("-w", "-g", "-p", "-i", "-h").contains(completed[i])) {
                i++;
            } else if (!completed[i].startsWith("-")) {
                positionals.add(completed[i]);
            }
        }
        String lastCompleted = completed.length == 0 ? "" : completed[completed.length - 1];
        int offset = builder.getStart() + remaining.length() - current.length();
        SuggestionsBuilder target = builder.createOffset(offset);

        String rootAlias = context.getNodes().isEmpty() ? "rg"
                : context.getNodes().get(0).getNode().getName();
        if (FIRE_ALIASES.contains(rootAlias)) {
            for (var level : context.getSource().getServer().getAllLevels()) {
                add(target, current, level.dimension().location().toString());
            }
            return target.buildFuture();
        }
        if (GENERAL_ALIASES.contains(rootAlias)) {
            for (var player : context.getSource().getServer().getPlayerList().getPlayers()) {
                add(target, current, player.getGameProfile().getName());
            }
            return target.buildFuture();
        }

        if (completed.length == 0) {
            Method root = commands.getMethods().get(null).get(rootAlias);
            Map<String, Method> children = commands.getMethods().get(root);
            children.keySet().stream().sorted().forEach(value -> add(target, current, value));
        } else if (lastCompleted.equals("-w")) {
            for (var level : context.getSource().getServer().getAllLevels()) {
                add(target, current, level.dimension().location().toString());
            }
        } else if (lastCompleted.equals("-g") && (subcommand.equals("flag") || subcommand.equals("f"))) {
            for (RegionGroup group : RegionGroup.values()) {
                add(target, current, group.name().toLowerCase(Locale.ROOT).replace("_", ""));
            }
        } else if (positionals.isEmpty() && REGION_ARGUMENTS.contains(subcommand)) {
            if (context.getSource().getPlayer() != null) {
                RegionManager manager = platform.getRegionContainer().get(
                        NeoForgeAdapter.adapt(context.getSource().getPlayer().serverLevel()));
                if (manager != null) {
                    manager.getRegions().keySet().stream().sorted().forEach(value -> add(target, current, value));
                }
            }
            add(target, current, "__global__");
        } else if (positionals.size() == 1 && (subcommand.equals("flag") || subcommand.equals("f"))) {
            for (var flag : WorldGuard.getInstance().getFlagRegistry()) {
                add(target, current, flag.getName());
            }
        } else if (!positionals.isEmpty() && PLAYER_ARGUMENTS.contains(subcommand)) {
            for (var player : context.getSource().getServer().getPlayerList().getPlayers()) {
                add(target, current, player.getGameProfile().getName());
            }
        }
        return target.buildFuture();
    }

    private static void add(SuggestionsBuilder builder, String prefix, String value) {
        if (value.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) {
            builder.suggest(value);
        }
    }

    private static String modVersion(String id) {
        return ModList.get().getModContainerById(id)
                .map(container -> container.getModInfo().getVersion().toString()).orElse("unknown");
    }
}
