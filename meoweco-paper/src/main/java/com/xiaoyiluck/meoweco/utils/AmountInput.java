package com.xiaoyiluck.meoweco.utils;

import com.xiaoyiluck.meoweco.MeowEco;
import com.xiaoyiluck.meoweco.objects.Currency;
import com.xiaoyiluck.meoweco.service.MoneyAmountPolicy;
import net.kyori.adventure.text.TextReplacementConfig;
import org.bukkit.command.CommandSender;

public final class AmountInput {
    private AmountInput() {
    }

    public static boolean fitsCurrencyScaleOrNotify(MeowEco plugin, CommandSender sender, double amount, Currency currency) {
        if (MoneyAmountPolicy.fitsCurrencyScale(amount, currency)) {
            return true;
        }
        sender.sendMessage(plugin.getConfigManager().getComponent("amount-too-precise")
                .replaceText(TextReplacementConfig.builder()
                        .matchLiteral("%decimals%")
                        .replacement(String.valueOf(MoneyAmountPolicy.decimalPlaces(currency)))
                        .build()));
        return false;
    }
}
