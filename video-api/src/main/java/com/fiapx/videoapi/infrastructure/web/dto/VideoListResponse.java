package com.fiapx.videoapi.infrastructure.web.dto;

import com.fiapx.videoapi.domain.model.PageResult;
import com.fiapx.videoapi.domain.model.Video;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Página de resultados da listagem de vídeos do usuário autenticado")
public record VideoListResponse(
    @Schema(description = "Vídeos da página atual") List<VideoStatusResponse> items,
    @Schema(description = "Página atual (0-based)", example = "0") int page,
    @Schema(description = "Tamanho de página solicitado", example = "20") int size,
    @Schema(description = "Total de vídeos do usuário, somando todas as páginas") long totalElements
) {

  public static VideoListResponse from(PageResult<Video> result) {
    List<VideoStatusResponse> items = result.items().stream().map(VideoStatusResponse::from).toList();
    return new VideoListResponse(items, result.page(), result.size(), result.totalElements());
  }
}
