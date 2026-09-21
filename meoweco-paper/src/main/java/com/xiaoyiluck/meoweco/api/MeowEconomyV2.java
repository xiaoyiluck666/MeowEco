package com.xiaoyiluck.meoweco.api;

import com.xiaoyiluck.meoweco.MeowEco;
import com.xiaoyiluck.meoweco.database.AuditScope;
import com.xiaoyiluck.meoweco.database.DatabaseManager;
import com.xiaoyiluck.meoweco.lifecycle.VaultAsyncOperationManager;
import com.xiaoyiluck.meoweco.objects.Currency;
import com.xiaoyiluck.meoweco.service.EconomyService;
import com.xiaoyiluck.meoweco.service.MoneyAmountPolicy;
import net.milkbowl.vault2.economy.AccountPermission;
import net.milkbowl.vault2.economy.AsyncEconomy;
import net.milkbowl.vault2.economy.EconomyResponse;
import net.milkbowl.vault2.economy.MultiEconomyResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

@SuppressWarnings("deprecation")
public final class MeowEconomyV2 implements net.milkbowl.vault2.economy.Economy {
    private static final String PROVIDER_NAME = "MeowEco";
    private static final String AUDIT_SOURCE = "vault";
    private static final String AUDIT_ACTOR = "external_plugin";

    interface Context {
        boolean isEnabled();
        Map<String, Currency> currencies();
        Currency currency(String id);
        Currency defaultCurrency();
        DatabaseManager database();
        EconomyService economyService();
        void invalidate(UUID uuid, String currencyId);
    }

    private final Context context;
    private final AsyncEconomy asyncEconomy;

    public MeowEconomyV2(MeowEco plugin, VaultAsyncOperationManager asyncOperations) {
        this(new PluginContext(plugin), asyncOperations);
    }

    MeowEconomyV2(Context context, Executor executor) {
        this(context, new VaultAsyncOperationManager(executor));
    }

    MeowEconomyV2(Context context, VaultAsyncOperationManager asyncOperations) {
        this.context = context;
        this.asyncEconomy = new AsyncAdapter(asyncOperations);
    }

    @Override
    public boolean isEnabled() {
        return context.isEnabled();
    }

    @Override
    public String getName() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean hasSharedAccountSupport() {
        return false;
    }

    @Override
    public boolean hasMultiCurrencySupport() {
        return true;
    }

    @Override
    public boolean supportsAsync() {
        return true;
    }

    @Override
    public Optional<AsyncEconomy> async() {
        return Optional.of(asyncEconomy);
    }

    @Override
    public int fractionalDigits(String pluginName) {
        return MoneyAmountPolicy.decimalPlaces(context.defaultCurrency());
    }

    @Override
    public int fractionalDigits(String pluginName, String currency) {
        Currency resolved = resolveCurrency(currency);
        return resolved == null ? fractionalDigits(pluginName) : MoneyAmountPolicy.decimalPlaces(resolved);
    }

    @Override
    public String format(BigDecimal amount) {
        return format("unknown", amount);
    }

    @Override
    public String format(String pluginName, BigDecimal amount) {
        return format(pluginName, amount, context.defaultCurrency().getId());
    }

    @Override
    public String format(BigDecimal amount, String currency) {
        return format("unknown", amount, currency);
    }

    @Override
    public String format(String pluginName, BigDecimal amount, String currency) {
        Currency resolved = resolveCurrency(currency);
        if (resolved == null || amount == null) {
            return amount == null ? "0" : amount.toPlainString();
        }
        BigDecimal normalized = amount.setScale(MoneyAmountPolicy.decimalPlaces(resolved), RoundingMode.HALF_UP);
        String unit = normalized.abs().compareTo(BigDecimal.ONE) == 0 ? resolved.getSingular() : resolved.getPlural();
        return normalized.toPlainString() + " " + unit;
    }

    @Override
    public boolean hasCurrency(String currency) {
        return resolveCurrency(currency) != null;
    }

    @Override
    public String getDefaultCurrency(String pluginName) {
        return context.defaultCurrency().getId();
    }

    @Override
    public String defaultCurrencyNamePlural(String pluginName) {
        return context.defaultCurrency().getPlural();
    }

