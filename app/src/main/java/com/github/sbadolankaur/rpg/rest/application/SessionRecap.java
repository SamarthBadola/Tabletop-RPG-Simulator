package com.github.sbadolankaur.rpg.rest.application;

import com.github.sbadolankaur.rpg.campaign.application.CampaignRecap;
import com.github.sbadolankaur.rpg.encounter.application.Encounters;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
final class SessionRecap implements CampaignRecap {
  private final RestParty party;
  private final Encounters encounters;

  SessionRecap(RestParty party, Encounters encounters) {
    this.party = party;
    this.encounters = encounters;
  }

  @Override
  public View read(UUID campaign, UUID actor, boolean dm) {
    var characters =
        party.sheets(campaign, actor).stream()
            .map(
                sheet ->
                    new Character(
                        sheet.name(),
                        sheet.currentHp(),
                        sheet.maximumHp(),
                        sheet.resources().stream()
                            .map(
                                row ->
                                    new Resource(
                                        row.name(), row.maximum() - row.used(), row.maximum()))
                            .toList()))
            .toList();
    return new View(dm ? encounters.currentTurn(campaign, actor) : null, characters);
  }
}
