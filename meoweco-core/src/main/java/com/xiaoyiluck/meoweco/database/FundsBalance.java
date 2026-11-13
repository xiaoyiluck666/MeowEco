package com.xiaoyiluck.meoweco.database;

/** One committed snapshot. Frozen funds are conservatively reserved against earned funds. */
public record FundsBalance(double balance, double frozenBalance, double initialGiftBalance) {
    public double available() { return Math.max(0, balance - frozenBalance); }
    public double earnedAvailable() { return Math.max(0, balance - initialGiftBalance - frozenBalance); }
}
