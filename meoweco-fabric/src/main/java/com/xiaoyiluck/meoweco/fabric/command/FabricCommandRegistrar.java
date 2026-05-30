package com.xiaoyiluck.meoweco.fabric.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.xiaoyiluck.meoweco.database.DatabaseManager;
import com.xiaoyiluck.meoweco.objects.Currency;
import com.xiaoyiluck.meoweco.fabric.FabricEconomyRuntime;
import com.xiaoyiluck.meoweco.service.EconomyService;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.util.Optional;
import java.util.UUID;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class FabricCommandRegistrar {
    private FabricCommandRegistrar() {
    }

    public static void register(FabricEconomyRuntime runtime) {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("meoweco")
                    .then(literal("bal")
                            .executes(ctx -> balanceSelf(ctx.getSource(), runtime))
                            .then(argument("player", StringArgumentType.word())
                                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ctx.getSource().getOnlinePlayerNames(), builder))
                                    .executes(ctx -> balanceOther(ctx.getSource(), StringArgumentType.getString(ctx, "player"), null, runtime))
                                    .then(argument("currency", StringArgumentType.word())
                                            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(runtime.getCurrencies().keySet(), builder))
                                            .executes(ctx -> balanceOther(
                                                    ctx.getSource(),
                                                    StringArgumentType.getString(ctx, "player"),
                                                    StringArgumentType.getString(ctx, "currency"),
                                                    runtime
                                            )))))
                    .then(literal("top")
                            .executes(ctx -> showTop(ctx.getSource(), runtime.getDefaultCurrency().getId(), 1, runtime))
                            .then(argument("currency", StringArgumentType.word())
                                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(runtime.getCurrencies().keySet(), builder))
                                    .executes(ctx -> showTop(ctx.getSource(), StringArgumentType.getString(ctx, "currency"), 1, runtime))
                                    .then(argument("page", IntegerArgumentType.integer(1))
                                            .executes(ctx -> showTop(
                                                    ctx.getSource(),
                                                    StringArgumentType.getString(ctx, "currency"),
                                                    IntegerArgumentType.getInteger(ctx, "page"),
                                                    runtime
                                            )))))
                    .then(literal("pay")
                            .then(argument("player", StringArgumentType.word())
                                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ctx.getSource().getOnlinePlayerNames(), builder))
                                    .then(argument("amount", DoubleArgumentType.doubleArg(0.0000001))
                                            .executes(ctx -> pay(
                                                    ctx.getSource(),
                                                    StringArgumentType.getString(ctx, "player"),
                                                    DoubleArgumentType.getDouble(ctx, "amount"),
                                                    runtime.getDefaultCurrency().getId(),
                                                    runtime
                                            ))
                                            .then(argument("currency", StringArgumentType.word())
                                                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(runtime.getCurrencies().keySet(), builder))
                                                    .executes(ctx -> pay(
                                                            ctx.getSource(),
                                                            StringArgumentType.getString(ctx, "player"),
                                                            DoubleArgumentType.getDouble(ctx, "amount"),
                                                            StringArgumentType.getString(ctx, "currency"),
                                                            runtime
                                                    ))))))
                    .then(literal("exchange")
                            .then(argument("amount", DoubleArgumentType.doubleArg(0.0000001))
                                    .then(argument("from", StringArgumentType.word())
                                            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(runtime.getCurrencies().keySet(), builder))
                                            .then(argument("to", StringArgumentType.word())
                                                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(runtime.getCurrencies().keySet(), builder))
                                                    .executes(ctx -> exchange(
                                                            ctx.getSource(),
                                                            DoubleArgumentType.getDouble(ctx, "amount"),
                                                            StringArgumentType.getString(ctx, "from"),
                                                            StringArgumentType.getString(ctx, "to"),
                                                            runtime
                                                    ))))))
                    .then(literal("give")
                            .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                            .then(adminMoneySubNode("give", runtime)))
                    .then(literal("take")
                            .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                            .then(adminMoneySubNode("take", runtime)))
                    .then(literal("set")
                            .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                            .then(adminMoneySubNode("set", runtime)))
                    .then(literal("freeze")
                            .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                            .then(adminMoneySubNode("freeze", runtime)))
                    .then(literal("unfreeze")
                            .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                            .then(adminMoneySubNode("unfreeze", runtime)))
                    .then(literal("deductfrozen")
                            .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                            .then(adminMoneySubNode("deductfrozen", runtime)))
                    .then(literal("setrate")
                            .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                            .then(argument("from", StringArgumentType.word())
                                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(runtime.getCurrencies().keySet(), builder))
                                    .then(argument("to", StringArgumentType.word())
                                            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(runtime.getCurrencies().keySet(), builder))
                                            .then(argument("rate", DoubleArgumentType.doubleArg(0.0000001))
                                                    .executes(ctx -> setRate(
                                                            ctx.getSource(),
                                                            StringArgumentType.getString(ctx, "from"),
                                                            StringArgumentType.getString(ctx, "to"),
                                                            DoubleArgumentType.getDouble(ctx, "rate"),
                                                            runtime
                                                    ))))))
                    .then(literal("hide")
                            .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                            .then(argument("player", StringArgumentType.word())
                                    .executes(ctx -> setHidden(ctx.getSource(), StringArgumentType.getString(ctx, "player"), true, runtime))))
                    .then(literal("unhide")
                            .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                            .then(argument("player", StringArgumentType.word())
                                    .executes(ctx -> setHidden(ctx.getSource(), StringArgumentType.getString(ctx, "player"), false, runtime))))
            );

            dispatcher.register(literal("money")
                    .executes(ctx -> balanceSelf(ctx.getSource(), runtime))
                    .then(argument("player", StringArgumentType.word())
                            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ctx.getSource().getOnlinePlayerNames(), builder))
                            .executes(ctx -> balanceOther(ctx.getSource(), StringArgumentType.getString(ctx, "player"), null, runtime))
                            .then(argument("currency", StringArgumentType.word())
                                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(runtime.getCurrencies().keySet(), builder))
                                    .executes(ctx -> balanceOther(
                                            ctx.getSource(),
                                            StringArgumentType.getString(ctx, "player"),
                                            StringArgumentType.getString(ctx, "currency"),
                                            runtime
                                    ))))
                    .then(literal("exchange")
                            .then(argument("amount", DoubleArgumentType.doubleArg(0.0000001))
                                    .then(argument("from", StringArgumentType.word())
                                            .then(argument("to", StringArgumentType.word())
                                                    .executes(ctx -> exchange(
                                                            ctx.getSource(),
                                                            DoubleArgumentType.getDouble(ctx, "amount"),
                                                            StringArgumentType.getString(ctx, "from"),
                                                            StringArgumentType.getString(ctx, "to"),
                                                            runtime
                                                    ))))))
            );

            dispatcher.register(literal("pay")
                    .then(argument("player", StringArgumentType.word())
                            .then(argument("amount", DoubleArgumentType.doubleArg(0.0000001))
                                    .executes(ctx -> pay(
                                            ctx.getSource(),
                                            StringArgumentType.getString(ctx, "player"),
                                            DoubleArgumentType.getDouble(ctx, "amount"),
                                            runtime.getDefaultCurrency().getId(),
                                            runtime
                                    ))
                                    .then(argument("currency", StringArgumentType.word())
                                            .executes(ctx -> pay(
                                                    ctx.getSource(),
                                                    StringArgumentType.getString(ctx, "player"),
                                                    DoubleArgumentType.getDouble(ctx, "amount"),
                                                    StringArgumentType.getString(ctx, "currency"),
                                                    runtime
                                            ))))));

            dispatcher.register(literal("eco")
                    .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                    .then(literal("give").then(adminMoneySubNode("give", runtime)))
                    .then(literal("take").then(adminMoneySubNode("take", runtime)))
                    .then(literal("set").then(adminMoneySubNode("set", runtime)))
                    .then(literal("freeze").then(adminMoneySubNode("freeze", runtime)))
                    .then(literal("unfreeze").then(adminMoneySubNode("unfreeze", runtime)))
                    .then(literal("deductfrozen").then(adminMoneySubNode("deductfrozen", runtime)))
                    .then(literal("hide").then(argument("player", StringArgumentType.word()).executes(ctx -> setHidden(ctx.getSource(), StringArgumentType.getString(ctx, "player"), true, runtime))))
                    .then(literal("unhide").then(argument("player", StringArgumentType.word()).executes(ctx -> setHidden(ctx.getSource(), StringArgumentType.getString(ctx, "player"), false, runtime))))
                    .then(literal("setrate")
                            .then(argument("from", StringArgumentType.word())
                                    .then(argument("to", StringArgumentType.word())
                                            .then(argument("rate", DoubleArgumentType.doubleArg(0.0000001))
                                                    .executes(ctx -> setRate(
                                                            ctx.getSource(),
                                                            StringArgumentType.getString(ctx, "from"),
                                                            StringArgumentType.getString(ctx, "to"),
                                                            DoubleArgumentType.getDouble(ctx, "rate"),
                                                            runtime
                                                    ))))))
                    .then(literal("top")
                            .executes(ctx -> showTop(ctx.getSource(), runtime.getDefaultCurrency().getId(), 1, runtime))
                            .then(argument("currency", StringArgumentType.word()).executes(ctx -> showTop(ctx.getSource(), StringArgumentType.getString(ctx, "currency"), 1, runtime)))
                            .then(argument("currency", StringArgumentType.word())
                                    .then(argument("page", IntegerArgumentType.integer(1))
                                            .executes(ctx -> showTop(
                                                    ctx.getSource(),
                                                    StringArgumentType.getString(ctx, "currency"),
                                                    IntegerArgumentType.getInteger(ctx, "page"),
                                                    runtime
                                            ))))));

            dispatcher.register(literal("baltop")
                    .executes(ctx -> showTop(ctx.getSource(), runtime.getDefaultCurrency().getId(), 1, runtime))
                    .then(argument("currency", StringArgumentType.word())
                            .executes(ctx -> showTop(ctx.getSource(), StringArgumentType.getString(ctx, "currency"), 1, runtime))
                            .then(argument("page", IntegerArgumentType.integer(1))
                                    .executes(ctx -> showTop(
                                            ctx.getSource(),
                                            StringArgumentType.getString(ctx, "currency"),
                                            IntegerArgumentType.getInteger(ctx, "page"),
                                            runtime
                                    )))));
        });
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> adminMoneySubNode(String op, FabricEconomyRuntime runtime) {
        return argument("player", StringArgumentType.word())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ctx.getSource().getOnlinePlayerNames(), builder))
                .then(argument("amount", DoubleArgumentType.doubleArg(0.0))
                        .executes(ctx -> adminMoneyOperation(
                                ctx.getSource(),
                                op,
                                StringArgumentType.getString(ctx, "player"),
                                DoubleArgumentType.getDouble(ctx, "amount"),
                                runtime.getDefaultCurrency().getId(),
                                runtime
                        ))
                        .then(argument("currency", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(runtime.getCurrencies().keySet(), builder))
                                .executes(ctx -> adminMoneyOperation(
                                        ctx.getSource(),
                                        op,
                                        StringArgumentType.getString(ctx, "player"),
                                        DoubleArgumentType.getDouble(ctx, "amount"),
                                        StringArgumentType.getString(ctx, "currency"),
                                        runtime
                                ))));
    }

    private static int balanceSelf(CommandSourceStack source, FabricEconomyRuntime runtime) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Only players can use this command without target."));
            return 0;
        }
        Currency currency = runtime.getDefaultCurrency();
        runtime.ensurePlayerAccounts(player.getUUID(), player.getName().getString());

        EconomyService economyService = new EconomyService(runtime.getDatabaseManager());
        EconomyService.BalanceResult result = economyService.getBalance(player.getUUID(), currency);
        if (!result.exists()) {
            source.sendFailure(Component.literal("Account not found."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Balance: " + runtime.formatFixed(result.balance(), currency) + " " + currency.getPlural()), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int balanceOther(CommandSourceStack source, String playerName, String currencyId, FabricEconomyRuntime runtime) {
        if (!Commands.LEVEL_GAMEMASTERS.check(source.permissions()) && source.getPlayer() != null && !source.getPlayer().getName().getString().equalsIgnoreCase(playerName)) {
            source.sendFailure(Component.literal("No permission to query others."));
            return 0;
        }

        Optional<Target> targetOpt = resolveTarget(source.getServer(), runtime.getDatabaseManager(), playerName);
        if (targetOpt.isEmpty()) {
            source.sendFailure(Component.literal("Player not found: " + playerName));
            return 0;
        }

        Target target = targetOpt.get();
        Currency currency = runtime.getCurrency(currencyId);
        if (currency == null) {
            source.sendFailure(Component.literal("Invalid currency: " + currencyId));
            return 0;
        }

        runtime.ensurePlayerAccounts(target.uuid(), target.displayName());
        EconomyService economyService = new EconomyService(runtime.getDatabaseManager());
        EconomyService.BalanceResult result = economyService.getBalance(target.uuid(), currency);
        if (!result.exists()) {
            source.sendFailure(Component.literal("Account not found for " + target.displayName()));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(target.displayName() + " balance: " + runtime.formatFixed(result.balance(), currency) + " " + currency.getPlural()), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int pay(CommandSourceStack source, String targetName, double amount, String currencyId, FabricEconomyRuntime runtime) {
        ServerPlayer sender = source.getPlayer();
        if (sender == null) {
            source.sendFailure(Component.literal("Only players can use /pay."));
            return 0;
        }

        Optional<Target> targetOpt = resolveTarget(source.getServer(), runtime.getDatabaseManager(), targetName);
        if (targetOpt.isEmpty()) {
            source.sendFailure(Component.literal("Player not found: " + targetName));
            return 0;
        }

        Target target = targetOpt.get();
        if (sender.getUUID().equals(target.uuid())) {
            source.sendFailure(Component.literal("You cannot pay yourself."));
            return 0;
        }

        Currency currency = runtime.getCurrency(currencyId);
        if (currency == null) {
            source.sendFailure(Component.literal("Invalid currency: " + currencyId));
            return 0;
        }

        runtime.ensurePlayerAccounts(sender.getUUID(), sender.getName().getString());
        runtime.ensurePlayerAccounts(target.uuid(), target.displayName());

        EconomyService economyService = new EconomyService(runtime.getDatabaseManager());
        EconomyService.PayResult payResult = economyService.pay(sender.getUUID(), target.uuid(), currency, amount);
        if (!payResult.success()) {
            source.sendFailure(Component.literal("Payment failed: insufficient funds or DB error."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Paid " + target.displayName() + " " + runtime.formatShort(amount, currency) + " " + currency.getPlural()), false);
        if (target.online() != null) {
            target.online().sendSystemMessage(Component.literal("Received " + runtime.formatShort(payResult.depositAmount(), currency) + " " + currency.getPlural() + " from " + sender.getName().getString()));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int exchange(CommandSourceStack source, double amount, String fromCurrencyId, String toCurrencyId, FabricEconomyRuntime runtime) {
        if (!runtime.isExchangeEnabled()) {
            source.sendFailure(Component.literal("Exchange is disabled."));
            return 0;
        }

        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Only players can exchange currency."));
            return 0;
        }

        Currency fromCurrency = runtime.getCurrency(fromCurrencyId);
        Currency toCurrency = runtime.getCurrency(toCurrencyId);
        if (fromCurrency == null || toCurrency == null) {
            source.sendFailure(Component.literal("Invalid currency."));
            return 0;
        }

        double rate = runtime.getExchangeRate(fromCurrency.getId(), toCurrency.getId());
        if (rate <= 0.0) {
            source.sendFailure(Component.literal("Exchange rate not configured for " + fromCurrency.getId() + " -> " + toCurrency.getId()));
            return 0;
        }

        runtime.ensurePlayerAccounts(player.getUUID(), player.getName().getString());

        EconomyService economyService = new EconomyService(runtime.getDatabaseManager());
        EconomyService.ExchangeResult exchangeResult = economyService.exchange(player.getUUID(), fromCurrency, toCurrency, amount, rate);
        if (!exchangeResult.success()) {
            source.sendFailure(Component.literal("Exchange failed: insufficient funds or DB error."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Exchanged " + runtime.formatShort(amount, fromCurrency) + " " + fromCurrency.getPlural() + " -> " + runtime.formatShort(exchangeResult.toAmount(), toCurrency) + " " + toCurrency.getPlural()), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int adminMoneyOperation(CommandSourceStack source, String op, String targetName, double amount, String currencyId, FabricEconomyRuntime runtime) {
        Optional<Target> targetOpt = resolveTarget(source.getServer(), runtime.getDatabaseManager(), targetName);
        if (targetOpt.isEmpty()) {
            source.sendFailure(Component.literal("Player not found: " + targetName));
            return 0;
        }

        Target target = targetOpt.get();
        Currency currency = runtime.getCurrency(currencyId);
        if (currency == null) {
            source.sendFailure(Component.literal("Invalid currency: " + currencyId));
            return 0;
        }

        if (("give".equals(op) || "take".equals(op) || "freeze".equals(op) || "unfreeze".equals(op) || "deductfrozen".equals(op)) && amount <= 0) {
            source.sendFailure(Component.literal("Amount must be greater than 0."));
            return 0;
        }
        if ("set".equals(op) && amount < 0) {
            source.sendFailure(Component.literal("Amount must be >= 0 for set."));
            return 0;
        }

        runtime.ensurePlayerAccounts(target.uuid(), target.displayName());
        EconomyService economyService = new EconomyService(runtime.getDatabaseManager());
        boolean success = economyService.applyAdminOperation(op, target.uuid(), currency, amount);

        if (!success) {
            source.sendFailure(Component.literal("Operation failed."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Success: " + op + " " + target.displayName() + " " + runtime.formatShort(amount, currency) + " " + currency.getPlural()), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int setRate(CommandSourceStack source, String fromCurrency, String toCurrency, double rate, FabricEconomyRuntime runtime) {
        Currency from = runtime.getCurrency(fromCurrency);
        Currency to = runtime.getCurrency(toCurrency);
        if (from == null || to == null) {
            source.sendFailure(Component.literal("Invalid currency id."));
            return 0;
        }

        if (!runtime.setExchangeRate(from.getId(), to.getId(), rate)) {
            source.sendFailure(Component.literal("Invalid rate."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Exchange rate updated: " + from.getId() + " -> " + to.getId() + " = " + rate), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int setHidden(CommandSourceStack source, String playerName, boolean hidden, FabricEconomyRuntime runtime) {
        Optional<Target> targetOpt = resolveTarget(source.getServer(), runtime.getDatabaseManager(), playerName);
        if (targetOpt.isEmpty()) {
            source.sendFailure(Component.literal("Player not found: " + playerName));
            return 0;
        }

        Target target = targetOpt.get();
        runtime.ensurePlayerAccounts(target.uuid(), target.displayName());
        boolean updated = runtime.getDatabaseManager().updateHidden(target.uuid(), target.displayName(), hidden);
        if (!updated) {
            source.sendFailure(Component.literal("Failed to update hidden status."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal((hidden ? "Hidden " : "Unhidden ") + target.displayName() + " from baltop."), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int showTop(CommandSourceStack source, String currencyId, int page, FabricEconomyRuntime runtime) {
        Currency currency = runtime.getCurrency(currencyId);
        if (currency == null) {
            source.sendFailure(Component.literal("Invalid currency: " + currencyId));
            return 0;
        }

        EconomyService economyService = new EconomyService(runtime.getDatabaseManager());
        EconomyService.TopResult topResult = economyService.getTopPage(currency, page, 10);
        if (topResult.entries().isEmpty()) {
            source.sendFailure(Component.literal("No data for currency " + currency.getId()));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Top " + currency.getDisplayName() + " - Page " + topResult.page()), false);
        double totalBalance = economyService.getTotalBalance(currency);
        source.sendSuccess(() -> Component.literal("Total: " + runtime.formatFixed(totalBalance, currency) + " " + currency.getPlural()), false);
        for (EconomyService.TopEntry entry : topResult.entries()) {
            String line = "#" + entry.rank() + " " + entry.playerName() + " - " + runtime.formatFixed(entry.balance(), currency) + " " + currency.getPlural();
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static Optional<Target> resolveTarget(MinecraftServer server, DatabaseManager databaseManager, String input) {
        ServerPlayer online = server.getPlayerList().getPlayerByName(input);
        if (online != null) {
            return Optional.of(new Target(online.getUUID(), online.getName().getString(), online));
        }

        Optional<UUID> uuidOptional = databaseManager.findUuidByUsername(input);
        if (uuidOptional.isPresent()) {
            UUID uuid = uuidOptional.get();            return Optional.of(new Target(uuid, input, null));
        }

        return Optional.empty();
    }

    private record Target(UUID uuid, String displayName, ServerPlayer online) {
    }
}






