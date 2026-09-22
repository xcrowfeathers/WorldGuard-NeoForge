package de.kuro.worldguard.platform;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sk89q.worldguard.util.profile.Profile;
import com.sk89q.worldguard.util.profile.resolver.ProfileService;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

final class KnownProfileService implements ProfileService {
    private final ProfileService fallback;
    private final Map<String, Profile> byName = new ConcurrentHashMap<>();
    private final Map<UUID, Profile> byId = new ConcurrentHashMap<>();

    KnownProfileService(ProfileService fallback, Path serverUserCache) {
        this.fallback = fallback;
        if (!Files.isRegularFile(serverUserCache)) return;
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(serverUserCache));
            if (parsed instanceof JsonArray entries) {
                for (JsonElement entry : entries) {
                    if (!(entry instanceof JsonObject object)
                            || !object.has("uuid") || !object.has("name")) continue;
                    try {
                        remember(new Profile(UUID.fromString(object.get("uuid").getAsString()),
                                object.get("name").getAsString()));
                    } catch (IllegalArgumentException ignored) { }
                }
            }
        } catch (IOException | RuntimeException error) {
            com.sk89q.worldguard.WorldGuard.logger.warning(
                    "Could not read Minecraft's known-player cache: " + error.getMessage());
        }
    }

    void remember(Profile profile) {
        byName.put(profile.getName().toLowerCase(Locale.ROOT), profile);
        byId.put(profile.getUniqueId(), profile);
    }

    @Override public int getIdealRequestLimit() { return fallback.getIdealRequestLimit(); }

    @Override @Nullable
    public Profile findByName(String name) throws IOException, InterruptedException {
        Profile known = byName.get(name.toLowerCase(Locale.ROOT));
        if (known != null) return known;
        Profile found = fallback.findByName(name);
        if (found != null) remember(found);
        return found;
    }

    @Override @Nullable
    public Profile findByUuid(UUID uuid) throws IOException, InterruptedException {
        Profile known = byId.get(uuid);
        if (known != null) return known;
        Profile found = fallback.findByUuid(uuid);
        if (found != null) remember(found);
        return found;
    }

    @Override
    public ImmutableList<Profile> findAllByName(Iterable<String> names) throws IOException, InterruptedException {
        List<Profile> result = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String name : names) {
            Profile known = byName.get(name.toLowerCase(Locale.ROOT));
            if (known == null) missing.add(name); else result.add(known);
        }
        for (Profile found : fallback.findAllByName(missing)) {
            remember(found);
            result.add(found);
        }
        return ImmutableList.copyOf(result);
    }

    @Override
    public void findAllByName(Iterable<String> names, Predicate<Profile> consumer)
            throws IOException, InterruptedException {
        for (Profile profile : findAllByName(names)) consumer.test(profile);
    }

    @Override
    public ImmutableList<Profile> findAllByUuid(Iterable<UUID> uuids) throws IOException, InterruptedException {
        List<Profile> result = new ArrayList<>();
        List<UUID> missing = new ArrayList<>();
        for (UUID uuid : uuids) {
            Profile known = byId.get(uuid);
            if (known == null) missing.add(uuid); else result.add(known);
        }
        for (Profile found : fallback.findAllByUuid(missing)) {
            remember(found);
            result.add(found);
        }
        return ImmutableList.copyOf(result);
    }

    @Override
    public void findAllByUuid(Iterable<UUID> uuids, Predicate<Profile> consumer)
            throws IOException, InterruptedException {
        for (Profile profile : findAllByUuid(uuids)) consumer.test(profile);
    }
}
