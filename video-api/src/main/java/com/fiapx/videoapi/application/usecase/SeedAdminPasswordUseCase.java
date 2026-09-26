package com.fiapx.videoapi.application.usecase;

import com.fiapx.videoapi.domain.model.User;
import com.fiapx.videoapi.domain.port.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Roda em todo startup (idempotente): enquanto o admin semeado (V3/V8) continuar com
 * {@code mustChangePassword=true}, aplica a senha vinda de fora (SSM via Terraform, nunca
 * versionada) em vez do placeholder inutilizável da migration. Depois que alguém troca a
 * senha de verdade pelo endpoint (`mustChangePassword` vira false), este seeder para de
 * mexer na conta — nunca sobrescreve uma senha real escolhida pelo usuário.
 */
@Service
public class SeedAdminPasswordUseCase {

  private static final String ADMIN_EMAIL = "admin@fiapx.local";

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  public SeedAdminPasswordUseCase(UserRepository userRepository, PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
  }

  public void handle(String seedPassword) {
    if (seedPassword == null || seedPassword.isBlank()) {
      return;
    }
    userRepository.findByEmail(ADMIN_EMAIL)
        .filter(User::isMustChangePassword)
        .ifPresent(admin -> userRepository.save(admin.withSeededPassword(passwordEncoder.encode(seedPassword))));
  }
}
