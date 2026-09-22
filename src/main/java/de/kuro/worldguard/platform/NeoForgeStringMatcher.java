package de.kuro.worldguard.platform;

import com.sk89q.minecraft.util.commands.CommandException;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.neoforge.NeoForgeAdapter;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.internal.platform.StringMatcher;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ServerLevelData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Name matching against live NeoForge dimensions and players. */
final class NeoForgeStringMatcher implements StringMatcher {
    private final NeoForgeWorldGuardPlatform platform;

    NeoForgeStringMatcher(NeoForgeWorldGuardPlatform platform) {
        this.platform = platform;
    }

    @Override
    public World matchWorld(Actor sender, String filter) throws CommandException {
        String name = switch (filter.toLowerCase(Locale.ROOT)) {
            case "#main", "#normal", "overworld" -> "minecraft:overworld";
            case "#nether", "nether", "the_nether" -> "minecraft:the_nether";
            case "#end", "end", "the_end" -> "minecraft:the_end";
            default -> filter;
        };
        if (name.startsWith("#player:")) {
            for (LocalPlayer player : platform.onlinePlayers()) {
                if (player.getName().equalsIgnoreCase(name.substring(8))) {
                    return (World) player.getLocation().getExtent();
                }
            }
        }
        World world = getWorldByName(name);
        if (world == null) {
            int displayMatches = 0;
            for (ServerLevel level : platform.server().getAllLevels()) {
                if (((ServerLevelData) level.getLevelData()).getLevelName().equalsIgnoreCase(name)) {
                    displayMatches++;
                }
            }
            if (displayMatches > 1) {
                throw new CommandException("World name '" + filter
                        + "' is shared by multiple dimensions; use a dimension ID such as minecraft:overworld.");
            }
            throw new CommandException("No world matched '" + filter + "'.");
        }
        return world;
    }

    @Override
    public List<LocalPlayer> matchPlayerNames(String filter) {
        String lower = filter.toLowerCase(Locale.ROOT);
        boolean exact = lower.startsWith("@");
        boolean contains = lower.startsWith("*");
        String query = exact || contains ? lower.substring(1) : lower;
        List<LocalPlayer> matches = new ArrayList<>();
        for (LocalPlayer player : platform.onlinePlayers()) {
            String name = player.getName().toLowerCase(Locale.ROOT);
            if (exact ? name.equals(query) : contains ? name.contains(query) : name.startsWith(query)) {
                matches.add(player);
            }
        }
        return matches;
    }

    @Override
    public Iterable<? extends LocalPlayer> matchPlayers(Actor source, String filter) throws CommandException {
        if (filter.equals("*")) {
            return checkPlayerMatch(new ArrayList<>(platform.onlinePlayers()));
        }
        if (filter.equals("#world") && source instanceof NeoForgeLocalPlayer sourcePlayer) {
            List<LocalPlayer> matches = new ArrayList<>();
            for (LocalPlayer player : platform.onlinePlayers()) {
                if (((NeoForgeLocalPlayer) player).getHandle().serverLevel() == sourcePlayer.getHandle().serverLevel()) {
                    matches.add(player);
                }
            }
            return checkPlayerMatch(matches);
        }
        if (filter.equals("#near") && source instanceof NeoForgeLocalPlayer sourcePlayer) {
            List<LocalPlayer> matches = new ArrayList<>();
            for (LocalPlayer player : platform.onlinePlayers()) {
                NeoForgeLocalPlayer candidate = (NeoForgeLocalPlayer) player;
                if (candidate.getHandle().serverLevel() == sourcePlayer.getHandle().serverLevel()
                        && candidate.getHandle().distanceToSqr(sourcePlayer.getHandle()) <= 900) {
                    matches.add(candidate);
                }
            }
            return checkPlayerMatch(matches);
        }
        return checkPlayerMatch(matchPlayerNames(filter));
    }

    @Override
    public Actor matchPlayerOrConsole(Actor sender, String filter) throws CommandException {
        if (filter.equals("!") || filter.equalsIgnoreCase("#console")
                || filter.equalsIgnoreCase("*console")) {
            return NeoForgeAdapter.adaptCommandSource(platform.server().createCommandSourceStack());
        }
        return matchSinglePlayer(sender, filter);
    }

    @Override
    @Nullable
    public World getWorldByName(String worldName) {
        ServerLevel nameMatch = null;
        for (ServerLevel level : platform.server().getAllLevels()) {
            if (level.dimension().location().toString().equalsIgnoreCase(worldName)) {
                return NeoForgeAdapter.adapt(level);
            }
            if (((ServerLevelData) level.getLevelData()).getLevelName().equalsIgnoreCase(worldName)) {
                if (nameMatch != null) {
                    return null; // Shared save name is ambiguous across dimensions.
                }
                nameMatch = level;
            }
        }
        return nameMatch == null ? null : NeoForgeAdapter.adapt(nameMatch);
    }

    @Override
    public String replaceMacros(Actor sender, String message) {
        String result = message.replace("%name%", sender.getName())
                .replace("%id%", sender.getUniqueId().toString())
                .replace("%online%", Integer.toString(platform.server().getPlayerCount()));
        if (sender instanceof LocalPlayer player) {
            result = result.replace("%world%", NeoForgeAdapter.adapt((World) player.getLocation().getExtent())
                            .dimension().location().toString())
                    .replace("%health%", Double.toString(player.getHealth()));
        }
        return result;
    }
}
