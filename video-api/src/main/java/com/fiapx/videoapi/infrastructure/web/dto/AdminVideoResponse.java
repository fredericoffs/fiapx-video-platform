package com.fiapx.videoapi.infrastructure.web.dto;

import com.fiapx.videoapi.application.dto.VideoWithOwner;
import com.fiapx.videoapi.domain.model.Video;
import com.fiapx.videoapi.domain.model.VideoStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Vídeo com dados do dono — usado só na listagem admin, para não poluir o "
    + "VideoStatusResponse do usuário comum com um campo que só faz sentido pra admin.")
public record AdminVideoResponse(
    @Schema(description = "ID do vídeo") UUID id,
    @Schema(description = "ID do usuário dono do vídeo") UUID userId,
    @Schema(description = "E-mail do usuário dono do vídeo", nullable = true) String ownerEmail,
    @Schema(description = "Nome original do arquivo enviado", example = "ferias-praia.mp4") String originalFilename,
    @Schema(description = "QUEUED → PROCESSING → COMPLETED ou FAILED") VideoStatus status,
    @Schema(description = "Motivo da falha — presente só quando status=FAILED", nullable = true) String errorMessage,
    @Schema(description = "Data/hora do upload") Instant createdAt,
    @Schema(description = "Data/hora da última mudança de status") Instant updatedAt
) {

  public static AdminVideoResponse from(VideoWithOwner videoWithOwner) {
    Video video = videoWithOwner.video();
    return new AdminVideoResponse(
        video.getId(),
        video.getUserId(),
        videoWithOwner.ownerEmail(),
        video.getOriginalFilename(),
        video.getStatus(),
        video.getErrorMessage(),
        video.getCreatedAt(),
        video.getUpdatedAt()
    );
  }
}
