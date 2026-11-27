package com.github.sbadolankaur.rpg.engine.equipment;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.sbadolankaur.rpg.engine.equipment.CurrencyLedger.Payment;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CurrencyLedgerTest {
  private static InsufficientFundsException refused(Runnable action) {
    try {
      action.run();
    } catch (InsufficientFundsException e) {
      return e;
    }
    throw new AssertionError("The payment was not refused");
  }

  @Test
  void RS_EQP_037_theDenominationsConvertToCopper() {
    assertThat(Denomination.CP.copper()).isEqualTo(1);
    assertThat(Denomination.SP.copper()).isEqualTo(10);
    assertThat(Denomination.EP.copper()).isEqualTo(50);
    assertThat(Denomination.GP.copper()).isEqualTo(100);
    assertThat(Denomination.PP.copper()).isEqualTo(1000);
    var ledger =
        CurrencyLedger.of(Map.of(Denomination.PP, 1, Denomination.GP, 3, Denomination.SP, 4));
    assertThat(ledger.totalCopper()).isEqualTo(1340);
  }

  @Test
  void WE_F5_payingBreaksTheSmallestCoinThatCoversTheRestAndReturnsChangeLargestFirst() {
    var ledger =
        CurrencyLedger.of(Map.of(Denomination.PP, 1, Denomination.GP, 3, Denomination.SP, 4));
    Payment payment = ledger.pay(750, false);
    assertThat(payment.ledger().count(Denomination.GP)).isEqualTo(5);
    assertThat(payment.ledger().count(Denomination.SP)).isEqualTo(9);
    assertThat(payment.ledger().count(Denomination.PP)).isZero();
    assertThat(payment.ledger().totalCopper()).isEqualTo(590);
    assertThat(payment.change()).isEqualTo(Map.of(Denomination.GP, 5, Denomination.SP, 9));
  }

  @Test
  void anExactPaymentNeedsNoChangeAndNeverOvershoots() {
    var ledger = CurrencyLedger.of(Map.of(Denomination.GP, 10, Denomination.SP, 5));
    var payment = ledger.pay(550, false);
    assertThat(payment.change()).isEqualTo(Map.of());
    assertThat(payment.ledger().count(Denomination.GP)).isEqualTo(5);
    assertThat(payment.ledger().count(Denomination.SP)).isZero();
  }

  @Test
  void aSmallPriceFromOneBigCoinReturnsChangeInManyCoins() {
    var payment = CurrencyLedger.of(Map.of(Denomination.PP, 1)).pay(1, false);
    assertThat(payment.change())
        .isEqualTo(Map.of(Denomination.GP, 9, Denomination.SP, 9, Denomination.CP, 9));
    assertThat(payment.ledger().totalCopper()).isEqualTo(999);
  }

  @Test
  void electrumIsSkippedUnlessTheCharacterChoosesIt() {
    var ledger = CurrencyLedger.of(Map.of(Denomination.EP, 2, Denomination.GP, 1));
    var skipped = ledger.pay(100, false);
    assertThat(skipped.ledger().count(Denomination.EP)).isEqualTo(2);
    var chosen = ledger.pay(50, true);
    assertThat(chosen.ledger().count(Denomination.EP)).isEqualTo(1);
    assertThat(chosen.ledger().count(Denomination.GP)).isEqualTo(1);
  }

  @Test
  void anElectrumOnlyPurseCannotPayUnlessChosenAndNeverGivesElectrumChange() {
    var ledger = CurrencyLedger.of(Map.of(Denomination.EP, 2));
    refused(() -> ledger.pay(30, false));
    var payment = ledger.pay(30, true);
    assertThat(payment.change()).isEqualTo(Map.of(Denomination.SP, 2));
    assertThat(payment.ledger().count(Denomination.EP)).isEqualTo(1);
  }

  @Test
  void T_58_aPaymentWithTooLittleMoneyIsRefusedAndNothingChanges() {
    var ledger = CurrencyLedger.of(Map.of(Denomination.CP, 5));
    refused(() -> ledger.pay(10, false));
    assertThat(ledger.count(Denomination.CP)).isEqualTo(5);
    refused(() -> CurrencyLedger.EMPTY.pay(1, false));
  }

  @Test
  void aFreeItemCostsNothing() {
    var ledger = CurrencyLedger.of(Map.of(Denomination.GP, 1));
    assertThat(ledger.pay(0, false).ledger()).isEqualTo(ledger);
  }

  @Test
  void receivingAddsCoinsWithoutConvertingThem() {
    var ledger =
        CurrencyLedger.EMPTY
            .receive(Map.of(Denomination.CP, 120))
            .receive(Map.of(Denomination.PP, 1));
    assertThat(ledger.count(Denomination.CP)).isEqualTo(120);
    assertThat(ledger.count(Denomination.PP)).isEqualTo(1);
    assertThat(ledger.totalCoins()).isEqualTo(121);
  }
}