    @Override
    public String defaultCurrencyNameSingular(String pluginName) {
        return context.defaultCurrency().getSingular();
    }

    @Override
    public Collection<String> currencies() {
        return List.copyOf(context.currencies().keySet());
    }

    @Override
    public boolean createAccount(UUID accountID, String name) {
        return createAccount(accountID, name, true);
    }

    @Override
    public boolean createAccount(UUID accountID, String name, boolean player) {
        if (!isEnabled() || accountID == null) {
            return false;
        }
        try (AuditScope ignored = context.database().openAuditScope(AUDIT_SOURCE, AUDIT_ACTOR)) {
            return context.economyService().createAccount(accountID, name, context.currencies());
        }
    }

    @Override
    public boolean createAccount(UUID accountID, String name, String worldName) {
        return createAccount(accountID, name, worldName, true);
    }

    @Override
    public boolean createAccount(UUID accountID, String name, String worldName, boolean player) {
        return createAccount(accountID, name, player);
    }

    @Override
    public Map<UUID, String> getUUIDNameMap() {
        return Map.copyOf(context.database().getAccountNames());
    }

    @Override
    public Optional<String> getAccountName(UUID accountID) {
        return context.database().findUsernameByUuid(accountID);
    }

    @Override
    public boolean hasAccount(UUID accountID) {
        return accountID != null && context.currencies().values().stream()
                .anyMatch(currency -> context.database().hasAccount(accountID, currency.getId()));
    }

    @Override
    public boolean hasAccount(UUID accountID, String worldName) {
        return hasAccount(accountID);
    }

    @Override
    public boolean renameAccount(UUID accountID, String name) {
        return renameAccount("unknown", accountID, name);
    }

    @Override
    public boolean renameAccount(String pluginName, UUID accountID, String name) {
        if (!hasAccount(accountID) || name == null || name.isBlank()) {
            return false;
        }
        context.database().updatePlayerName(accountID, name);
        return true;
    }

    @Override
    public boolean deleteAccount(String pluginName, UUID accountID) {
        return false;
    }

    @Override
    public boolean accountSupportsCurrency(String pluginName, UUID accountID, String currency) {
        Currency resolved = resolveCurrency(currency);
        return resolved != null && context.database().hasAccount(accountID, resolved.getId());
    }

    @Override
    public boolean accountSupportsCurrency(String pluginName, UUID accountID, String currency, String world) {
        return accountSupportsCurrency(pluginName, accountID, currency);
    }

    @Override
    public BigDecimal getBalance(String pluginName, UUID accountID) {
        return balanceFor(accountID, context.defaultCurrency());
    }

    @Override
    public BigDecimal getBalance(String pluginName, UUID accountID, String worldName) {
        return getBalance(pluginName, accountID);
    }

    @Override
    public BigDecimal getBalance(String pluginName, UUID accountID, String worldName, String currency) {
        Currency resolved = resolveCurrency(currency);
        return resolved == null ? BigDecimal.ZERO : balanceFor(accountID, resolved);
    }

    @Override
    public boolean has(String pluginName, UUID accountID, BigDecimal amount) {
        return hasAmount(accountID, context.defaultCurrency(), amount);
    }

    @Override
    public boolean has(String pluginName, UUID accountID, String worldName, BigDecimal amount) {
        return has(pluginName, accountID, amount);
    }

    @Override
    public boolean has(String pluginName, UUID accountID, String worldName, String currency, BigDecimal amount) {
        Currency resolved = resolveCurrency(currency);
        return resolved != null && hasAmount(accountID, resolved, amount);
    }

    @Override
    public EconomyResponse set(String pluginName, UUID accountID, BigDecimal amount) {
        return setBalance(accountID, context.defaultCurrency(), amount);
    }

    @Override
    public EconomyResponse set(String pluginName, UUID accountID, String worldName, BigDecimal amount) {
        return set(pluginName, accountID, amount);
    }

    @Override
    public EconomyResponse set(String pluginName, UUID accountID, String worldName, String currency, BigDecimal amount) {
        Currency resolved = resolveCurrency(currency);
        return resolved == null ? failure(accountID, context.defaultCurrency(), "Unknown currency")
                : setBalance(accountID, resolved, amount);
    }

