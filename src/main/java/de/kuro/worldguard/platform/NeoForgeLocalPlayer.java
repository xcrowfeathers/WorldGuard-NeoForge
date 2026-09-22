package de.kuro.worldguard.platform;

import com.sk89q.worldedit.neoforge.NeoForgeAdapter;
import com.sk89q.worldedit.neoforge.NeoForgePlayer;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.weather.WeatherType;
import com.sk89q.worldedit.world.weather.WeatherTypes;
import com.sk89q.worldedit.world.gamemode.GameMode;
import com.sk89q.worldedit.world.gamemode.GameModes;
import com.sk89q.worldguard.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.world.level.GameType;

public final class NeoForgeLocalPlayer extends NeoForgePlayer implements LocalPlayer {
    private final ServerPlayer player;
    private final NeoForgeWorldGuardPlatform platform;
    private WeatherType weatherOverride;
    private Long timeOverride;
    private boolean relativeTime = true;
    private boolean applyingRegionGameMode;

    NeoForgeLocalPlayer(ServerPlayer player, NeoForgeWorldGuardPlatform platform) {
        super(player);
        this.player = player;
        this.platform = platform;
    }

    public ServerPlayer getHandle() {
        return player;
    }

    @Override
    public boolean setLocation(Location location) {
        ServerLevel target = NeoForgeAdapter.adapt((World) location.getExtent());
        player.teleportTo(target, location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch());
        return player.serverLevel() == target && Math.abs(player.getX() - location.getX()) < 0.01
                && Math.abs(player.getY() - location.getY()) < 0.01
                && Math.abs(player.getZ() - location.getZ()) < 0.01;
    }

    public boolean isApplyingRegionGameMode() { return applyingRegionGameMode; }

    @Override
    public GameMode getGameMode() {
        return GameModes.get(player.gameMode.getGameModeForPlayer().getName());
    }

    @Override
    public void setGameMode(GameMode mode) {
        GameType target = GameType.byName(mode.id());
        if (target == null) throw new IllegalArgumentException("Unknown game mode: " + mode.id());
        applyingRegionGameMode = true;
        try {
            player.setGameMode(target);
        } finally {
            applyingRegionGameMode = false;
        }
    }

    @Override
    public boolean hasGroup(String group) {
        return platform.permissionResolver().hasGroup(player, group);
    }

    @Override
    public String[] getGroups() {
        return platform.permissionResolver().getGroups(player);
    }

    @Override
    public boolean hasPermission(String permission) {
        return platform.permissionResolver().hasPermission(player, permission);
    }

    @Override
    public void kick(String message) {
        player.connection.disconnect(Component.literal(message));
    }

    @Override
    public void ban(String message) {
        player.server.getPlayerList().getBans().add(new UserBanListEntry(
                player.getGameProfile(), null, "WorldGuard", null, message));
        kick(message);
    }

    @Override
    public double getHealth() {
        return player.getHealth();
    }

    @Override
    public void setHealth(double health) {
        player.setHealth((float) health);
    }

    @Override
    public double getMaxHealth() {
        return player.getMaxHealth();
    }

    @Override
    public double getFoodLevel() {
        return player.getFoodData().getFoodLevel();
    }

    @Override
    public void setFoodLevel(double foodLevel) {
        player.getFoodData().setFoodLevel((int) foodLevel);
    }

    @Override
    public double getSaturation() {
        return player.getFoodData().getSaturationLevel();
    }

    @Override
    public void setSaturation(double saturation) {
        player.getFoodData().setSaturation((float) saturation);
    }

    @Override
    public float getExhaustion() {
        return player.getFoodData().getExhaustionLevel();
    }

    @Override
    public void setExhaustion(float exhaustion) {
        player.getFoodData().setExhaustion(exhaustion);
    }

    @Override
    public WeatherType getPlayerWeather() {
        return weatherOverride;
    }

    @Override
    public void setPlayerWeather(WeatherType weather) {
        weatherOverride = weather;
        sendWeather(weather == WeatherTypes.CLEAR ? 0 : 1,
                weather == WeatherTypes.THUNDER_STORM ? 1 : 0);
    }

