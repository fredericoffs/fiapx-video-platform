package com.fiapx.videoapi.domain.model;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

  @Test
  void hasValidTokenWhenIssuedAfterAccountCreationAndNoPasswordChangeHappened() {
    User user = new User(UUID.randomUUID(), "user@example.com", "hash", Role.USER, Instant.now());

    assertThat(user.hasValidToken(Instant.now().plusSeconds(1))).isTrue();
  }

  @Test
  void hasValidTokenWhenRevocationBaselineIsExplicitlyNull() {
    User user = new User(
        UUID.randomUUID(), "user@example.com", "hash", Role.USER, Instant.now(), false, null
    );

    assertThat(user.hasValidToken(Instant.now().minus(1, ChronoUnit.DAYS))).isTrue();
  }

  @Test
  void rejectsATokenIssuedBeforePasswordWasChanged() {
    User user = new User(UUID.randomUUID(), "user@example.com", "hash", Role.USER, Instant.now());
    User afterChange = user.withPasswordChanged("new-hash");

    Instant beforeChange = Instant.now().minusSeconds(60);

    assertThat(afterChange.hasValidToken(beforeChange)).isFalse();
  }

  // Item 14: o claim "iat" do JWT só tem precisão de segundo — um token reemitido na mesma
  // janela em que a senha foi trocada não pode nascer "já revogado" por causa disso.
  @Test
  void acceptsATokenIssuedInTheSameSecondAsThePasswordChange() {
    User user = new User(UUID.randomUUID(), "user@example.com", "hash", Role.USER, Instant.now());
    User afterChange = user.withPasswordChanged("new-hash");

    Instant issuedAtSameSecondTruncated = afterChange.getTokensValidAfter().truncatedTo(ChronoUnit.SECONDS);

    assertThat(afterChange.hasValidToken(issuedAtSameSecondTruncated)).isTrue();
  }

  @Test
  void acceptsATokenIssuedAfterPasswordWasChanged() {
    User user = new User(UUID.randomUUID(), "user@example.com", "hash", Role.USER, Instant.now());
    User afterChange = user.withPasswordChanged("new-hash");

    Instant afterChangeInstant = afterChange.getTokensValidAfter().plusSeconds(60);

    assertThat(afterChange.hasValidToken(afterChangeInstant)).isTrue();
  }

  @Test
  void seedingAPasswordDoesNotMoveTheRevocationBaseline() {
    Instant tokensValidAfter = Instant.now().minusSeconds(3600);
    User user = new User(
        UUID.randomUUID(), "admin@fiapx.local", "placeholder", Role.ADMIN, Instant.now(), false, tokensValidAfter
    );

    User seeded = user.withSeededPassword("real-hash");

    assertThat(seeded.getTokensValidAfter()).isEqualTo(tokensValidAfter);
    assertThat(seeded.isMustChangePassword()).isTrue();
  }
}
