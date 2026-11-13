package com.xiaoyiluck.meoweco.initial;

import com.xiaoyiluck.meoweco.objects.Currency;
import com.xiaoyiluck.meoweco.service.MoneyAmountPolicy;

/** Immutable creation-time amounts; existing wallets never acquire this policy retroactively. */
public record InitialBalancePolicy(double configuredBalance, double offlineBalance, double premiumBalance) {
    public static InitialBalancePolicy forCurrency(Currency currency, double offline, double premium) {
        if (!MoneyAmountPolicy.isValidNonNegativeInput(offline, currency)
                || !MoneyAmountPolicy.isValidNonNegativeInput(premium, currency)
                || premium < offline) {
            throw new IllegalArgumentException("Initial amounts must fit currency precision and premium >= offline >= 0");
        }
        return new InitialBalancePolicy(currency.getInitialBalance(), offline, premium);
    }

    public boolean appliesTo(double requestedBalance) {
        // Explicit administrative/tax accounts created with another amount keep their requested balance.
        return Double.compare(configuredBalance, requestedBalance) == 0;
    }

    /** null means unsigned/unresolved, including a missing PlaceholderAPI expansion. */
    public static Boolean verifiedPremium(String verified, String premium) {
        if (!"true".equals(verified)) return null;
        if ("true".equals(premium)) return Boolean.TRUE;
        if ("false".equals(premium)) return Boolean.FALSE;
        return null;
    }
}
