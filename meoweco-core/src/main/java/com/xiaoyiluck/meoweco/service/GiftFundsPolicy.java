package com.xiaoyiluck.meoweco.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/** Immutable runtime policy. Gift tags survive exchanges and permitted transfers. */
public record GiftFundsPolicy(boolean allowPlayerTransfer, SpendingOrder spendingOrder) {
    public static final GiftFundsPolicy DEFAULT = new GiftFundsPolicy(false, SpendingOrder.GIFT_FIRST);
    public enum SpendingOrder { GIFT_FIRST, EARNED_FIRST }

    public GiftFundsPolicy {
        java.util.Objects.requireNonNull(spendingOrder, "spendingOrder");
    }

    public static GiftFundsPolicy parse(boolean allow, String order) {
        return new GiftFundsPolicy(allow, SpendingOrder.valueOf(order.trim().toUpperCase(Locale.ROOT)));
    }

    public double spentGift(double total, double gift, double debit) {
        double earned = Math.max(0, total - gift);
        return spendingOrder == SpendingOrder.GIFT_FIRST
                ? Math.min(gift, debit) : Math.max(0, debit - earned);
    }

    public static double convertedGift(double giftedDebit, double debit, double credit) {
        if (giftedDebit <= 0 || credit <= 0) return 0;
        // Round the label upward so repeated currency conversion cannot shed gift restrictions.
        return Math.min(credit, BigDecimal.valueOf(credit).multiply(BigDecimal.valueOf(giftedDebit))
                .divide(BigDecimal.valueOf(debit), 12, RoundingMode.CEILING).doubleValue());
    }
}