    @Override
    public MultiEconomyResponse transfer(String pluginName, UUID from, UUID to, BigDecimal amount) {
        return transferBalance(from, to, context.defaultCurrency(), amount);
    }

    @Override
    public MultiEconomyResponse transfer(String pluginName, UUID from, UUID to, String worldName, BigDecimal amount) {
        return transfer(pluginName, from, to, amount);
    }

    @Override
    public MultiEconomyResponse transfer(String pluginName, UUID from, UUID to, String worldName,
                                         String currency, BigDecimal amount) {
        Currency resolved = resolveCurrency(currency);
        return resolved == null ? multiFailure(BigDecimal.ZERO, "Unknown currency")
                : transferBalance(from, to, resolved, amount);
    }

    @Override
    public EconomyResponse canWithdraw(String pluginName, UUID accountID, BigDecimal amount) {
        return canWithdrawAmount(accountID, context.defaultCurrency(), amount);
    }

    @Override
    public EconomyResponse canWithdraw(String pluginName, UUID accountID, String worldName, BigDecimal amount) {
        return canWithdraw(pluginName, accountID, amount);
    }

    @Override
    public EconomyResponse canWithdraw(String pluginName, UUID accountID, String worldName,
                                       String currency, BigDecimal amount) {
        Currency resolved = resolveCurrency(currency);
        return resolved == null ? failure(accountID, context.defaultCurrency(), "Unknown currency")
                : canWithdrawAmount(accountID, resolved, amount);
    }

    @Override
    public EconomyResponse withdraw(String pluginName, UUID accountID, BigDecimal amount) {
        return withdrawAmount(accountID, context.defaultCurrency(), amount);
    }

    @Override
    public EconomyResponse withdraw(String pluginName, UUID accountID, String worldName, BigDecimal amount) {
        return withdraw(pluginName, accountID, amount);
    }

    @Override
    public EconomyResponse withdraw(String pluginName, UUID accountID, String worldName,
                                    String currency, BigDecimal amount) {
        Currency resolved = resolveCurrency(currency);
        return resolved == null ? failure(accountID, context.defaultCurrency(), "Unknown currency")
                : withdrawAmount(accountID, resolved, amount);
    }

    @Override
    public EconomyResponse canDeposit(String pluginName, UUID accountID, BigDecimal amount) {
        return canDepositAmount(accountID, context.defaultCurrency(), amount);
    }

    @Override
    public EconomyResponse canDeposit(String pluginName, UUID accountID, String worldName, BigDecimal amount) {
        return canDeposit(pluginName, accountID, amount);
    }

    @Override
    public EconomyResponse canDeposit(String pluginName, UUID accountID, String worldName,
                                      String currency, BigDecimal amount) {
        Currency resolved = resolveCurrency(currency);
        return resolved == null ? failure(accountID, context.defaultCurrency(), "Unknown currency")
                : canDepositAmount(accountID, resolved, amount);
    }

    @Override
    public EconomyResponse deposit(String pluginName, UUID accountID, BigDecimal amount) {
        return depositAmount(accountID, context.defaultCurrency(), amount);
    }

    @Override
    public EconomyResponse deposit(String pluginName, UUID accountID, String worldName, BigDecimal amount) {
        return deposit(pluginName, accountID, amount);
    }

    @Override
    public EconomyResponse deposit(String pluginName, UUID accountID, String worldName,
                                   String currency, BigDecimal amount) {
        Currency resolved = resolveCurrency(currency);
        return resolved == null ? failure(accountID, context.defaultCurrency(), "Unknown currency")
                : depositAmount(accountID, resolved, amount);
    }

    @Override
    public boolean createSharedAccount(String pluginName, UUID accountID, String name, UUID owner) {
        return false;
    }

    @Override
    public boolean isAccountOwner(String pluginName, UUID accountID, UUID uuid) {
        return false;
    }

    @Override
    public boolean setOwner(String pluginName, UUID accountID, UUID uuid) {
        return false;
    }

    @Override
    public boolean isAccountMember(String pluginName, UUID accountID, UUID uuid) {
        return false;
    }

