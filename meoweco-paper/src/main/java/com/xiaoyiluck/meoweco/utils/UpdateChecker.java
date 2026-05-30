package com.xiaoyiluck.meoweco.utils;

import com.xiaoyiluck.meoweco.MeowEco;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdateChecker {

    private final MeowEco plugin;
    private final String currentVersion;
    private final String slug;
    private String latestVersion;
    private String downloadUrl;

    public UpdateChecker(MeowEco plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getPluginMeta().getVersion();
        this.slug = plugin.getConfig().getString("update-checker.slug", "meoweco");
    }

    public CompletableFuture<Boolean> check() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Modrinth API V2: /project/{slug}/version
                // This returns a JSON array of versions, first one is the latest
                URL url = new URI("https://api.modrinth.com/v2/project/" + slug + "/version").toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "MeowEco-UpdateChecker/" + currentVersion);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                if (conn.getResponseCode() != 200) {
                    return false;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                String json = response.toString();
                // Simple parsing without external libraries (like Gson) to keep it lightweight
                // The first version in the array is the latest.
                // We look for "version_number":"..."
                latestVersion = parseJsonField(json, "version_number");
                
                if (latestVersion == null) return false;

                // Compare versions
                boolean hasUpdate = isNewer(latestVersion, currentVersion);
                if (hasUpdate) {
                    downloadUrl = "https://modrinth.com/plugin/" + slug + "/versions";
                }
                return hasUpdate;

            } catch (Exception e) {
                plugin.debug("Failed to check for updates: " + e.getMessage());
                return false;
            }
        });
    }

    private String parseJsonField(String json, String field) {
        Pattern pattern = Pattern.compile("\"" + field + "\":\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private boolean isNewer(String latest, String current) {
        // Clean version strings (remove 'v' prefix if present)
        latest = latest.toLowerCase().replace("v", "");
        current = current.toLowerCase().replace("v", "");

        String[] latestParts = latest.split("\\.");
        String[] currentParts = current.split("\\.");

        int length = Math.max(latestParts.length, currentParts.length);
        for (int i = 0; i < length; i++) {
            int l = i < latestParts.length ? tryParseInt(latestParts[i]) : 0;
            int c = i < currentParts.length ? tryParseInt(currentParts[i]) : 0;
            if (l > c) return true;
            if (l < c) return false;
        }
        return false;
    }

    private int tryParseInt(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }
}
