package com.github.sbadolankaur.rpg.campaign.application;

import com.github.sbadolankaur.rpg.campaign.persistence.InviteStore;
import com.github.sbadolankaur.rpg.identity.application.RegistrationService;
import com.github.sbadolankaur.rpg.platform.InstanceMac;
import com.github.sbadolankaur.rpg.platform.ServerIds;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Invites {
  private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
  private final SecureRandom random = new SecureRandom();
  private final InstanceMac mac;
  private final InviteStore store;
  private final Campaigns campaigns;
  private final RegistrationService registration;
  private final CampaignCommands commands;

  public Invites(
      InstanceMac mac,
      InviteStore store,
      Campaigns campaigns,
      RegistrationService registration,
      CampaignCommands commands) {
    this.mac = mac;
    this.store = store;
    this.campaigns = campaigns;
    this.registration = registration;
    this.commands = commands;
  }

  @Transactional
  public String issue(UUID campaign, UUID actor, int uses, int days) {
    campaigns.requireDm(campaign, actor);
    if (uses < 1 || uses > 20 || days < 1 || days > 30) {
      throw new CampaignException(CampaignException.Code.VALIDATION_FAILED);
    }
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    for (int attempt = 0; attempt < 3; attempt++) {
      StringBuilder code = new StringBuilder(10);
      for (int i = 0; i < 10; i++) {
        code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
      }
      String value = code.toString();
      if (store.insert(
          ServerIds.at(now.toInstant()),
          campaign,
          hash(value),
          actor,
          now,
          now.plusDays(days),
          uses)) {
        return value;
      }
    }
    throw new IllegalStateException("Could not allocate an invite");
  }

  @Transactional
  public UUID join(UUID user, String code) {
    var invite = lock(code);
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    checkActive(invite, now);
    // A repeat from a current member uses no additional seat, including a just-consumed code.
    if (campaigns.isMember(invite.campaign(), user)) {
      return invite.campaign();
    }
    checkSeats(invite);
    if (store.addPlayer(invite.campaign(), user, now)) {
      store.consume(invite.id());
    }
    return invite.campaign();
  }

  @Transactional
  public UUID register(String code, String username, String displayName, String password) {
    var invite = lock(code);
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    checkActive(invite, now);
    checkSeats(invite);
    UUID user = registration.registerInvited(username, displayName, password);
    store.addPlayer(invite.campaign(), user, now);
    store.consume(invite.id());
    return invite.campaign();
  }

  @Transactional(readOnly = true)
  public List<InviteView> list(UUID campaign, UUID actor) {
    campaigns.requireDm(campaign, actor);
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    return store.list(campaign).stream()
        .map(
            row ->
                new InviteView(
                    row.id(),
                    row.createdAt(),
                    row.expiresAt(),
                    row.usesLeft(),
                    row.revokedAt() != null
                        ? "revoked"
                        : !row.expiresAt().isAfter(now)
                            ? "expired"
                            : row.usesLeft() == 0 ? "used-up" : "active"))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<InviteStore.MemberRow> members(UUID campaign, UUID actor) {
    campaigns.requireDm(campaign, actor);
    return store.members(campaign);
  }

  public void revoke(UUID campaign, UUID actor, UUID id, CampaignCommands.Envelope envelope) {
    commands.run(
        campaign,
        actor,
        envelope,
        CampaignCommands.Kind.INVITE_REVOKE,
        id,
        clock -> {
          var result = java.util.Map.of("inviteId", id, "state", "revoked");
          if (store.revoke(campaign, id, OffsetDateTime.now(ZoneOffset.UTC)) == 1) {
            return CampaignCommands.Change.at(
                clock, "InviteRevoked", CampaignCommands.Audience.DM, result, result);
          }
          if (store.list(campaign).stream().anyMatch(row -> row.id().equals(id))) {
            return CampaignCommands.Change.unchanged(clock, result);
          }
          throw new CampaignException(CampaignException.Code.NOT_FOUND);
        });
  }

  private InviteStore.LockedInvite lock(String code) {
    if (code == null || code.length() != 10 || !code.matches("[23456789A-HJ-NP-Za-hj-np-z]{10}")) {
      throw invalid();
    }
    byte[] expected = hash(code.toUpperCase(Locale.ROOT));
    var row = store.lock(expected);
    byte[] actual = row.map(InviteStore.LockedInvite::hash).orElseGet(() -> new byte[32]);
    if (!MessageDigest.isEqual(expected, actual) || row.isEmpty()) {
      throw invalid();
    }
    return row.orElseThrow();
  }

  private byte[] hash(String code) {
    return mac.sign("invite-code", code);
  }

  private static void checkActive(InviteStore.LockedInvite invite, OffsetDateTime now) {
    if (invite.revokedAt() != null || !invite.expires().isAfter(now)) {
      throw invalid();
    }
  }

  private static void checkSeats(InviteStore.LockedInvite invite) {
    if (invite.uses() == 0) {
      throw invalid();
    }
  }

  private static CampaignException invalid() {
    return new CampaignException(CampaignException.Code.INVITE_INVALID);
  }

  public record InviteView(
      UUID id, OffsetDateTime createdAt, OffsetDateTime expiresAt, int usesLeft, String state) {}
}
