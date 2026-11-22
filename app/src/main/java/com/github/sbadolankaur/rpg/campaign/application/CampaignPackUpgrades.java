package com.github.sbadolankaur.rpg.campaign.application;

import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands.Audience;
import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands.Change;
import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands.Envelope;
import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands.Kind;
import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands.Reply;
import com.github.sbadolankaur.rpg.campaign.application.CampaignException.Code;
import com.github.sbadolankaur.rpg.content.ContentPacks;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampaignPackUpgrades {
  private final Campaigns campaigns;
  private final CampaignCommands commands;
  private final ContentPacks packs;

  CampaignPackUpgrades(Campaigns campaigns, CampaignCommands commands, ContentPacks packs) {
    this.campaigns = campaigns;
    this.commands = commands;
    this.packs = packs;
  }

  public List<ContentPacks.SrdVersion> list(UUID campaign, UUID actor) {
    campaigns.requireDm(campaign, actor);
    return packs.bundledVersions(campaign);
  }

  public String currentVersion() {
    return packs.currentBundledVersion();
  }

  @Transactional(readOnly = true)
  public ContentPacks.UpgradeReview review(
      UUID campaign, UUID actor, UUID pack, String targetVersion) {
    campaigns.requireDm(campaign, actor);
    return packs
        .reviewUpgrade(campaign, pack, targetVersion)
        .orElseThrow(() -> new CampaignException(Code.NOT_FOUND));
  }

  public Reply apply(
      UUID campaign,
      UUID actor,
      UUID pack,
      Envelope envelope,
      String targetVersion,
      String reviewHash,
      Set<String> groups) {
    return commands.run(
        campaign,
        actor,
        envelope,
        Kind.PACK_UPGRADE,
        new Input(pack, targetVersion, reviewHash, groups.stream().sorted().toList()),
        clock -> {
          var review =
              packs
                  .reviewUpgrade(campaign, pack, targetVersion)
                  .orElseThrow(() -> new CampaignException(Code.PACK_REVIEW_STALE));
          if (!review.hash().equals(reviewHash) || !review.groups().keySet().equals(groups)) {
            throw new CampaignException(Code.PACK_REVIEW_STALE);
          }
          if (!review.blockers().isEmpty()) throw new CampaignException(Code.CONTENT_INVALID);
          if (!packs.applyReviewedUpgrade(campaign, actor, review))
            throw new CampaignException(Code.PACK_REVIEW_STALE);
          return Change.at(
              clock,
              "ContentPackUpgraded",
              Audience.DM,
              Map.of(
                  "previousHash",
                  review.previousHash(),
                  "nextHash",
                  review.nextHash(),
                  "version",
                  targetVersion,
                  "groups",
                  review.groups()),
              Map.of("packId", review.target(), "version", targetVersion));
        });
  }

  private record Input(UUID source, String targetVersion, String reviewHash, List<String> groups) {}
}
