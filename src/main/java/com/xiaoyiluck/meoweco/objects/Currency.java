package com.xiaoyiluck.meoweco.objects;

public class Currency {
    private final String id;
    private final String displayName;
    private final String singular;
    private final String plural;
    private final double initialBalance;
    private final int decimalPlaces;
    private final double transferTax;

    public Currency(String id, String displayName, String singular, String plural, double initialBalance, int decimalPlaces, double transferTax) {
        this.id = id;
        this.displayName = displayName;
        this.singular = singular;
        this.plural = plural;
        this.initialBalance = initialBalance;
        this.decimalPlaces = decimalPlaces;
        this.transferTax = transferTax;
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
}
