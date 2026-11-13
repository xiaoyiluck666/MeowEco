package com.xiaoyiluck.meoweco.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xiaoyiluck.meoweco.MeowEco;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdateChecker {
    private static final Pattern NUMERIC_VERSION = Pattern.compile("^[vV]?([0-9]+(?:\\.[0-9]+)*)(?:[-+].*)?$");

    private final MeowEco plugin;
    private final String currentVersion;
    private final String slug;
    private final String minecraftVersion;
    private volatile String latestVersion;
    private volatile String downloadUrl;

    public UpdateChecker(MeowEco plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getPluginMeta().getVersion();
        this.slug = plugin.getConfig().getString("update-checker.slug", "meoweco");
        this.minecraftVersion = plugin.getServer().getMinecraftVersion();
    }

    public CompletableFuture<Boolean> check() {
        return CompletableFuture.supplyAsync(() -> {
            latestVersion = null;
            downloadUrl = null;
            try {
                // A more recently uploaded build may target a different Minecraft branch.
                String gameVersions = URLEncoder.encode("[\"" + minecraftVersion + "\"]", StandardCharsets.UTF_8);
                String loaders = URLEncoder.encode("[\"paper\"]", StandardCharsets.UTF_8);
                var url = new URI("https://api.modrinth.com/v2/project/" + slug
                        + "/version?game_versions=" + gameVersions + "&loaders=" + loaders).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                try {
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("User-Agent", "MeowEco-UpdateChecker/" + currentVersion);
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    if (conn.getResponseCode() != 200) return false;

                    StringBuilder response = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) response.append(line);
                    }

                    Optional<String> update = findCompatibleUpdate(response.toString(), minecraftVersion, currentVersion);
                    if (update.isPresent()) {
                        latestVersion = update.get();
                        downloadUrl = "https://modrinth.com/plugin/" + slug + "/versions";
                    }
                    return update.isPresent();
                } finally {
                    conn.disconnect();
                }
            } catch (Exception e) {
                plugin.debug("Failed to check for updates: " + e.getMessage());
                return false;
            }
        });
    }

    static Optional<String> findCompatibleUpdate(String json, String minecraftVersion, String currentVersion) {
        JsonArray versions = JsonParser.parseString(json).getAsJsonArray();
        String selected = currentVersion;
        for (JsonElement element : versions) {
            if (!element.isJsonObject()) continue;
            JsonObject version = element.getAsJsonObject();
            // Verify compatibility locally as well as applying the API query filters.
            if (!contains(version, "game_versions", minecraftVersion)
                    || !contains(version, "loaders", "paper")) continue;
            JsonElement number = version.get("version_number");
            if (number == null || !number.isJsonPrimitive() || !number.getAsJsonPrimitive().isString()) continue;
            String candidate = number.getAsString();
            if (isNewer(candidate, selected)) selected = candidate;
        }
        return selected.equals(currentVersion) ? Optional.empty() : Optional.of(selected);
    }

    private static boolean contains(JsonObject version, String field, String expected) {
        JsonElement values = version.get(field);
        if (values == null || !values.isJsonArray()) return false;
        for (JsonElement value : values.getAsJsonArray()) {
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                    && value.getAsString().equals(expected)) return true;
        }
        return false;
    }

    static boolean isNewer(String latest, String current) {
        String[] latestParts = numericParts(latest);
        String[] currentParts = numericParts(current);
        if (latestParts.length == 0 || currentParts.length == 0) return false;
        int length = Math.max(latestParts.length, currentParts.length);
        for (int i = 0; i < length; i++) {
            BigInteger l = i < latestParts.length ? new BigInteger(latestParts[i]) : BigInteger.ZERO;
            BigInteger c = i < currentParts.length ? new BigInteger(currentParts[i]) : BigInteger.ZERO;
            int comparison = l.compareTo(c);
            if (comparison != 0) return comparison > 0;
        }
        return false;
    }

    private static String[] numericParts(String version) {
        // Build metadata and Minecraft target suffixes are not numeric release components.
        Matcher matcher = NUMERIC_VERSION.matcher(version.trim());
        return matcher.matches() ? matcher.group(1).split("\\.") : new String[0];
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }
}
