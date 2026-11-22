package com.github.sbadolankaur.rpg.campaign.application;

import com.github.sbadolankaur.rpg.campaign.persistence.CampaignStore;
import com.github.sbadolankaur.rpg.platform.ServerIds;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Campaigns {
  private final CampaignStore store;

  public Campaigns(CampaignStore store) {
    this.store = store;
  }

  @Transactional
  public CampaignView create(UUID creator, String name) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    UUID id = ServerIds.at(now.toInstant());
    store.create(id, name, creator, now);
    return new CampaignView(id, name, CampaignView.Role.DM, CampaignView.SessionState.NONE);
  }

  @Transactional(readOnly = true)
  public List<CampaignView> listFor(UUID actor) {
    return store.listFor(actor);
  }

  @Transactional(readOnly = true)
  public CampaignView requireMember(UUID campaign, UUID actor) {
    return store
        .findFor(campaign, actor)
        .orElseThrow(() -> new CampaignException(CampaignException.Code.NOT_FOUND));
  }

  @Transactional(readOnly = true)
  public boolean isMember(UUID campaign, UUID actor) {
    return store.findFor(campaign, actor).isPresent();
  }

  @Transactional(readOnly = true)
  public CampaignView requireDm(UUID campaign, UUID actor) {
    CampaignView view = requireMember(campaign, actor);
    if (view.role() != CampaignView.Role.DM) {
      throw new CampaignException(CampaignException.Code.FORBIDDEN);
    }
    return view;
  }
}
