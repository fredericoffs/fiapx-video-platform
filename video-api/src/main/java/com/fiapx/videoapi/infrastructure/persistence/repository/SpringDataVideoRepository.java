package com.fiapx.videoapi.infrastructure.persistence.repository;

import com.fiapx.videoapi.infrastructure.persistence.entity.VideoEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SpringDataVideoRepository
    extends JpaRepository<VideoEntity, UUID>, JpaSpecificationExecutor<VideoEntity> {

}
