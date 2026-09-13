package com.fiapx.videoapi.infrastructure.persistence.repository;

import com.fiapx.videoapi.domain.model.VideoStatus;
import com.fiapx.videoapi.infrastructure.persistence.entity.VideoEntity;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataVideoRepository extends JpaRepository<VideoEntity, UUID> {

  Page<VideoEntity> findByUserId(UUID userId, Pageable pageable);

  Page<VideoEntity> findByUserIdAndStatus(UUID userId, VideoStatus status, Pageable pageable);

  Page<VideoEntity> findByStatus(VideoStatus status, Pageable pageable);

  Page<VideoEntity> findByOriginalFilenameContainingIgnoreCase(String originalFilename, Pageable pageable);

  Page<VideoEntity> findByStatusAndOriginalFilenameContainingIgnoreCase(
      VideoStatus status, String originalFilename, Pageable pageable
  );
}
