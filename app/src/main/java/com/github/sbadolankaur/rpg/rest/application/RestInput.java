package com.github.sbadolankaur.rpg.rest.application;

import com.github.sbadolankaur.rpg.campaign.application.CampaignException;
import com.github.sbadolankaur.rpg.campaign.application.CampaignException.FieldError;
import com.github.sbadolankaur.rpg.engine.rest.RestResolver.Kind;
import com.github.sbadolankaur.rpg.engine.rest.RestResolver.Plan;
import com.github.sbadolankaur.rpg.engine.rest.RestResolver.Request;
import com.github.sbadolankaur.rpg.engine.rest.RestResolver.Song;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** The ordered participant list and canonical choice maps are signed with the action. */
public record RestInput(
    Kind kind,
    int durationMinutes,
    List<UUID> participantIds,
    Map<UUID, Integer> spendCount,
    Map<UUID, Integer> spendUntil,
    boolean interrupted,
    Map<UUID, Boolean> ateAndDrank,
    Map<UUID, Map<Integer, Integer>> recoverHitDice,
    Map<UUID, Map<Integer, Integer>> arcaneRecovery,
    List<UUID> songOfRestPerformerIds,
    List<UUID> songOfRestExcludedIds) {

  public RestInput {
    participantIds = List.copyOf(participantIds);
    spendCount = sorted(spendCount);
    spendUntil = sorted(spendUntil);
    ateAndDrank = sorted(ateAndDrank);
    recoverHitDice = sortedChoices(recoverHitDice);
    arcaneRecovery = sortedChoices(arcaneRecovery);
    songOfRestPerformerIds = List.copyOf(songOfRestPerformerIds);
    songOfRestExcludedIds = List.copyOf(songOfRestExcludedIds);
  }

  Request toRequest(boolean spendAllowed) {
    var participants = new HashSet<>(participantIds);
    if (participants.isEmpty()
        || participants.size() > 30
        || participants.size() != participantIds.size())
      throw invalid("participantIds", "INCORRECT");
    checkParticipants(participants, spendCount, "spendCount", spendAllowed);
    checkParticipants(participants, spendUntil, "spendUntil", spendAllowed);
    checkParticipants(participants, ateAndDrank, "ateAndDrank", kind == Kind.LONG);
    checkParticipants(participants, recoverHitDice, "recoverHitDice", kind == Kind.LONG);
    checkParticipants(participants, arcaneRecovery, "arcaneRecovery", kind == Kind.SHORT);
    if (!songOfRestExcludedIds.isEmpty()
        && (kind != Kind.SHORT || !participants.containsAll(songOfRestExcludedIds)))
      throw invalid("songOfRestExcludedIds", "INCORRECT");
    var listeners = new HashSet<>(participants);
    listeners.removeAll(songOfRestExcludedIds);
    var plans =
        participantIds.stream()
            .map(
                id ->
                    new Plan(
                        id,
                        spendCount.get(id),
                        spendUntil.get(id),
                        ateAndDrank.getOrDefault(id, true),
                        recoverHitDice.get(id),
                        arcaneRecovery.getOrDefault(id, Map.of())))
            .toList();
    var songs = songOfRestPerformerIds.stream().map(id -> new Song(id, listeners)).toList();
    return new Request(kind, durationMinutes, interrupted, plans, songs);
  }

  private static void checkParticipants(
      HashSet<UUID> participants, Map<UUID, ?> choices, String field, boolean permitted) {
    if (!choices.isEmpty() && (!permitted || !participants.containsAll(choices.keySet())))
      throw invalid(field, "INCORRECT");
  }

  public static CampaignException invalid(String field, String code) {
    return new CampaignException(new FieldError(field, code));
  }

  private static <K extends Comparable<K>, V> Map<K, V> sorted(Map<K, V> input) {
    return Collections.unmodifiableMap(new TreeMap<>(input));
  }

  private static Map<UUID, Map<Integer, Integer>> sortedChoices(
      Map<UUID, Map<Integer, Integer>> input) {
    var sorted = new TreeMap<UUID, Map<Integer, Integer>>();
    input.forEach((id, values) -> sorted.put(id, sorted(values)));
    return Collections.unmodifiableMap(sorted);
  }
}
