package com.fiapx.videoapi.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.fiapx.videoapi.domain.model.Video;
import com.fiapx.videoapi.domain.port.VideoRepository;
import com.fiapx.videoapi.infrastructure.persistence.entity.VideoEntity;
import com.fiapx.videoapi.infrastructure.persistence.mapper.VideoMapper;
import com.fiapx.videoapi.infrastructure.persistence.repository.SpringDataVideoRepository;

@Component
public class VideoRepositoryAdapter implements VideoRepository {

	private final SpringDataVideoRepository springDataVideoRepository;

	public VideoRepositoryAdapter(SpringDataVideoRepository springDataVideoRepository) {
		this.springDataVideoRepository = springDataVideoRepository;
	}

	@Override
	public Video save(Video video) {
		VideoEntity entity = VideoMapper.toEntity(video);
		VideoEntity saved = springDataVideoRepository.save(entity);
		return VideoMapper.toDomain(saved);
	}

	@Override
	public Optional<Video> findById(UUID id) {
		return springDataVideoRepository.findById(id).map(VideoMapper::toDomain);
	}
}
