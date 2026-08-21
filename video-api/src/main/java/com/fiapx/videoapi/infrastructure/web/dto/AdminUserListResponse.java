package com.fiapx.videoapi.infrastructure.web.dto;

import com.fiapx.videoapi.domain.model.PageResult;
import com.fiapx.videoapi.domain.model.User;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Página de resultados da listagem de usuários (admin)")
public record AdminUserListResponse(
    @Schema(description = "Usuários da página atual") List<AdminUserResponse> items,
    @Schema(description = "Página atual (0-based)", example = "0") int page,
    @Schema(description = "Tamanho de página solicitado", example = "20") int size,
    @Schema(description = "Total de usuários cadastrados, somando todas as páginas") long totalElements
) {

  public static AdminUserListResponse from(PageResult<User> result) {
    List<AdminUserResponse> items = result.items().stream().map(AdminUserResponse::from).toList();
    return new AdminUserListResponse(items, result.page(), result.size(), result.totalElements());
  }
}