    @Override
    public boolean addAccountMember(String pluginName, UUID accountID, UUID uuid) {
        return false;
    }

    @Override
    public boolean addAccountMember(String pluginName, UUID accountID, UUID uuid,
                                    AccountPermission... initialPermissions) {
        return false;
    }

    @Override
    public boolean removeAccountMember(String pluginName, UUID accountID, UUID uuid) {
        return false;
    }

    @Override
    public boolean hasAccountPermission(String pluginName, UUID accountID, UUID uuid,
                                        AccountPermission permission) {
        return false;
    }

    @Override
    public boolean updateAccountPermission(String pluginName, UUID accountID, UUID uuid,
                                           AccountPermission permission, boolean value) {
        return false;
    }

    private EconomyResponse setBalance(UUID accountID, Currency currency, BigDecimal amount) {
        OptionalDouble converted = exactAmount(amount, currency, false);
        if (converted.isEmpty()) {
            return failure(accountID, currency, "Amount cannot be represented at currency precision");
        }
        boolean success;
        try (AuditScope ignored = context.database().openAuditScope(AUDIT_SOURCE, AUDIT_ACTOR)) {
            success = context.economyService().setBalance(accountID, currency, converted.getAsDouble());
        }
        context.invalidate(accountID, currency.getId());
        return response(success, amount, accountID, currency,
                success ? null : "Account missing, frozen funds exceed target, or database error");
    }

    private EconomyResponse canWithdrawAmount(UUID accountID, Currency currency, BigDecimal amount) {
        OptionalDouble converted = exactAmount(amount, currency, true);
        boolean success = converted.isPresent()
                && context.economyService().canWithdraw(accountID, currency, converted.getAsDouble());
        return response(success, amount, accountID, currency,
                success ? null : "Insufficient available funds or unsafe amount");
    }

    private EconomyResponse withdrawAmount(UUID accountID, Currency currency, BigDecimal amount) {
        OptionalDouble converted = exactAmount(amount, currency, true);
        if (converted.isEmpty()) {
            return failure(accountID, currency, "Amount cannot be represented at currency precision");
        }
        boolean success;
        try (AuditScope ignored = context.database().openAuditScope(AUDIT_SOURCE, AUDIT_ACTOR)) {
            success = context.economyService().withdraw(accountID, currency, converted.getAsDouble());
        }
        context.invalidate(accountID, currency.getId());
        return response(success, amount, accountID, currency,
                success ? null : "Insufficient available funds or database error");
    }

    private EconomyResponse canDepositAmount(UUID accountID, Currency currency, BigDecimal amount) {
        OptionalDouble converted = exactAmount(amount, currency, true);
        boolean success = converted.isPresent()
                && context.economyService().canDeposit(accountID, currency, converted.getAsDouble());
        return response(success, amount, accountID, currency,
                success ? null : "Account missing or amount would exceed safe precision");
    }

    private EconomyResponse depositAmount(UUID accountID, Currency currency, BigDecimal amount) {
        OptionalDouble converted = exactAmount(amount, currency, true);
        if (converted.isEmpty()) {
            return failure(accountID, currency, "Amount cannot be represented at currency precision");
        }
        boolean success;
        try (AuditScope ignored = context.database().openAuditScope(AUDIT_SOURCE, AUDIT_ACTOR)) {
            success = context.economyService().deposit(accountID, currency, converted.getAsDouble());
        }
        context.invalidate(accountID, currency.getId());
        return response(success, amount, accountID, currency,
                success ? null : "Account missing, unsafe resulting balance, or database error");
    }

    private MultiEconomyResponse transferBalance(UUID from, UUID to, Currency currency, BigDecimal amount) {
        OptionalDouble converted = exactAmount(amount, currency, true);
        if (converted.isEmpty() || from == null || to == null || from.equals(to)) {
            return multiFailure(BigDecimal.ZERO, "Invalid or unsafe transfer");
        }
        EconomyService.PayResult result;
        try (AuditScope ignored = context.database().openAuditScope(AUDIT_SOURCE, AUDIT_ACTOR)) {
            result = context.economyService().pay(from, to, currency, converted.getAsDouble());
        }
        context.invalidate(from, currency.getId());
        context.invalidate(to, currency.getId());
        MultiEconomyResponse response = new MultiEconomyResponse(
                result.success() ? scaled(amount, currency) : BigDecimal.ZERO,
                result.success() ? EconomyResponse.ResponseType.SUCCESS : EconomyResponse.ResponseType.FAILURE,
                result.success() ? null : "Transfer failed or was rolled back");
        response.addBalance(from, balanceFor(from, currency));
        response.addBalance(to, balanceFor(to, currency));
        return response;
    }

