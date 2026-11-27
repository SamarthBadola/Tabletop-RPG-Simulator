package com.github.sbadolankaur.rpg.engine.equipment;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Whole coins per denomination (RS-EQP-037, RS-EQP-038, E-107). Coins are never converted unless a
 * payment needs change. The ledger is immutable: a payment returns the new ledger and a refused
 * payment throws and changes nothing.
 */
public final class CurrencyLedger {
  public static final CurrencyLedger EMPTY = new CurrencyLedger(new EnumMap<>(Denomination.class));

  private static final Denomination[] SPEND_ORDER = {
    Denomination.PP, Denomination.GP, Denomination.EP, Denomination.SP, Denomination.CP
  };

  private final EnumMap<Denomination, Integer> coins;

  private CurrencyLedger(EnumMap<Denomination, Integer> coins) {
    this.coins = coins;
  }

  /** What a payment did: the new ledger, the coins handed over and the change given back. */
  public record Payment(
      CurrencyLedger ledger, Map<Denomination, Integer> spent, Map<Denomination, Integer> change) {}

  public static CurrencyLedger of(Map<Denomination, Integer> given) {
    var coins = new EnumMap<Denomination, Integer>(Denomination.class);
    for (var entry : given.entrySet()) {
      if (entry.getValue() < 0) throw new IllegalArgumentException("Coins cannot be negative");
      if (entry.getValue() > 0) coins.put(entry.getKey(), entry.getValue());
    }
    return new CurrencyLedger(coins);
  }

  public int count(Denomination denomination) {
    return coins.getOrDefault(denomination, 0);
  }

  public long totalCopper() {
    long total = 0;
    for (var entry : coins.entrySet()) total += (long) entry.getKey().copper() * entry.getValue();
    return total;
  }

  public long totalCoins() {
    long total = 0;
    for (int count : coins.values()) total += count;
    return total;
  }

  public CurrencyLedger receive(Map<Denomination, Integer> received) {
    var next = new EnumMap<Denomination, Integer>(coins);
    for (var entry : received.entrySet()) {
      if (entry.getValue() < 0) throw new IllegalArgumentException("Coins cannot be negative");
      next.merge(entry.getKey(), entry.getValue(), Integer::sum);
    }
    return of(next);
  }

  /**
   * Pays a price in copper. Coins go from the largest denomination down and never overshoot the
   * price. If that cannot cover it, the smallest coin that covers the rest is broken and the change
   * comes back largest first. Electrum is skipped, for payment and for change, unless chosen.
   */
  public Payment pay(long costInCopper, boolean useElectrum) {
    if (costInCopper < 0) throw new IllegalArgumentException("A price cannot be negative");
    var left = new EnumMap<Denomination, Integer>(coins);
    var spent = new EnumMap<Denomination, Integer>(Denomination.class);
    long remaining = costInCopper;
    for (var denomination : SPEND_ORDER) {
      if (denomination == Denomination.EP && !useElectrum) continue;
      int take =
          (int) Math.min(left.getOrDefault(denomination, 0), remaining / denomination.copper());
      if (take > 0) {
        spent.merge(denomination, take, Integer::sum);
        left.merge(denomination, -take, Integer::sum);
        remaining -= (long) take * denomination.copper();
      }
    }
    var change = new EnumMap<Denomination, Integer>(Denomination.class);
    if (remaining > 0) {
      long need = remaining;
      var breaking =
          java.util.Arrays.stream(Denomination.values())
              .filter(d -> useElectrum || d != Denomination.EP)
              .filter(d -> left.getOrDefault(d, 0) > 0 && d.copper() >= need)
              .min(Comparator.comparingInt(Denomination::copper));
      if (breaking.isEmpty()) {
        throw new InsufficientFundsException("Not enough coins to pay " + costInCopper + " copper");
      }
      var coin = breaking.get();
      spent.merge(coin, 1, Integer::sum);
      left.merge(coin, -1, Integer::sum);
      long owed = coin.copper() - remaining;
      for (var denomination : SPEND_ORDER) {
        if (denomination == Denomination.EP) continue;
        int give = (int) (owed / denomination.copper());
        if (give > 0) {
          change.put(denomination, give);
          left.merge(denomination, give, Integer::sum);
          owed -= (long) give * denomination.copper();
        }
      }
    }
    return new Payment(of(left), Map.copyOf(spent), Map.copyOf(change));
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof CurrencyLedger ledger && coins.equals(ledger.coins);
  }

  @Override
  public int hashCode() {
    return Objects.hash(coins);
  }

  @Override
  public String toString() {
    return coins.toString();
  }
}
