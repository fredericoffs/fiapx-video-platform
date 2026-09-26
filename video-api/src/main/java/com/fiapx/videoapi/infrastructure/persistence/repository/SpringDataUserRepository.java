package com.fiapx.videoapi.infrastructure.persistence.repository;

import com.fiapx.videoapi.infrastructure.persistence.entity.UserEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataUserRepository extends JpaRepository<UserEntity, UUID> {

  Optional<UserEntity> findByEmail(String email);

  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT u FROM UserEntity u WHERE u.id = :id")
  Optional<UserEntity> findLockedById(@Param("id") UUID id);

  boolean existsByEmail(String email);

  Page<UserEntity> findByEmailContainingIgnoreCase(String email, Pageable pageable);
}