    @Override
    public void resetPlayerWeather() {
        weatherOverride = null;
        sendWeather(player.serverLevel().getRainLevel(1), player.serverLevel().getThunderLevel(1));
    }

    private void sendWeather(float rain, float thunder) {
        player.connection.send(new ClientboundGameEventPacket(
                rain > 0 ? ClientboundGameEventPacket.START_RAINING : ClientboundGameEventPacket.STOP_RAINING, 0));
        player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, rain));
        player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, thunder));
    }

    @Override
    public boolean isPlayerTimeRelative() {
        return relativeTime;
    }

    @Override
    public long getPlayerTimeOffset() {
        return timeOverride == null ? 0 : timeOverride;
    }

    @Override
    public void setPlayerTime(long time, boolean relative) {
        if (relative && time == 0) {
            resetPlayerTime();
            return;
        }
        timeOverride = time;
        relativeTime = relative;
        syncTimeView();
    }

    @Override
    public void resetPlayerTime() {
        timeOverride = null;
        relativeTime = true;
        player.connection.send(new ClientboundSetTimePacket(player.serverLevel().getGameTime(),
                player.serverLevel().getDayTime(), player.serverLevel().getGameRules().getBoolean(
                        net.minecraft.world.level.GameRules.RULE_DAYLIGHT)));
    }

    @Override
    public int getFireTicks() {
        return player.getRemainingFireTicks();
    }

    @Override
    public void setFireTicks(int fireTicks) {
        player.setRemainingFireTicks(fireTicks);
    }

    @Override
    public void setCompassTarget(Location location) {
        BlockPos target = NeoForgeAdapter.toBlockPos(location.toVector().toBlockPoint());
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket(target, 0));
    }

    @Override
    public void sendTitle(String title, String subtitle) {
        if (platform.getGlobalStateManager().get(getWorld()).forceDefaultTitleTimes) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
        }
        player.connection.send(new ClientboundSetTitleTextPacket(legacy(title)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle == null ? Component.empty() : legacy(subtitle)));
    }

    @Override
    public void printRaw(String message) {
        for (String line : message.split("\\n", -1)) player.sendSystemMessage(legacy(line));
    }

    /** Vanilla time/weather packets can overwrite player-only view locks. */
    void syncView() {
        syncTimeView();
        if (weatherOverride != null) sendWeather(weatherOverride == WeatherTypes.CLEAR ? 0 : 1,
                weatherOverride == WeatherTypes.THUNDER_STORM ? 1 : 0);
    }

    private void syncTimeView() {
        if (timeOverride == null) return;
        long dayTime = relativeTime ? player.serverLevel().getDayTime() + timeOverride : timeOverride;
        player.connection.send(new ClientboundSetTimePacket(player.serverLevel().getGameTime(), dayTime,
                relativeTime && player.serverLevel().getGameRules().getBoolean(
                        net.minecraft.world.level.GameRules.RULE_DAYLIGHT)));
    }

    private static Component legacy(String input) {
        MutableComponent result = Component.empty();
        Style style = Style.EMPTY;
        int start = 0;
        for (int i = 0; i < input.length() - 1; i++) {
            if (input.charAt(i) != '\u00a7') continue;
            ChatFormatting format = ChatFormatting.getByCode(input.charAt(i + 1));
            if (format == null) continue;
            if (i > start) result.append(Component.literal(input.substring(start, i)).withStyle(style));
            style = format == ChatFormatting.RESET ? Style.EMPTY
                    : format.isColor() ? Style.EMPTY.applyFormat(format) : style.applyFormat(format);
            start = ++i + 1;
        }
        if (start < input.length()) result.append(Component.literal(input.substring(start)).withStyle(style));
        return result;
    }

    @Override
    public void resetFallDistance() {
        player.resetFallDistance();
    }

    @Override
    public void teleport(Location location, String successMessage, String failMessage) {
        print(setLocation(location) ? successMessage : failMessage);
    }
}
