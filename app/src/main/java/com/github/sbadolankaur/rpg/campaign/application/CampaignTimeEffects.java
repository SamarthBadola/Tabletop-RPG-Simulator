package com.github.sbadolankaur.rpg.campaign.application;

import java.util.UUID;

/** Handles campaign-wide effects of a DM clock advance in the command transaction. */
public interface CampaignTimeEffects {
  CampaignCommands.Change advance(UUID campaign, UUID actor, long start, long seconds);
}
