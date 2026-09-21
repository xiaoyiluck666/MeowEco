package com.xiaoyiluck.meoweco.service;

import com.xiaoyiluck.meoweco.objects.Currency;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyAmountPolicy {
    /** Largest balance for which every whole-unit increment remains exact in a double. */
    public static final double MAX_SAFE_BALANCE = 0x1.fffffffffffffp52;

    private MoneyAmountPolicy() {
    }

    public static boolean isPositiveFinite(double amount) {
        return Double.isFinite(amount) && amount > 0.0D && amount <= MAX_SAFE_BALANCE;
    }

    public static boolean isNonNegativeFinite(double amount) {
        return Double.isFinite(amount) && amount >= 0.0D && amount <= MAX_SAFE_BALANCE;
    }

    public static boolean fitsCurrencyScale(double amount, Currency currency) {
        if (!Double.isFinite(amount)) {
            return false;
        }
        BigDecimal value = BigDecimal.valueOf(amount).stripTrailingZeros();
        return value.scale() <= decimalPlaces(currency);
    }

    public static boolean isValidPositiveInput(double amount, Currency currency) {
        return isPositiveFinite(amount) && fitsCurrencyScale(amount, currency);
    }

    public static boolean isValidNonNegativeInput(double amount, Currency currency) {
        return isNonNegativeFinite(amount) && fitsCurrencyScale(amount, currency);
    }

    public static double roundForStorage(double amount, Currency currency) {
        if (!Double.isFinite(amount)) {
            return amount;
        }
        return BigDecimal.valueOf(amount)
                .setScale(decimalPlaces(currency), RoundingMode.HALF_UP)
                .doubleValue();
    }

    public static int decimalPlaces(Currency currency) {
        if (currency == null) {
            return 2;
        }
        return Math.max(0, currency.getDecimalPlaces());
    }
}
