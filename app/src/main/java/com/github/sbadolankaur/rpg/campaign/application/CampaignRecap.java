package com.github.sbadolankaur.rpg.campaign.application;

import java.util.List;
import java.util.UUID;

public interface CampaignRecap {
  View read(UUID campaign, UUID actor, boolean dm);

  record View(String acting, List<Character> party) {}

  record Character(String name, int hp, int maximumHp, List<Resource> resources) {}

  record Resource(String name, int remaining, int maximum) {}
}
