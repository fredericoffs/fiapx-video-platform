package com.fiapx.videoapi.infrastructure.persistence.repository;

import com.fiapx.videoapi.infrastructure.persistence.entity.UserEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataUserRepository extends JpaRepository<UserEntity, UUID> {

  Optional<UserEntity> findByEmail(String email);

  boolean existsByEmail(String email);

  Page<UserEntity> findByEmailContainingIgnoreCase(String email, Pageable pageable);
}
