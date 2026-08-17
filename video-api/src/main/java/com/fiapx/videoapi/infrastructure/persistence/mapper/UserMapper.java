package com.fiapx.videoapi.infrastructure.persistence.mapper;

import com.fiapx.videoapi.domain.model.User;
import com.fiapx.videoapi.infrastructure.persistence.entity.UserEntity;

public final class UserMapper {

  private UserMapper() {
  }

  public static UserEntity toEntity(User user) {
    UserEntity entity = new UserEntity();
    entity.setId(user.getId());
    entity.setEmail(user.getEmail());
    entity.setPasswordHash(user.getPasswordHash());
    entity.setCreatedAt(user.getCreatedAt());
    return entity;
  }

  public static User toDomain(UserEntity entity) {
    return new User(
        entity.getId(),
        entity.getEmail(),
        entity.getPasswordHash(),
        entity.getCreatedAt()
    );
  }
}
