package com.xiaoyiluck.meoweco.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Shared progressive tax calculation for balance and transaction taxes. */
public final class TaxPolicy {
    private TaxPolicy() {
    }

    public record Tier(double threshold, double rate) {
        public Tier {
            if (!Double.isFinite(threshold) || threshold < 0.0D) {
                threshold = 0.0D;
            }
            if (!Double.isFinite(rate) || rate < 0.0D) {
                rate = 0.0D;
            }
            rate = Math.min(rate, 1.0D);
        }
    }

    public static List<Tier> normalize(List<Tier> tiers, double fallbackThreshold, double fallbackRate) {
        List<Tier> normalized = new ArrayList<>();
        if (tiers != null) {
            normalized.addAll(tiers);
        }
        if (normalized.isEmpty()) {
            normalized.add(new Tier(fallbackThreshold, fallbackRate));
        }
        normalized.sort(Comparator.comparingDouble(Tier::threshold));
        List<Tier> deduplicated = new ArrayList<>();
        for (Tier tier : normalized) {
            if (!deduplicated.isEmpty()
                    && Double.compare(deduplicated.get(deduplicated.size() - 1).threshold(), tier.threshold()) == 0) {
                deduplicated.set(deduplicated.size() - 1, tier);
            } else {
                deduplicated.add(tier);
            }
        }
        return List.copyOf(deduplicated);
    }

    /** Calculates marginal tax: each tier rate applies only between its threshold and the next tier. */
    public static double calculate(double amount, List<Tier> tiers) {
        if (!Double.isFinite(amount) || amount <= 0.0D || tiers == null || tiers.isEmpty()) {
            return 0.0D;
        }
        List<Tier> normalized = normalize(tiers, 0.0D, 0.0D);
        double tax = 0.0D;
        for (int i = 0; i < normalized.size(); i++) {
            Tier tier = normalized.get(i);
            double upper = i + 1 < normalized.size() ? normalized.get(i + 1).threshold() : amount;
            double taxable = Math.min(amount, upper) - tier.threshold();
            if (taxable > 0.0D) {
                tax += taxable * tier.rate();
            }
            if (amount <= upper) {
                break;
            }
        }
        return Math.max(0.0D, tax);
    }
}
