package com.fiapx.videoapi.infrastructure.config;

import com.fiapx.videoapi.application.usecase.SeedAdminPasswordUseCase;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AdminPasswordSeederRunner implements ApplicationRunner {

  private final SeedAdminPasswordUseCase seedAdminPasswordUseCase;
  private final AdminProperties adminProperties;

  public AdminPasswordSeederRunner(SeedAdminPasswordUseCase seedAdminPasswordUseCase, AdminProperties adminProperties) {
    this.seedAdminPasswordUseCase = seedAdminPasswordUseCase;
    this.adminProperties = adminProperties;
  }

  @Override
  public void run(@NonNull ApplicationArguments args) {
    seedAdminPasswordUseCase.handle(adminProperties.seedPassword());
  }
}
