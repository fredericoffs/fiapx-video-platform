package com.fiapx.videoapi.domain.port;

import java.util.Optional;
import java.util.UUID;

import com.fiapx.videoapi.domain.model.Video;

public interface VideoRepository {

	Video save(Video video);

	Optional<Video> findById(UUID id);
}