    private boolean hasAmount(UUID accountID, Currency currency, BigDecimal amount) {
        OptionalDouble converted = exactAmount(amount, currency, false);
        if (converted.isEmpty()) {
            return false;
        }
        EconomyService.BalanceResult balance = context.economyService().getBalance(accountID, currency);
        return balance.exists() && balance.balance() - balance.frozen() >= converted.getAsDouble();
    }

    private OptionalDouble exactAmount(BigDecimal amount, Currency currency, boolean positive) {
        if (amount == null || currency == null || (positive ? amount.signum() <= 0 : amount.signum() < 0)) {
            return OptionalDouble.empty();
        }
        return MoneyAmountPolicy.toExactDouble(amount, currency);
    }

    private BigDecimal balanceFor(UUID accountID, Currency currency) {
        if (accountID == null || currency == null) {
            return BigDecimal.ZERO;
        }
        EconomyService.BalanceResult result = context.economyService().getBalance(accountID, currency);
        return scaled(BigDecimal.valueOf(result.exists() ? result.balance() : 0.0D), currency);
    }

    private BigDecimal scaled(BigDecimal value, Currency currency) {
        return value.setScale(MoneyAmountPolicy.decimalPlaces(currency), RoundingMode.HALF_UP);
    }

    private EconomyResponse response(boolean success, BigDecimal amount, UUID accountID,
                                     Currency currency, String error) {
        return new EconomyResponse(success ? scaled(amount, currency) : BigDecimal.ZERO,
                balanceFor(accountID, currency),
                success ? EconomyResponse.ResponseType.SUCCESS : EconomyResponse.ResponseType.FAILURE,
                error);
    }

    private EconomyResponse failure(UUID accountID, Currency currency, String error) {
        return new EconomyResponse(BigDecimal.ZERO, balanceFor(accountID, currency),
                EconomyResponse.ResponseType.FAILURE, error);
    }

    private MultiEconomyResponse multiFailure(BigDecimal amount, String error) {
        return new MultiEconomyResponse(amount, EconomyResponse.ResponseType.FAILURE, error);
    }

    private Currency resolveCurrency(String currency) {
        return currency == null ? null : context.currency(currency.trim());
    }

    private final class AsyncAdapter implements AsyncEconomy {
        private final VaultAsyncOperationManager asyncOperations;

        private AsyncAdapter(VaultAsyncOperationManager asyncOperations) {
            this.asyncOperations = asyncOperations;
        }

        private <T> CompletableFuture<T> submit(Supplier<T> supplier) {
            return asyncOperations.submit(supplier);
        }

