package com.xiaoyiluck.meoweco.objects;

import com.xiaoyiluck.meoweco.service.TaxPolicy;

import java.util.List;

public class Currency {
    private final String id;
    private final String displayName;
    private final String singular;
    private final String plural;
    private final double initialBalance;
    private final int decimalPlaces;
    private final double transferTax;
    private final List<TaxPolicy.Tier> transferTaxTiers;

    public Currency(String id, String displayName, String singular, String plural, double initialBalance, int decimalPlaces, double transferTax) {
        this(id, displayName, singular, plural, initialBalance, decimalPlaces, transferTax, List.of());
    }

    public Currency(String id, String displayName, String singular, String plural, double initialBalance, int decimalPlaces,
                    double transferTax, List<TaxPolicy.Tier> transferTaxTiers) {
        this.id = id;
        this.displayName = displayName;
        this.singular = singular;
        this.plural = plural;
        this.initialBalance = initialBalance;
        this.decimalPlaces = decimalPlaces;
        this.transferTax = transferTax;
        this.transferTaxTiers = TaxPolicy.normalize(transferTaxTiers, 0.0D, transferTax);
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getSingular() {
        return singular;
    }

    public String getPlural() {
        return plural;
    }

    public double getInitialBalance() {
        return initialBalance;
    }

    public int getDecimalPlaces() {
        return decimalPlaces;
    }

    public double getTransferTax() {
        return transferTax;
    }

    public List<TaxPolicy.Tier> getTransferTaxTiers() {
        return transferTaxTiers;
    }
}
