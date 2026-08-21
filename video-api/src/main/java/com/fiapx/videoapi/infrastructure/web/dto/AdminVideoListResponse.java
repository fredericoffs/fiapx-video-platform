package com.fiapx.videoapi.infrastructure.web.dto;

import com.fiapx.videoapi.application.dto.VideoWithOwner;
import com.fiapx.videoapi.domain.model.PageResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Página de resultados da listagem de vídeos (admin)")
public record AdminVideoListResponse(
    @Schema(description = "Vídeos da página atual") List<AdminVideoResponse> items,
    @Schema(description = "Página atual (0-based)", example = "0") int page,
    @Schema(description = "Tamanho de página solicitado", example = "20") int size,
    @Schema(description = "Total de vídeos do sistema, somando todas as páginas") long totalElements
) {

  public static AdminVideoListResponse from(PageResult<VideoWithOwner> result) {
    List<AdminVideoResponse> items = result.items().stream().map(AdminVideoResponse::from).toList();
    return new AdminVideoListResponse(items, result.page(), result.size(), result.totalElements());
  }
}
