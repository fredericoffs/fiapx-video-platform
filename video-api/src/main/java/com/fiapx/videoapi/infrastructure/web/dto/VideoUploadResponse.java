package com.fiapx.videoapi.infrastructure.web.dto;

import com.fiapx.videoapi.application.dto.VideoUploadResult;
import com.fiapx.videoapi.domain.model.VideoStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Confirmação de upload aceito para processamento")
public record VideoUploadResponse(
    @Schema(description = "ID gerado do vídeo") UUID id,
    @Schema(description = "Sempre QUEUED nesta resposta — o processamento é assíncrono") VideoStatus status
) {

  public static VideoUploadResponse from(VideoUploadResult result) {
    return new VideoUploadResponse(result.id(), result.status());
  }
}