        @Override public CompletableFuture<Boolean> createAccount(UUID id, String name, boolean player) { return submit(() -> MeowEconomyV2.this.createAccount(id, name, player)); }
        @Override public CompletableFuture<Boolean> createAccount(UUID id, String name, String world, boolean player) { return submit(() -> MeowEconomyV2.this.createAccount(id, name, world, player)); }
        @Override public CompletableFuture<Map<UUID, String>> getUUIDNameMap() { return submit(MeowEconomyV2.this::getUUIDNameMap); }
        @Override public CompletableFuture<Optional<String>> getAccountName(UUID id) { return submit(() -> MeowEconomyV2.this.getAccountName(id)); }
        @Override public CompletableFuture<Boolean> hasAccount(UUID id) { return submit(() -> MeowEconomyV2.this.hasAccount(id)); }
        @Override public CompletableFuture<Boolean> hasAccount(UUID id, String world) { return submit(() -> MeowEconomyV2.this.hasAccount(id, world)); }
        @Override public CompletableFuture<Boolean> renameAccount(String plugin, UUID id, String name) { return submit(() -> MeowEconomyV2.this.renameAccount(plugin, id, name)); }
        @Override public CompletableFuture<Boolean> deleteAccount(String plugin, UUID id) { return submit(() -> MeowEconomyV2.this.deleteAccount(plugin, id)); }
        @Override public CompletableFuture<Boolean> accountSupportsCurrency(String plugin, UUID id, String currency) { return submit(() -> MeowEconomyV2.this.accountSupportsCurrency(plugin, id, currency)); }
        @Override public CompletableFuture<Boolean> accountSupportsCurrency(String plugin, UUID id, String currency, String world) { return submit(() -> MeowEconomyV2.this.accountSupportsCurrency(plugin, id, currency, world)); }
        @Override public CompletableFuture<BigDecimal> balance(String plugin, UUID id) { return submit(() -> MeowEconomyV2.this.getBalance(plugin, id)); }
        @Override public CompletableFuture<BigDecimal> balance(String plugin, UUID id, String world) { return submit(() -> MeowEconomyV2.this.getBalance(plugin, id, world)); }
        @Override public CompletableFuture<BigDecimal> balance(String plugin, UUID id, String world, String currency) { return submit(() -> MeowEconomyV2.this.getBalance(plugin, id, world, currency)); }
        @Override public CompletableFuture<Boolean> has(String plugin, UUID id, BigDecimal amount) { return submit(() -> MeowEconomyV2.this.has(plugin, id, amount)); }
        @Override public CompletableFuture<Boolean> has(String plugin, UUID id, String world, BigDecimal amount) { return submit(() -> MeowEconomyV2.this.has(plugin, id, world, amount)); }
        @Override public CompletableFuture<Boolean> has(String plugin, UUID id, String world, String currency, BigDecimal amount) { return submit(() -> MeowEconomyV2.this.has(plugin, id, world, currency, amount)); }
        @Override public CompletableFuture<EconomyResponse> set(String plugin, UUID id, BigDecimal amount) { return submit(() -> MeowEconomyV2.this.set(plugin, id, amount)); }
        @Override public CompletableFuture<EconomyResponse> set(String plugin, UUID id, String world, BigDecimal amount) { return submit(() -> MeowEconomyV2.this.set(plugin, id, world, amount)); }
        @Override public CompletableFuture<EconomyResponse> set(String plugin, UUID id, String world, String currency, BigDecimal amount) { return submit(() -> MeowEconomyV2.this.set(plugin, id, world, currency, amount)); }
        @Override public CompletableFuture<MultiEconomyResponse> transfer(String plugin, UUID from, UUID to, BigDecimal amount) { return submit(() -> MeowEconomyV2.this.transfer(plugin, from, to, amount)); }
        @Override public CompletableFuture<MultiEconomyResponse> transfer(String plugin, UUID from, UUID to, String world, BigDecimal amount) { return submit(() -> MeowEconomyV2.this.transfer(plugin, from, to, world, amount)); }
        @Override public CompletableFuture<MultiEconomyResponse> transfer(String plugin, UUID from, UUID to, String world, String currency, BigDecimal amount) { return submit(() -> MeowEconomyV2.this.transfer(plugin, from, to, world, currency, amount)); }
        @Override public CompletableFuture<EconomyResponse> canWithdraw(String plugin, UUID id, BigDecimal amount) { return submit(() -> MeowEconomyV2.this.canWithdraw(plugin, id, amount)); }
        @Override public CompletableFuture<EconomyResponse> canWithdraw(String plugin, UUID id, String world, BigDecimal amount) { return submit(() -> MeowEconomyV2.this.canWithdraw(plugin, id, world, amount)); }
        @Override public CompletableFuture<EconomyResponse> canWithdraw(String plugin, UUID id, String world, String currency, BigDecimal amount) { return submit(() -> MeowEconomyV2.this.canWithdraw(plugin, id, world, currency, amount)); }
        @Override public CompletableFuture<EconomyResponse> withdraw(String plugin, UUID id, BigDecimal amount) { return submit(() -> MeowEconomyV2.this.withdraw(plugin, id, amount)); }
        @Override public CompletableFuture<EconomyResponse> withdraw(String plugin, UUID id, String world, BigDecimal amount) { return submit(() -> MeowEconomyV2.this.withdraw(plugin, id, world, amount)); }
        @Override public CompletableFuture<EconomyResponse> withdraw(String plugin, UUID id, String world, String currency, BigDecimal amount) { return submit(() -> MeowEconomyV2.this.withdraw(plugin, id, world, currency, amount)); }
        @Override public CompletableFuture<EconomyResponse> canDeposit(String plugin, UUID id, BigDecimal amount) { return submit(() -> MeowEconomyV2.this.canDeposit(plugin, id, amount)); }
        @Override public CompletableFuture<EconomyResponse> canDeposit(String plugin, UUID id, String world, BigDecimal amount) { return submit(() -> MeowEconomyV2.this.canDeposit(plugin, id, world, amount)); }
        @Override public CompletableFuture<EconomyResponse> canDeposit(String plugin, UUID id, String world, String currency, BigDecimal amount) { return submit(() -> MeowEconomyV2.this.canDeposit(plugin, id, world, currency, amount)); }
        @Override public CompletableFuture<EconomyResponse> deposit(String plugin, UUID id, BigDecimal amount) { return submit(() -> MeowEconomyV2.this.deposit(plugin, id, amount)); }
        @Override public CompletableFuture<EconomyResponse> deposit(String plugin, UUID id, String world, BigDecimal amount) { return submit(() -> MeowEconomyV2.this.deposit(plugin, id, world, amount)); }
        @Override public CompletableFuture<EconomyResponse> deposit(String plugin, UUID id, String world, String currency, BigDecimal amount) { return submit(() -> MeowEconomyV2.this.deposit(plugin, id, world, currency, amount)); }
        @Override public CompletableFuture<Boolean> createSharedAccount(String plugin, UUID id, String name, UUID owner) { return submit(() -> false); }
        @Override public CompletableFuture<List<UUID>> accountsWithOwnerOf(String plugin, UUID id) { return submit(List::of); }
        @Override public CompletableFuture<List<UUID>> accountsWithMembershipTo(String plugin, UUID id) { return submit(List::of); }
        @Override public CompletableFuture<List<UUID>> accountsWithAccessTo(String plugin, UUID id, AccountPermission... permissions) { return submit(List::of); }
        @Override public CompletableFuture<Boolean> isAccountOwner(String plugin, UUID id, UUID uuid) { return submit(() -> false); }
        @Override public CompletableFuture<Boolean> setOwner(String plugin, UUID id, UUID uuid) { return submit(() -> false); }
        @Override public CompletableFuture<Boolean> isAccountMember(String plugin, UUID id, UUID uuid) { return submit(() -> false); }
        @Override public CompletableFuture<Boolean> addAccountMember(String plugin, UUID id, UUID uuid) { return submit(() -> false); }
        @Override public CompletableFuture<Boolean> addAccountMember(String plugin, UUID id, UUID uuid, AccountPermission... permissions) { return submit(() -> false); }
        @Override public CompletableFuture<Boolean> removeAccountMember(String plugin, UUID id, UUID uuid) { return submit(() -> false); }
        @Override public CompletableFuture<Boolean> hasAccountPermission(String plugin, UUID id, UUID uuid, AccountPermission permission) { return submit(() -> false); }
        @Override public CompletableFuture<Boolean> updateAccountPermission(String plugin, UUID id, UUID uuid, AccountPermission permission, boolean value) { return submit(() -> false); }
    }

    private record PluginContext(MeowEco plugin) implements Context {
        @Override public boolean isEnabled() { return plugin.isEnabled(); }
        @Override public Map<String, Currency> currencies() { return plugin.getCurrencies(); }
        @Override public Currency currency(String id) { return plugin.getCurrency(id); }
        @Override public Currency defaultCurrency() { return plugin.getDefaultCurrency(); }
        @Override public DatabaseManager database() { return plugin.getDatabaseManager(); }
        @Override public EconomyService economyService() { return plugin.getEconomyService(); }
        @Override public void invalidate(UUID uuid, String currencyId) { plugin.invalidateVaultEconomyCache(uuid, currencyId); }
    }
}
