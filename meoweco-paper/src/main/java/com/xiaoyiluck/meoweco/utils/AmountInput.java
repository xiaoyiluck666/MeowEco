package com.xiaoyiluck.meoweco.utils;

import com.xiaoyiluck.meoweco.MeowEco;
import com.xiaoyiluck.meoweco.objects.Currency;
import com.xiaoyiluck.meoweco.service.MoneyAmountPolicy;
import org.bukkit.command.CommandSender;

import java.util.Objects;

public final class AmountInput {
    private AmountInput() {
    }

    public static boolean fitsCurrencyScaleOrNotify(MeowEco plugin, CommandSender sender, double amount, Currency currency) {
        if (MoneyAmountPolicy.fitsCurrencyScale(amount, currency)) {
            return true;
        }
        sender.sendMessage(plugin.getConfigManager().getComponent("amount-too-precise")
                .replaceText(config -> config
                        .matchLiteral("%decimals%")
                        .replacement(Objects.requireNonNull(String.valueOf(MoneyAmountPolicy.decimalPlaces(currency))))));
        return false;
    }
}
