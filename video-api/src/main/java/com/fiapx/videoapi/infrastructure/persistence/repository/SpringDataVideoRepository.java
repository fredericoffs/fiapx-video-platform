package com.fiapx.videoapi.infrastructure.persistence.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fiapx.videoapi.infrastructure.persistence.entity.VideoEntity;

public interface SpringDataVideoRepository extends JpaRepository<VideoEntity, UUID> {
}
