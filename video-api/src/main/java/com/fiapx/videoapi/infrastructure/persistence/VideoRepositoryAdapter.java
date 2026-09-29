package com.fiapx.videoapi.infrastructure.persistence;

import com.fiapx.videoapi.domain.model.PageResult;
import com.fiapx.videoapi.domain.model.Video;
import com.fiapx.videoapi.domain.model.VideoFilter;
import com.fiapx.videoapi.domain.model.VideoSort;
import com.fiapx.videoapi.domain.port.VideoRepository;
import com.fiapx.videoapi.infrastructure.persistence.entity.VideoEntity;
import com.fiapx.videoapi.infrastructure.persistence.mapper.VideoMapper;
import com.fiapx.videoapi.infrastructure.persistence.repository.SpringDataVideoRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class VideoRepositoryAdapter implements VideoRepository {

  private static final char LIKE_ESCAPE = '\\';

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
  public PageResult<Video> findByUserId(UUID userId, VideoFilter filter, VideoSort sort, int page, int size) {
    return search(userId, filter, sort, page, size);
  }

  @Override
  public PageResult<Video> findAll(VideoFilter filter, VideoSort sort, int page, int size) {
    return search(null, filter, sort, page, size);
  }

  private PageResult<Video> search(UUID ownerId, VideoFilter filter, VideoSort sort, int page, int size) {
    PageRequest pageRequest = PageRequest.of(page, size, toSpringSort(sort));
    Page<VideoEntity> result = springDataVideoRepository.findAll(toSpecification(ownerId, filter), pageRequest);
    List<Video> items = result.getContent().stream().map(VideoMapper::toDomain).toList();
    return new PageResult<>(items, page, size, result.getTotalElements());
  }

  private static Specification<VideoEntity> toSpecification(UUID ownerId, VideoFilter filter) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (ownerId != null) {
        predicates.add(cb.equal(root.get("userId"), ownerId));
      }
      if (filter.status() != null) {
        predicates.add(cb.equal(root.get("status"), filter.status()));
      }
      if (filter.filename() != null && !filter.filename().isBlank()) {
        String pattern = "%" + escapeLike(filter.filename().toLowerCase(Locale.ROOT)) + "%";
        predicates.add(cb.like(cb.lower(root.get("originalFilename")), pattern, LIKE_ESCAPE));
      }
      if (filter.createdFrom() != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.createdFrom()));
      }
      if (filter.createdTo() != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), filter.createdTo()));
      }
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }

  // % e _ digitados pelo usuário são literais, não curingas.
  private static String escapeLike(String value) {
    return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  // Desempate por createdAt e id: sem ordem total, a paginação repete/pula itens com a
  // mesma chave de ordenação (vários vídeos com o mesmo nome, por exemplo).
  private static Sort toSpringSort(VideoSort sort) {
    Sort.Direction direction = sort.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC;
    Sort.Order primary = switch (sort.field()) {
      case CREATED_AT -> new Sort.Order(direction, "createdAt");
      case FILENAME -> new Sort.Order(direction, "originalFilename").ignoreCase();
      // Vídeos anteriores à V7 não têm tamanho: ficam no fim nos dois sentidos.
      case FILE_SIZE -> new Sort.Order(direction, "fileSizeBytes").nullsLast();
    };
    return Sort.by(primary, Sort.Order.desc("createdAt"), Sort.Order.asc("id"));
  }

  @Override
  public void deleteById(UUID id) {
    springDataVideoRepository.deleteById(id);
  }
}
