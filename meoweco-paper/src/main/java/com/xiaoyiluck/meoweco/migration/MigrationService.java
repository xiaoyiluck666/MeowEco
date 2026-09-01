package com.xiaoyiluck.meoweco.migration;

import com.xiaoyiluck.meoweco.MeowEco;
import com.xiaoyiluck.meoweco.api.MeowEconomy;
import com.xiaoyiluck.meoweco.database.MigrationBalance;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class MigrationService {
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private final MeowEco plugin;

    public MigrationService(MeowEco plugin) {
        this.plugin = plugin;
    }

    public List<String> vaultSources() {
        return plugin.getServer().getServicesManager().getRegistrations(Economy.class).stream()
                .filter(registration -> !isMeowEco(registration))
                .map(registration -> registration.getPlugin().getName())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    public MigrationPreview previewVault(String requestedSource) {
        RegisteredServiceProvider<Economy> registration = plugin.getServer().getServicesManager()
                .getRegistrations(Economy.class).stream()
                .filter(candidate -> !isMeowEco(candidate))
                .filter(candidate -> requestedSource == null || requestedSource.isBlank()
                        || candidate.getPlugin().getName().equalsIgnoreCase(requestedSource)
                        || candidate.getProvider().getName().equalsIgnoreCase(requestedSource))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No source Vault economy found. Available: " + String.join(", ", vaultSources())));

        Economy economy = registration.getProvider();
        Map<UUID, MigrationBalance> balances = new LinkedHashMap<>();
        int skipped = 0;
        for (OfflinePlayer player : plugin.getServer().getOfflinePlayers()) {
            try {
                if (!economy.hasAccount(player)) {
                    continue;
                }
                double balance = economy.getBalance(player);
                if (!validBalance(balance)) {
                    skipped++;
                    continue;
                }
                balances.put(player.getUniqueId(), new MigrationBalance(
                        player.getUniqueId(), safeName(player.getName()), balance));
            } catch (RuntimeException ignored) {
                skipped++;
            }
        }
        String source = registration.getPlugin().getName() + " / " + economy.getName();
        return preview("vault", source, balances, skipped, List.of(
                "Keep the source economy plugin enabled until the migration finishes."));
    }

    public MigrationPreview previewEssentials(Path userdataDirectory) throws IOException {
        if (!Files.isDirectory(userdataDirectory)) {
            throw new IOException("Essentials userdata directory not found: " + userdataDirectory);
        }
        Map<UUID, MigrationBalance> balances = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        int skipped = 0;
        try (var files = Files.list(userdataDirectory)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".yml")).sorted().toList()) {
                String fileName = file.getFileName().toString();
                try {
                    UUID uuid = UUID.fromString(fileName.substring(0, fileName.length() - 4));
                    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
                    Object rawMoney = yaml.get("money");
                    if (rawMoney == null) {
                        continue;
                    }
                    double balance = parseAmount(rawMoney);
                    if (!validBalance(balance)) {
                        skipped++;
                        continue;
                    }
                    String username = yaml.getString("last-account-name", "Unknown");
                    balances.put(uuid, new MigrationBalance(uuid, safeName(username), balance));
                } catch (IllegalArgumentException exception) {
                    skipped++;
                    if (warnings.size() < 10) {
                        warnings.add(fileName + ": " + exception.getMessage());
                    }
                }
            }
        }
        return preview("essentials", userdataDirectory.toAbsolutePath().toString(), balances, skipped, warnings);
    }

    public MigrationPreview previewCsv(Path csvFile) throws IOException {
        if (!Files.isRegularFile(csvFile)) {
            throw new IOException("CSV file not found: " + csvFile);
        }
        Map<UUID, MigrationBalance> balances = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        int skipped = 0;
        try (BufferedReader reader = Files.newBufferedReader(csvFile, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (header == null) {
                throw new IOException("CSV file is empty.");
            }
            List<String> columns = parseCsvLine(stripBom(header));
            int uuidIndex = findColumn(columns, "uuid");
            int usernameIndex = findColumn(columns, "username", "name", "player");
            int balanceIndex = findColumn(columns, "balance", "money", "points", "amount");
            if (uuidIndex < 0 || balanceIndex < 0) {
                throw new IOException("CSV requires uuid and balance columns.");
            }

            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                try {
                    List<String> values = parseCsvLine(line);
                    UUID uuid = UUID.fromString(valueAt(values, uuidIndex));
                    double balance = parseAmount(valueAt(values, balanceIndex));
                    if (!validBalance(balance)) {
                        throw new IllegalArgumentException("balance must be a finite non-negative number");
                    }
                    String username = usernameIndex < 0 ? "Unknown" : valueAt(values, usernameIndex);
                    balances.put(uuid, new MigrationBalance(uuid, safeName(username), balance));
                } catch (IllegalArgumentException exception) {
                    skipped++;
                    if (warnings.size() < 10) {
                        warnings.add("line " + lineNumber + ": " + exception.getMessage());
                    }
                }
            }
        }
        return preview("csv", csvFile.toAbsolutePath().toString(), balances, skipped, warnings);
    }

    public Path writeBackup(String currency, List<MigrationBalance> incoming) throws IOException {
        Path directory = plugin.getDataFolder().toPath().resolve("migration-backups");
        Files.createDirectories(directory);
        Path backup = directory.resolve("before-" + cleanFilePart(currency) + "-"
                + FILE_TIME.format(LocalDateTime.now()) + ".csv");
        List<MigrationBalance> current = incoming.stream()
                .filter(balance -> plugin.getDatabaseManager().hasAccount(balance.uuid(), currency))
                .map(balance -> new MigrationBalance(balance.uuid(), balance.username(),
                        plugin.getDatabaseManager().findBalance(balance.uuid(), currency).orElse(0.0D)))
                .sorted(Comparator.comparing(balance -> balance.uuid().toString()))
                .toList();
        writeCsv(backup, current);
        return backup;
    }

    public Path exportAudit(List<com.xiaoyiluck.meoweco.database.AuditEntry> entries) throws IOException {
        Path directory = plugin.getDataFolder().toPath().resolve("audit-exports");
        Files.createDirectories(directory);
        Path output = directory.resolve("audit-" + FILE_TIME.format(LocalDateTime.now()) + ".csv");
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW)) {
            writer.write("id,transaction_id,created_at,uuid,username,currency,operation,amount,balance_before,balance_after,frozen_before,frozen_after,source,actor");
            writer.newLine();
            for (var entry : entries) {
                writeCsvRow(writer, List.of(
                        Long.toString(entry.id()), entry.transactionId(), entry.createdAt().toString(),
                        entry.accountUuid().toString(), entry.username(), entry.currency(), entry.operation(),
                        decimal(entry.amount()), decimal(entry.balanceBefore()), decimal(entry.balanceAfter()),
                        decimal(entry.frozenBefore()), decimal(entry.frozenAfter()), entry.source(), entry.actor()));
            }
        }
        return output;
    }

    public Path resolveInput(String fileName) throws IOException {
        Path directory = plugin.getDataFolder().toPath().resolve("migration-input").toAbsolutePath().normalize();
        Files.createDirectories(directory);
        Path resolved = directory.resolve(fileName).normalize();
        if (!resolved.startsWith(directory)) {
            throw new IOException("Migration input must stay inside " + directory);
        }
        return resolved;
    }

    public Path defaultEssentialsUserdata() {
        return plugin.getDataFolder().toPath().resolveSibling("Essentials").resolve("userdata");
    }

    private boolean isMeowEco(RegisteredServiceProvider<Economy> registration) {
        return registration.getPlugin().equals(plugin) || registration.getProvider() instanceof MeowEconomy;
    }

    private MigrationPreview preview(String adapter, String source, Map<UUID, MigrationBalance> balances,
                                     int skipped, List<String> warnings) {
        List<MigrationBalance> sorted = balances.values().stream()
                .sorted(Comparator.comparing(balance -> balance.uuid().toString()))
                .toList();
        double total = sorted.stream().mapToDouble(MigrationBalance::balance).sum();
        return new MigrationPreview(adapter, source, sorted, skipped, total, List.copyOf(warnings));
    }

    private void writeCsv(Path output, List<MigrationBalance> balances) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW)) {
            writer.write("uuid,username,balance");
            writer.newLine();
            for (MigrationBalance balance : balances) {
                writeCsvRow(writer, List.of(balance.uuid().toString(), balance.username(), decimal(balance.balance())));
            }
        }
    }

    private void writeCsvRow(BufferedWriter writer, List<String> values) throws IOException {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                writer.write(',');
            }
            writer.write(escapeCsv(values.get(i)));
        }
        writer.newLine();
    }

    static List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char current = line.charAt(i);
            if (current == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    value.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (current == ',' && !quoted) {
                values.add(value.toString().trim());
                value.setLength(0);
            } else {
                value.append(current);
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("unclosed quoted field");
        }
        values.add(value.toString().trim());
        return values;
    }

    private int findColumn(List<String> columns, String... names) {
        for (int i = 0; i < columns.size(); i++) {
            String column = columns.get(i).trim().toLowerCase(Locale.ROOT);
            for (String name : names) {
                if (column.equals(name)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private String valueAt(List<String> values, int index) {
        if (index < 0 || index >= values.size()) {
            throw new IllegalArgumentException("missing column value");
        }
        return values.get(index);
    }

    private double parseAmount(Object raw) {
        try {
            return new BigDecimal(String.valueOf(raw).trim()).doubleValue();
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("invalid balance: " + raw);
        }
    }

    private boolean validBalance(double balance) {
        return Double.isFinite(balance) && balance >= 0.0D;
    }

    private String safeName(String name) {
        if (name == null || name.isBlank()) {
            return "Unknown";
        }
        String clean = name.trim();
        return clean.length() <= 16 ? clean : clean.substring(0, 16);
    }

    private String cleanFilePart(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String decimal(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private String escapeCsv(String value) {
        String clean = value == null ? "" : value;
        if (clean.indexOf(',') >= 0 || clean.indexOf('"') >= 0 || clean.indexOf('\n') >= 0 || clean.indexOf('\r') >= 0) {
            return '"' + clean.replace("\"", "\"\"") + '"';
        }
        return clean;
    }

    private String stripBom(String value) {
        return value.startsWith("\uFEFF") ? value.substring(1) : value;
    }
}
