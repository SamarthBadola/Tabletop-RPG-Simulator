package com.github.sbadolankaur.rpg.rest.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.sbadolankaur.rpg.campaign.application.CampaignException;
import com.github.sbadolankaur.rpg.engine.rest.RestResolver.Kind;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RestInputTest {
  private static final UUID PARTICIPANT = new UUID(0, 2);
  private static final UUID OTHER = new UUID(0, 1);

  @Test
  void restChoicesAreValidatedAgainstAttemptedKindAndParticipants() {
    var noSlots = Map.<UUID, Map<Integer, Integer>>of();
    var outsider = Map.of(OTHER, Map.of(8, 1));
    invalid(
        input(Kind.SHORT, Map.of(PARTICIPANT, false), noSlots, noSlots, List.of()), "ateAndDrank");
    invalid(input(Kind.LONG, Map.of(OTHER, false), noSlots, noSlots, List.of()), "ateAndDrank");
    invalid(input(Kind.LONG, Map.of(), outsider, noSlots, List.of()), "recoverHitDice");
    invalid(input(Kind.SHORT, Map.of(), noSlots, outsider, List.of()), "arcaneRecovery");
    invalid(input(Kind.SHORT, Map.of(), noSlots, noSlots, List.of(OTHER)), "songOfRestExcludedIds");
    invalid(
        input(Kind.LONG, Map.of(), noSlots, noSlots, List.of(PARTICIPANT)),
        "songOfRestExcludedIds");
  }

  @Test
  void canonicalizesMapKeysWhilePreservingParticipantOrder() {
    var command =
        new RestInput(
            Kind.LONG,
            480,
            List.of(PARTICIPANT, OTHER),
            Map.of(),
            Map.of(),
            false,
            Map.of(PARTICIPANT, false, OTHER, true),
            Map.of(PARTICIPANT, Map.of(10, 1, 8, 0)),
            Map.of(),
            List.of(),
            List.of());
    assertThat(command.ateAndDrank().keySet()).containsExactly(OTHER, PARTICIPANT);
    assertThat(command.recoverHitDice().get(PARTICIPANT).keySet()).containsExactly(8, 10);
    assertThat(command.toRequest(false).plans())
        .extracting(plan -> plan.id())
        .containsExactly(PARTICIPANT, OTHER);
  }

  private static RestInput input(
      Kind kind,
      Map<UUID, Boolean> food,
      Map<UUID, Map<Integer, Integer>> recovery,
      Map<UUID, Map<Integer, Integer>> slots,
      List<UUID> excluded) {
    return new RestInput(
        kind,
        480,
        List.of(PARTICIPANT),
        Map.of(),
        Map.of(),
        false,
        food,
        recovery,
        slots,
        List.of(),
        excluded);
  }

  private static void invalid(RestInput input, String field) {
    assertThatThrownBy(() -> input.toRequest(false))
        .isInstanceOfSatisfying(
            CampaignException.class,
            failure -> {
              assertThat(failure.code()).isEqualTo(CampaignException.Code.VALIDATION_FAILED);
              assertThat(failure.fields())
                  .containsExactly(new CampaignException.FieldError(field, "INCORRECT"));
            });
  }
}
