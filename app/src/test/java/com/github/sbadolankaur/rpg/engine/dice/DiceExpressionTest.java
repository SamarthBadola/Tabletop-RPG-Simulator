package com.github.sbadolankaur.rpg.engine.dice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class DiceExpressionTest {
  private record Vector(String input, String canonical, DiceErrorCode error) {}

  static Stream<Vector> vectors() throws IOException {
    String json;
    try (var stream = DiceExpressionTest.class.getResourceAsStream("dice-vectors.json")) {
      if (stream == null) throw new IOException("Dice vectors resource is missing");
      json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
    Matcher objects = Pattern.compile("\\{([^}]*)}").matcher(json);
    List<Vector> vectors = new ArrayList<>();
    while (objects.find()) {
      String object = objects.group(1);
      String input = value(object, "input").replace("\\n", "\n").replace("\\t", "\t");
      String canonical = nullableValue(object, "canonical");
      String error = nullableValue(object, "error");
      vectors.add(
          new Vector(input, canonical, error == null ? null : DiceErrorCode.valueOf(error)));
    }
    return vectors.stream();
  }

  private static String value(String object, String key) {
    String value = nullableValue(object, key);
    if (value == null) throw new IllegalArgumentException("Missing vector field: " + key);
    return value;
  }

  private static String nullableValue(String object, String key) {
    Matcher matcher =
        Pattern.compile("\\\"" + key + "\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"").matcher(object);
    return matcher.find() ? matcher.group(1) : null;
  }

  @ParameterizedTest
  @MethodSource("vectors")
  void followsTheSpecificationVectors(Vector vector) {
    if (vector.error() == null) {
      DiceExpression parsed = DiceExpression.parse(vector.input());
      assertThat(parsed.toString()).isEqualTo(vector.canonical());
      assertThat(DiceExpression.parse(parsed.toString())).isEqualTo(parsed);
    } else {
      assertThatThrownBy(() -> DiceExpression.parse(vector.input()))
          .isInstanceOf(DiceParseException.class)
          .satisfies(
              error -> assertThat(((DiceParseException) error).code()).isEqualTo(vector.error()));
    }
  }

  @Test
  void everyStableErrorHasADataDrivenExample() throws IOException {
    assertThat(vectors().map(Vector::error).filter(java.util.Objects::nonNull).distinct().toList())
        .containsExactlyInAnyOrder(DiceErrorCode.values());
  }

  @Test
  void evaluatesExamplesInRequiredDrawOrderAndKeepsHistory() {
    ForcedDice dice = new ForcedDice(1, 5, 6);
    RollResult result = DiceExpression.parse("2d6r<=2").roll(dice);
    dice.assertExhausted();
    assertThat(result.terms().getFirst().dice()).extracting(DieRoll::rolled).containsExactly(1, 5);
    assertThat(result.terms().getFirst().dice())
        .extracting(DieRoll::rerollFace)
        .containsExactly(6, null);
    assertThat(result.total()).isEqualTo(11);
  }

  @Test
  void evaluatesSignedDamageAndCriticalFloorExamples() {
    ForcedDice damageDice = new ForcedDice(5, 2, 4, 6);
    RollResult damage = DiceExpression.parse("1d8+3d6+3").roll(damageDice);
    damageDice.assertExhausted();
    assertThat(damage.total()).isEqualTo(20);
    assertThat(damage.terms()).extracting(RollTerm::subtotal).containsExactly(5, 12, 3);

    ForcedDice d20Dice = new ForcedDice(7);
    RollResult reliableTalent = RollPlan.builder().d20(RollMode.NORMAL, 10).build().roll(d20Dice);
    d20Dice.assertExhausted();
    assertThat(reliableTalent.naturalD20()).isEqualTo(10);
  }

  @Test
  void appliesFloorBeforeKeepAndUsesEarlierDieForTies() {
    ForcedDice dice = new ForcedDice(7, 4);
    RollResult result = DiceExpression.parse("2d20kh1min10").roll(dice);
    dice.assertExhausted();
    assertThat(result.total()).isEqualTo(10);
    assertThat(result.terms().getFirst().dice())
        .extracting(DieRoll::kept)
        .containsExactly(true, false);
    assertThat(result.terms().getFirst().dice()).allMatch(DieRoll::floored);
  }

  @Test
  void parserRoundTripsGeneratedValidExpressionsAndRejectsMalformedInputsOnlyAsDiceErrors() {
    SeededRandom random = new SeededRandom(0x51A7E);
    long start = System.nanoTime();
    int iterations = Boolean.getBoolean("dice.fuzz.long") ? 250_000 : 12_000;
    for (int i = 0; i < iterations; i++) {
      if (i % 2 == 0) {
        String expression =
            random.nextInt(2) == 0
                ? (random.nextInt(100) + 1) + "d" + (random.nextInt(999) + 2)
                : "d" + (random.nextInt(999) + 2) + (random.nextInt(2) == 0 ? "kh1" : "r<=1");
        DiceExpression parsed = DiceExpression.parse(expression);
        assertThat(DiceExpression.parse(parsed.toString())).isEqualTo(parsed);
      } else {
        int length = random.nextInt(220);
        StringBuilder malformed = new StringBuilder(length);
        for (int c = 0; c < length; c++) malformed.append((char) random.nextInt(0x100));
        try {
          DiceExpression.parse(malformed.toString());
        } catch (DiceParseException expected) {
          assertThat(expected.code()).isNotNull();
        }
      }
    }
    long limit = Boolean.getBoolean("dice.fuzz.long") ? 30_000_000_000L : 5_000_000_000L;
    assertThat(System.nanoTime() - start).isLessThan(limit);
  }

  @Test
  void parsingIsIndependentOfDefaultLocale() {
    Locale prior = Locale.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));
      assertThat(DiceExpression.parse("2D20KH1").toString()).isEqualTo("2d20kh1");
    } finally {
      Locale.setDefault(prior);
    }
  }

  @Test
  void localeCiJobReallyRunsUnderItsRequestedPlatformSettings() {
    if (!Boolean.getBoolean("dice.localeJob")) return;
    assertThat(Locale.getDefault().getLanguage()).isEqualTo("tr");
    assertThat(TimeZone.getDefault().getID()).isEqualTo("Pacific/Kiritimati");
  }

  @Test
  void parseErrorsExposeTheExactTokenAndLimitDetail() {
    try {
      DiceExpression.parse("0d6");
      throw new AssertionError("Expected out-of-range count");
    } catch (DiceParseException error) {
      assertThat(error.code()).isEqualTo(DiceErrorCode.DICE_VALUE_OUT_OF_RANGE);
      assertThat(error.position()).isZero();
      assertThat(error.token()).isEqualTo("0");
      assertThat(error.field()).isEqualTo("count");
    }
    try {
      DiceExpression.parse("100d6+100d6+1d6");
      throw new AssertionError("Expected total dice limit");
    } catch (DiceParseException error) {
      assertThat(error.code()).isEqualTo(DiceErrorCode.DICE_LIMIT_EXCEEDED);
      assertThat(error.limitName()).isEqualTo("total dice");
      assertThat(error.limitValue()).isEqualTo(200);
    }
  }

  @Test
  void expressionAndResultCollectionsAreImmutableAndPlansAreSafeToShare() throws Exception {
    DiceExpression expression = DiceExpression.parse("2d6kh1");
    assertThatThrownBy(() -> expression.terms().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    RollPlan plan = RollPlan.builder().d20(RollMode.ADVANTAGE).fixed("bonus", null, 3).build();
    ForcedDice immutableSource = new ForcedDice(6, 4);
    RollResult immutableResult = plan.roll(immutableSource);
    immutableSource.assertExhausted();
    assertThatThrownBy(() -> immutableResult.terms().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> immutableResult.terms().getFirst().dice().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    try (var executor = Executors.newFixedThreadPool(4)) {
      var futures =
          java.util.stream.IntStream.range(0, 20)
              .mapToObj(i -> executor.submit(() -> plan.roll(new SeededRandom(i)).total()))
              .toList();
      for (int i = 0; i < futures.size(); i++) {
        assertThat(futures.get(i).get()).isEqualTo(plan.roll(new SeededRandom(i)).total());
      }
    }
  }

  @Test
  void returnsUniformFacesAtSpecifiedSampleSizeAndHandlesAwkwardBounds() {
    SeededRandom source = new SeededRandom(20261122L);
    int[] frequencies = new int[20];
    for (int i = 0; i < 200_000; i++) frequencies[source.nextInt(20)]++;
    java.util.Arrays.stream(frequencies)
        .forEach(count -> assertThat(count).isBetween(9_500, 10_500));
    for (int bound : new int[] {3, 5, 17, 20, 257, 1000, 1001}) {
      for (int i = 0; i < 20_000; i++) assertThat(source.nextInt(bound)).isBetween(0, bound - 1);
    }
    int[] awkward = new int[1001];
    for (int i = 0; i < 200_000; i++) awkward[source.nextInt(1001)]++;
    java.util.Arrays.stream(awkward).forEach(count -> assertThat(count).isBetween(130, 270));

    int[] advantage = new int[20];
    for (int i = 0; i < 200_000; i++) advantage[Math.max(source.nextInt(20), source.nextInt(20))]++;
    for (int face = 1; face <= 20; face++) {
      double expected = 200_000.0 * (2.0 * face - 1) / 400.0;
      double deviation = Math.sqrt(expected * (1.0 - (2.0 * face - 1) / 400.0));
      assertThat(advantage[face - 1])
          .isBetween(
              (int) Math.floor(expected - 6 * deviation),
              (int) Math.ceil(expected + 6 * deviation));
    }
  }

  @Test
  void seededRandomUsesStableSplitMix64GoldenVectors() {
    assertThat(values(new SeededRandom(0))).containsExactly(1, 0, 19, 222, 333);
    assertThat(values(new SeededRandom(1))).containsExactly(0, 3, 15, 117, 366);
    assertThat(values(new SeededRandom(-1))).containsExactly(0, 4, 0, 921, 263);
  }

  @Test
  void rollsAtTheApprovedDiceLimitsWithinTheNightlyTimingBudget() {
    if (!Boolean.getBoolean("dice.timing")) return;
    DiceExpression maximum = DiceExpression.parse("100d1000+100d1000");
    long start = System.nanoTime();
    RollResult result = maximum.roll(new SeededRandom(42));
    assertThat(result.terms()).hasSize(2);
    assertThat(result.terms().stream().mapToInt(term -> term.dice().size()).sum()).isEqualTo(200);
    assertThat(System.nanoTime() - start).isLessThan(2_000_000_000L);
  }

  private static int[] values(RandomSource source) {
    return new int[] {
      source.nextInt(2),
      source.nextInt(6),
      source.nextInt(20),
      source.nextInt(1000),
      source.nextInt(1001)
    };
  }
}
