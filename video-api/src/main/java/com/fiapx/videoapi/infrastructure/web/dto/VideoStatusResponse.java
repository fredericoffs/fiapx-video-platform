package com.fiapx.videoapi.infrastructure.web.dto;

import com.fiapx.videoapi.domain.model.Video;
import com.fiapx.videoapi.domain.model.VideoStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Status atual de um vídeo no pipeline de processamento")
public record VideoStatusResponse(
    @Schema(description = "ID do vídeo") UUID id,
    @Schema(description = "Nome original do arquivo enviado", example = "ferias-praia.mp4") String originalFilename,
    @Schema(description = "QUEUED → PROCESSING → COMPLETED ou FAILED") VideoStatus status,
    @Schema(description = "Motivo da falha — presente só quando status=FAILED", nullable = true) String errorMessage,
    @Schema(description = "Data/hora do upload") Instant createdAt,
    @Schema(description = "Data/hora da última mudança de status") Instant updatedAt
) {

  public static VideoStatusResponse from(Video video) {
    return new VideoStatusResponse(
        video.getId(),
        video.getOriginalFilename(),
        video.getStatus(),
        video.getErrorMessage(),
        video.getCreatedAt(),
        video.getUpdatedAt()
    );
  }
}
