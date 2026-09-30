package com.xiaoyiluck.meoweco.database;

public record AccountBalance(double balance, double frozenBalance) {
    public double availableBalance() {
        return balance - frozenBalance;
    }
}
