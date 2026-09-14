package com.fiapx.videoapi.infrastructure.persistence.entity;

import com.fiapx.videoapi.domain.model.VideoStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "videos")
@Getter
@Setter
@NoArgsConstructor
public class VideoEntity {

  @Id
  private UUID id;

  @Column(name = "user_id")
  private UUID userId;

  @Column(name = "original_filename", nullable = false)
  private String originalFilename;

  @Column(name = "storage_key", nullable = false)
  private String storageKey;

  @Column(name = "file_size_bytes")
  private Long fileSizeBytes;

  @Column(name = "zip_storage_key")
  private String zipStorageKey;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private VideoStatus status;

  @Column(name = "error_message")
  private String errorMessage;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private Long version;
}
