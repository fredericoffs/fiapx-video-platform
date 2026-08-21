package com.fiapx.videoapi.infrastructure.persistence;

import com.fiapx.videoapi.domain.model.PageResult;
import com.fiapx.videoapi.domain.model.Video;
import com.fiapx.videoapi.domain.model.VideoStatus;
import com.fiapx.videoapi.domain.port.VideoRepository;
import com.fiapx.videoapi.infrastructure.persistence.entity.VideoEntity;
import com.fiapx.videoapi.infrastructure.persistence.mapper.VideoMapper;
import com.fiapx.videoapi.infrastructure.persistence.repository.SpringDataVideoRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

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

  @Override
  public PageResult<Video> findByUserId(UUID userId, VideoStatus statusFilter, int page, int size) {
    PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    Page<VideoEntity> result = statusFilter == null
        ? springDataVideoRepository.findByUserId(userId, pageRequest)
        : springDataVideoRepository.findByUserIdAndStatus(userId, statusFilter, pageRequest);

    List<Video> items = result.getContent().stream().map(VideoMapper::toDomain).toList();
    return new PageResult<>(items, page, size, result.getTotalElements());
  }

  @Override
  public PageResult<Video> findAll(VideoStatus statusFilter, int page, int size) {
    PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    Page<VideoEntity> result = statusFilter == null
        ? springDataVideoRepository.findAll(pageRequest)
        : springDataVideoRepository.findByStatus(statusFilter, pageRequest);

    List<Video> items = result.getContent().stream().map(VideoMapper::toDomain).toList();
    return new PageResult<>(items, page, size, result.getTotalElements());
  }

  @Override
  public void deleteById(UUID id) {
    springDataVideoRepository.deleteById(id);
  }
}
